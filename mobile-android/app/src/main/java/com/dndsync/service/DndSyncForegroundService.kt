package com.dndsync.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dndsync.DndSyncApplication
import com.dndsync.R
import com.dndsync.model.DndMode
import com.dndsync.model.NotificationItem
import com.dndsync.network.DiscoveryClient
import com.dndsync.network.DndWebSocketClient
import com.dndsync.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DndSyncForegroundService : Service(), DndWebSocketClient.MessageListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var wsClient: DndWebSocketClient
    private lateinit var discoveryClient: DiscoveryClient

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Searching : ConnectionState()
        data class Connecting(val target: String) : ConnectionState()
        data class Connected(val desktopName: String, val host: String) : ConnectionState()
        data class Paired(val desktopName: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        discoveryClient = DiscoveryClient(this)
        wsClient = DndWebSocketClient(DndSyncApplication.instance, this)

        startForeground(NOTIFICATION_ID, buildForegroundNotification("Waiting for desktop connection..."))

        // Try connecting to last known host if available
        val lastHost = DndSyncApplication.instance.prefs.getString("last_host", null)
        if (lastHost != null) {
            connectToDesktop(lastHost, 47890)
        }

        // Start background discovery and connection loop
        startDiscoveryLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val host = intent?.getStringExtra("host")
        val pin = intent?.getStringExtra("pin")

        if (action == ACTION_CONNECT_MANUAL && host != null) {
            connectToDesktop(host, 47890)
        } else if (action == ACTION_PAIR_PIN && pin != null) {
            pairWithPin(pin)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        wsClient.disconnect()
        serviceScope.cancel()
    }

    private fun startDiscoveryLoop() {
        serviceScope.launch {
            while (isActive) {
                if (!wsClient.isConnected.value) {
                    val currentState = _connectionState.value
                    if (currentState !is ConnectionState.Connecting && currentState !is ConnectionState.Error) {
                        _connectionState.value = ConnectionState.Searching
                        updateNotification("Searching for desktop on Wi-Fi...")
                    }

                    val discovered = discoveryClient.listenForDesktop(timeoutMs = 4000)
                    if (discovered != null) {
                        Log.d(TAG, "Discovered Desktop: ${discovered.deviceName} at ${discovered.ipAddress}:${discovered.wsPort}")
                        connectToDesktop(discovered.ipAddress, discovered.wsPort)
                    }
                }
                delay(3000)
            }
        }
    }

    fun connectToDesktop(host: String, port: Int = 47890) {
        val (cleanHost, cleanPort) = DndWebSocketClient.cleanHostAndPort(host, port)
        if (cleanHost.isBlank()) {
            _connectionState.value = ConnectionState.Error("Please enter a valid IP address")
            return
        }
        DndSyncApplication.instance.prefs.edit().putString("last_host", cleanHost).apply()
        _connectionState.value = ConnectionState.Connecting("$cleanHost:$cleanPort")
        updateNotification("Connecting to $cleanHost:$cleanPort...")
        serviceScope.launch {
            wsClient.connect(cleanHost, cleanPort)
        }
    }

    fun pairWithPin(pin: String) {
        wsClient.pairWithPin(pin)
    }

    fun handleNotificationPosted(item: NotificationItem) {
        wsClient.sendNotificationPosted(item)
    }

    fun handleNotificationRemoved(key: String, packageName: String) {
        wsClient.sendNotificationRemoved(key, packageName)
    }

    fun handleDndChanged(mode: DndMode, isEnabled: Boolean, rawCode: Int, modeName: String? = null) {
        wsClient.sendDndUpdate(mode, isEnabled, rawCode, modeName)
    }

    // WebSocket callbacks
    override fun onConnected() {
        Log.d(TAG, "WebSocket Connected")
        val host = DndSyncApplication.instance.prefs.getString("last_host", "") ?: ""
        _connectionState.value = ConnectionState.Connected("Desktop PC", host)
        updateNotification("Connected to Desktop")
        DndNotificationListenerService.instance?.broadcastCurrentDndStatus()
    }

    override fun onDisconnected() {
        Log.d(TAG, "WebSocket Disconnected")
        if (_connectionState.value !is ConnectionState.Searching && _connectionState.value !is ConnectionState.Connecting) {
            _connectionState.value = ConnectionState.Disconnected
            updateNotification("Disconnected from Desktop")
        }
    }

    override fun onConnectionError(reason: String) {
        Log.e(TAG, "WebSocket Connection Error: $reason")
        _connectionState.value = ConnectionState.Error(reason)
        updateNotification("Connection failed: $reason")
    }

    override fun onPairingSuccess(sessionToken: String) {
        Log.d(TAG, "Pairing successful!")
        _connectionState.value = ConnectionState.Paired("Desktop PC")
        updateNotification("Paired & Connected with Desktop")
    }

    override fun onPairingFailed(error: String) {
        Log.e(TAG, "Pairing failed: $error")
        val host = DndSyncApplication.instance.prefs.getString("last_host", "") ?: ""
        _connectionState.value = ConnectionState.Connected("Desktop PC", host)
        updateNotification("Pairing failed: $error")
    }

    override fun onSetDndRequested(enabled: Boolean, mode: DndMode) {
        Log.d(TAG, "Desktop requested DND: enabled=$enabled, mode=$mode")
        DndNotificationListenerService.instance?.setDnd(enabled, mode)
    }

    override fun onDismissNotificationRequested(notificationId: String, packageName: String) {
        Log.d(TAG, "Desktop requested dismiss: $notificationId")
        DndNotificationListenerService.instance?.dismissNotificationByKey(notificationId)
    }

    override fun onSendReplyRequested(notificationId: String, actionId: String, packageName: String, text: String) {
        Log.d(TAG, "Desktop requested reply for $notificationId: $text")
        DndNotificationListenerService.instance?.sendReply(notificationId, actionId, text)
    }

    override fun onSyncAllRequested() {
        val notifs = DndNotificationListenerService.instance?.getAllActiveNotifications() ?: emptyList()
        val filter = DndNotificationListenerService.instance?.currentInterruptionFilter ?: 1
        val (mode, isEnabled) = when (filter) {
            1 -> Pair(DndMode.OFF, false)
            2 -> Pair(DndMode.PRIORITY_ONLY, true)
            3 -> Pair(DndMode.TOTAL_SILENCE, true)
            4 -> Pair(DndMode.ALARMS_ONLY, true)
            else -> Pair(DndMode.OFF, false)
        }
        val dndPayload = com.dndsync.model.DndStatusPayload(
            mode = mode,
            isEnabled = isEnabled,
            sourceDevice = DndSyncApplication.instance.deviceId,
            rawFilterCode = filter
        )
        wsClient.sendSyncAllResponse(notifs, dndPayload)
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, DndSyncApplication.CHANNEL_ID)
            .setContentTitle("DND Syncer Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(statusText))
    }

    companion object {
        private const val TAG = "DndSyncFgService"
        const val NOTIFICATION_ID = 9482
        const val ACTION_CONNECT_MANUAL = "com.dndsync.CONNECT_MANUAL"
        const val ACTION_PAIR_PIN = "com.dndsync.PAIR_PIN"

        var instance: DndSyncForegroundService? = null
            private set

        fun startService(context: Context) {
            val intent = Intent(context, DndSyncForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

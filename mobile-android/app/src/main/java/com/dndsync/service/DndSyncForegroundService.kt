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
import com.dndsync.model.PairedDesktop
import com.dndsync.network.DiscoveryClient
import com.dndsync.network.DndWebSocketClient
import com.dndsync.ui.MainActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

class DndSyncForegroundService : Service(), DndWebSocketClient.MessageListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private lateinit var discoveryClient: DiscoveryClient

    // Active clients mapped by host (e.g. "192.168.1.50:47890")
    private val activeClients = ConcurrentHashMap<String, DndWebSocketClient>()

    private val _pairedDesktops = MutableStateFlow<List<PairedDesktop>>(emptyList())
    val pairedDesktops: StateFlow<List<PairedDesktop>> = _pairedDesktops

    private val _discoveredDesktops = MutableStateFlow<List<DiscoveryClient.DiscoveredDesktop>>(emptyList())
    val discoveredDesktops: StateFlow<List<DiscoveryClient.DiscoveredDesktop>> = _discoveredDesktops

    override fun onCreate() {
        super.onCreate()
        instance = this
        discoveryClient = DiscoveryClient(this)

        startForeground(NOTIFICATION_ID, buildForegroundNotification("Waiting for desktop connection..."))

        // Load paired desktops from storage and connect to all
        loadPairedDesktops()
        connectToAllPaired()

        // Start background discovery loop
        startDiscoveryLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val host = intent?.getStringExtra("host")
        val pin = intent?.getStringExtra("pin")

        if (action == ACTION_CONNECT_MANUAL && host != null) {
            val (cleanHost, cleanPort) = DndWebSocketClient.cleanHostAndPort(host)
            if (pin != null && pin.isNotBlank()) {
                pairWithDesktop(cleanHost, cleanPort, pin)
            } else {
                connectToHost(cleanHost, cleanPort)
            }
        } else if (action == ACTION_PAIR_PIN && pin != null) {
            val defaultHost = _pairedDesktops.value.firstOrNull()?.host
                ?: DndSyncApplication.instance.prefs.getString("last_host", null)
            if (defaultHost != null) {
                val (cleanHost, cleanPort) = DndWebSocketClient.cleanHostAndPort(defaultHost)
                pairWithDesktop(cleanHost, cleanPort, pin)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        activeClients.values.forEach { it.disconnect() }
        activeClients.clear()
        serviceScope.cancel()
    }

    private fun loadPairedDesktops() {
        val json = DndSyncApplication.instance.prefs.getString(PREF_PAIRED_DESKTOPS, null)
        val list: MutableList<PairedDesktop> = if (json != null) {
            try {
                val type = object : TypeToken<List<PairedDesktop>>() {}.type
                gson.fromJson<List<PairedDesktop>>(json, type).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }

        // Migrate legacy single host preference if not in list
        val legacyHost = DndSyncApplication.instance.prefs.getString("last_host", null)
        val legacyToken = DndSyncApplication.instance.prefs.getString("session_token", null)
        if (legacyHost != null && list.none { it.host == legacyHost }) {
            list.add(
                PairedDesktop(
                    deviceId = legacyHost,
                    deviceName = "Desktop PC",
                    deviceType = "macos",
                    host = legacyHost,
                    port = 47890,
                    sessionToken = legacyToken
                )
            )
        }

        _pairedDesktops.value = list
    }

    private fun savePairedDesktops(list: List<PairedDesktop>) {
        _pairedDesktops.value = list
        val json = gson.toJson(list)
        DndSyncApplication.instance.prefs.edit().putString(PREF_PAIRED_DESKTOPS, json).apply()
    }

    fun connectToAllPaired() {
        _pairedDesktops.value.forEach { desktop ->
            getOrCreateClient(desktop.host, desktop.port, desktop.sessionToken, desktop.deviceName, desktop.deviceId)
        }
    }

    private fun getOrCreateClient(
        host: String,
        port: Int = 47890,
        token: String? = null,
        name: String = "Desktop PC",
        deviceId: String? = null
    ): DndWebSocketClient {
        val key = "$host:$port"
        return activeClients.computeIfAbsent(key) {
            DndWebSocketClient(
                app = DndSyncApplication.instance,
                host = host,
                port = port,
                desktopId = deviceId,
                desktopName = name,
                sessionToken = token,
                listener = this
            ).apply {
                connect()
            }
        }
    }

    fun connectToHost(host: String, port: Int = 47890) {
        val (cleanHost, cleanPort) = DndWebSocketClient.cleanHostAndPort(host, port)
        if (cleanHost.isBlank()) return
        val client = getOrCreateClient(cleanHost, cleanPort)
        client.connect()
    }

    fun pairWithDesktop(host: String, port: Int = 47890, pin: String, deviceName: String = "Desktop PC") {
        val (cleanHost, cleanPort) = DndWebSocketClient.cleanHostAndPort(host, port)
        if (cleanHost.isBlank()) return

        val key = "$cleanHost:$cleanPort"
        val existing = activeClients[key]
        val client = existing ?: DndWebSocketClient(
            app = DndSyncApplication.instance,
            host = cleanHost,
            port = cleanPort,
            desktopName = deviceName,
            listener = this
        ).also { activeClients[key] = it }

        client.pairWithPin(pin)
        client.connect()
    }

    fun unpairDesktop(deviceIdOrHost: String) {
        // Disconnect matching client
        val removedKeys = activeClients.keys.filter { key ->
            val client = activeClients[key]
            key.contains(deviceIdOrHost) || client?.desktopId == deviceIdOrHost || client?.host == deviceIdOrHost
        }
        removedKeys.forEach { key ->
            activeClients.remove(key)?.disconnect()
        }

        // Remove from persistent list
        val updated = _pairedDesktops.value.filterNot { it.deviceId == deviceIdOrHost || it.host == deviceIdOrHost }
        savePairedDesktops(updated)
        updateStatusNotification()
    }

    private fun startDiscoveryLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val discovered = discoveryClient.listenForDesktop(timeoutMs = 3000)
                    if (discovered != null) {
                        Log.d(TAG, "Discovered Desktop: ${discovered.deviceName} (${discovered.ipAddress}:${discovered.wsPort})")

                        // Update discovered desktops list
                        val currentDiscovered = _discoveredDesktops.value.toMutableList()
                        val discIndex = currentDiscovered.indexOfFirst { it.deviceId == discovered.deviceId || it.ipAddress == discovered.ipAddress }
                        if (discIndex >= 0) {
                            currentDiscovered[discIndex] = discovered
                        } else {
                            currentDiscovered.add(discovered)
                        }
                        _discoveredDesktops.value = currentDiscovered

                        // If device was already paired, ensure connection is alive
                        val matching = _pairedDesktops.value.find { it.deviceId == discovered.deviceId || it.host == discovered.ipAddress }
                        if (matching != null) {
                            val client = getOrCreateClient(
                                host = discovered.ipAddress,
                                port = discovered.wsPort,
                                token = matching.sessionToken,
                                name = discovered.deviceName,
                                deviceId = discovered.deviceId
                            )
                            if (!client.isConnected.value) {
                                client.connect()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Discovery error: ${e.message}")
                }
                delay(1000)
            }
        }
    }

    fun handleNotificationPosted(item: NotificationItem) {
        activeClients.values.filter { it.isConnected.value }.forEach {
            it.sendNotificationPosted(item)
        }
    }

    fun handleNotificationRemoved(key: String, packageName: String) {
        activeClients.values.filter { it.isConnected.value }.forEach {
            it.sendNotificationRemoved(key, packageName)
        }
    }

    fun handleDndChanged(mode: DndMode, isEnabled: Boolean, rawCode: Int, modeName: String? = null, skipClient: DndWebSocketClient? = null) {
        activeClients.values.filter { it.isConnected.value && it != skipClient }.forEach {
            it.sendDndUpdate(mode, isEnabled, rawCode, modeName)
        }
    }

    // WebSocket Callbacks
    override fun onConnected(client: DndWebSocketClient) {
        Log.d(TAG, "Connected to ${client.desktopName} (${client.host}:${client.port})")
        updateDesktopOnlineState(client, isOnline = true)
        updateStatusNotification()
        DndNotificationListenerService.instance?.broadcastCurrentDndStatus()
    }

    override fun onDisconnected(client: DndWebSocketClient) {
        Log.d(TAG, "Disconnected from ${client.desktopName} (${client.host}:${client.port})")
        updateDesktopOnlineState(client, isOnline = false)
        updateStatusNotification()
    }

    override fun onConnectionError(client: DndWebSocketClient, reason: String) {
        Log.e(TAG, "Connection error for ${client.host}: $reason")
        updateDesktopOnlineState(client, isOnline = false)
        updateStatusNotification()
    }

    override fun onPairingSuccess(client: DndWebSocketClient, sessionToken: String, desktopDeviceId: String) {
        Log.d(TAG, "Pairing success with ${client.desktopName} ($desktopDeviceId)")
        val currentList = _pairedDesktops.value.toMutableList()
        val index = currentList.indexOfFirst { it.deviceId == desktopDeviceId || it.host == client.host }

        val updatedDesktop = PairedDesktop(
            deviceId = desktopDeviceId,
            deviceName = client.desktopName,
            deviceType = "macos",
            host = client.host,
            port = client.port,
            sessionToken = sessionToken,
            isOnline = true,
            lastSeenAt = System.currentTimeMillis()
        )

        if (index >= 0) {
            currentList[index] = updatedDesktop
        } else {
            currentList.add(updatedDesktop)
        }

        savePairedDesktops(currentList)
        updateStatusNotification()
    }

    override fun onPairingFailed(client: DndWebSocketClient, error: String) {
        Log.e(TAG, "Pairing failed for ${client.host}: $error")
    }

    override fun onSetDndRequested(client: DndWebSocketClient, enabled: Boolean, mode: DndMode) {
        Log.d(TAG, "Desktop ${client.desktopName} requested DND: enabled=$enabled, mode=$mode")
        DndNotificationListenerService.instance?.setDnd(enabled, mode)
        // Relay change to all other connected desktops!
        handleDndChanged(mode, enabled, if (enabled) 2 else 1, null, skipClient = client)
    }

    override fun onDismissNotificationRequested(client: DndWebSocketClient, notificationId: String, packageName: String) {
        Log.d(TAG, "Desktop ${client.desktopName} requested dismiss: $notificationId")
        DndNotificationListenerService.instance?.dismissNotificationByKey(notificationId)
    }

    override fun onSendReplyRequested(client: DndWebSocketClient, notificationId: String, actionId: String, packageName: String, text: String) {
        Log.d(TAG, "Desktop ${client.desktopName} requested reply for $notificationId: $text")
        DndNotificationListenerService.instance?.sendReply(notificationId, actionId, text)
    }

    override fun onSyncAllRequested(client: DndWebSocketClient) {
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
        client.sendSyncAllResponse(notifs, dndPayload)
    }

    private fun updateDesktopOnlineState(client: DndWebSocketClient, isOnline: Boolean) {
        val currentList = _pairedDesktops.value.map { desktop ->
            if (desktop.deviceId == client.desktopId || desktop.host == client.host) {
                desktop.copy(isOnline = isOnline, lastSeenAt = if (isOnline) System.currentTimeMillis() else desktop.lastSeenAt)
            } else {
                desktop
            }
        }
        _pairedDesktops.value = currentList
    }

    private fun updateStatusNotification() {
        val onlineCount = _pairedDesktops.value.count { it.isOnline }
        val totalCount = _pairedDesktops.value.size
        val text = if (totalCount == 0) {
            "Waiting for desktop connection..."
        } else {
            "$onlineCount of $totalCount computers synced"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(text))
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

    companion object {
        private const val TAG = "DndSyncFgService"
        const val NOTIFICATION_ID = 9482
        const val PREF_PAIRED_DESKTOPS = "paired_desktops_list"
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

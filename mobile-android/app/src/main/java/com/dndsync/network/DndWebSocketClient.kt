package com.dndsync.network

import android.util.Log
import com.dndsync.DndSyncApplication
import com.dndsync.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.UUID
import java.util.concurrent.TimeUnit

class DndWebSocketClient(
    private val app: DndSyncApplication,
    private val listener: MessageListener
) {
    interface MessageListener {
        fun onConnected()
        fun onDisconnected()
        fun onPairingSuccess(sessionToken: String)
        fun onPairingFailed(error: String)
        fun onSetDndRequested(enabled: Boolean, mode: DndMode)
        fun onDismissNotificationRequested(notificationId: String, packageName: String)
        fun onSendReplyRequested(notificationId: String, actionId: String, packageName: String, text: String)
        fun onSyncAllRequested()
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private var currentHost: String? = null
    private var currentPort: Int = 47890

    fun connect(host: String, port: Int = 47890) {
        currentHost = host
        currentPort = port
        disconnect()

        val request = Request.Builder()
            .url("ws://$host:$port")
            .build()

        Log.d(TAG, "Connecting to ws://$host:$port")
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Opened to $host:$port")
                _isConnected.value = true
                listener.onConnected()

                // Check if session token exists, authenticate or wait for user pairing
                val sessionToken = app.prefs.getString("session_token", null)
                if (sessionToken != null) {
                    sendAuthRequest(sessionToken)
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closing: $code $reason")
                _isConnected.value = false
                listener.onDisconnected()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${t.message}")
                _isConnected.value = false
                listener.onDisconnected()
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client closed")
        webSocket = null
        _isConnected.value = false
    }

    fun pairWithPin(pin: String) {
        val deviceInfo = DeviceInfo(
            deviceId = app.deviceId,
            deviceName = app.deviceName,
            deviceType = DeviceType.ANDROID,
            appVersion = "1.0.0",
            protocolVersion = "1.0.0"
        )
        val payload = PairingRequestPayload(
            deviceInfo = deviceInfo,
            pin = pin
        )
        val msg = SyncMessage(
            id = UUID.randomUUID().toString(),
            type = "PAIR_REQUEST",
            senderId = app.deviceId,
            timestamp = System.currentTimeMillis(),
            payload = payload
        )
        send(gson.toJson(msg))
    }

    private fun sendAuthRequest(token: String) {
        val msg = SyncMessage(
            id = UUID.randomUUID().toString(),
            type = "AUTH_REQUEST",
            senderId = app.deviceId,
            timestamp = System.currentTimeMillis(),
            payload = mapOf("sessionToken" to token)
        )
        send(gson.toJson(msg))
    }

    fun sendDndUpdate(mode: DndMode, isEnabled: Boolean, rawFilterCode: Int, modeName: String? = null) {
        val payload = DndStatusPayload(
            mode = mode,
            modeName = modeName,
            isEnabled = isEnabled,
            sourceDevice = app.deviceId,
            rawFilterCode = rawFilterCode
        )
        val msg = SyncMessage(
            id = UUID.randomUUID().toString(),
            type = "DND_STATUS_UPDATE",
            senderId = app.deviceId,
            timestamp = System.currentTimeMillis(),
            payload = payload
        )
        send(gson.toJson(msg))
    }

    fun sendNotificationPosted(item: NotificationItem) {
        val payload = NotificationPostedPayload(item)
        val msg = SyncMessage(
            id = UUID.randomUUID().toString(),
            type = "NOTIFICATION_POSTED",
            senderId = app.deviceId,
            timestamp = System.currentTimeMillis(),
            payload = payload
        )
        send(gson.toJson(msg))
    }

    fun sendNotificationRemoved(id: String, packageName: String, reason: String? = null) {
        val payload = NotificationRemovedPayload(id, packageName, reason)
        val msg = SyncMessage(
            id = UUID.randomUUID().toString(),
            type = "NOTIFICATION_REMOVED",
            senderId = app.deviceId,
            timestamp = System.currentTimeMillis(),
            payload = payload
        )
        send(gson.toJson(msg))
    }

    fun sendSyncAllResponse(notifications: List<NotificationItem>, dndStatus: DndStatusPayload) {
        val payload = mapOf(
            "notifications" to notifications,
            "dndStatus" to dndStatus
        )
        val msg = SyncMessage(
            id = UUID.randomUUID().toString(),
            type = "SYNC_ALL_NOTIFICATIONS_RESPONSE",
            senderId = app.deviceId,
            timestamp = System.currentTimeMillis(),
            payload = payload
        )
        send(gson.toJson(msg))
    }

    private fun send(json: String) {
        if (_isConnected.value) {
            webSocket?.send(json)
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type").asString
            val payloadObj = json.get("payload")

            when (type) {
                "PAIR_RESPONSE" -> {
                    val resp = gson.fromJson(payloadObj, PairingResponsePayload::class.java)
                    if (resp.success && resp.sessionToken != null) {
                        app.prefs.edit().putString("session_token", resp.sessionToken).apply()
                        listener.onPairingSuccess(resp.sessionToken)
                    } else {
                        listener.onPairingFailed(resp.errorMessage ?: "Pairing failed")
                    }
                }
                "SET_DND_REQUEST" -> {
                    val req = gson.fromJson(payloadObj, SetDndPayload::class.java)
                    listener.onSetDndRequested(req.enabled, req.mode)
                }
                "DISMISS_NOTIFICATION" -> {
                    val req = gson.fromJson(payloadObj, DismissNotificationPayload::class.java)
                    listener.onDismissNotificationRequested(req.notificationId, req.packageName)
                }
                "SEND_NOTIFICATION_REPLY" -> {
                    val req = gson.fromJson(payloadObj, SendReplyPayload::class.java)
                    listener.onSendReplyRequested(req.notificationId, req.actionId, req.packageName, req.replyText)
                }
                "SYNC_ALL_NOTIFICATIONS_REQUEST" -> {
                    listener.onSyncAllRequested()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: $text", e)
        }
    }

    companion object {
        private const val TAG = "DndWebSocketClient"
    }
}

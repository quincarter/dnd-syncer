package com.dndsync.model

import com.google.gson.annotations.SerializedName

enum class DeviceType {
    @SerializedName("android") ANDROID,
    @SerializedName("macos") MACOS,
    @SerializedName("windows") WINDOWS,
    @SerializedName("linux") LINUX
}

enum class DndMode {
    @SerializedName("OFF") OFF,
    @SerializedName("PRIORITY_ONLY") PRIORITY_ONLY,
    @SerializedName("TOTAL_SILENCE") TOTAL_SILENCE,
    @SerializedName("ALARMS_ONLY") ALARMS_ONLY
}

data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val deviceType: DeviceType,
    val appVersion: String,
    val protocolVersion: String,
    val ipAddress: String? = null,
    val port: Int? = null
)

data class NotificationActionItem(
    val id: String,
    val title: String,
    val isReply: Boolean = false,
    val replyPlaceholder: String? = null
)

data class NotificationItem(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val subText: String? = null,
    val timestamp: Long,
    val isOngoing: Boolean = false,
    val isClearable: Boolean = true,
    val category: String? = null,
    val appIconBase64: String? = null,
    val actions: List<NotificationActionItem> = emptyList()
)

data class SyncMessage<T>(
    val id: String,
    val type: String,
    val senderId: String,
    val targetId: String? = null,
    val timestamp: Long,
    val payload: T
)

data class PairingRequestPayload(
    val deviceInfo: DeviceInfo,
    val pin: String,
    val publicKey: String? = null
)

data class PairingResponsePayload(
    val success: Boolean,
    val deviceId: String,
    val sessionToken: String? = null,
    val errorMessage: String? = null
)

data class DndStatusPayload(
    val mode: DndMode,
    val modeName: String? = null,
    val isEnabled: Boolean,
    val sourceDevice: String,
    val rawFilterCode: Int? = null
)

data class SetDndPayload(
    val mode: DndMode,
    val modeName: String? = null,
    val enabled: Boolean
)

data class NotificationPostedPayload(
    val notification: NotificationItem
)

data class NotificationRemovedPayload(
    val notificationId: String,
    val packageName: String,
    val reason: String? = null
)

data class DismissNotificationPayload(
    val notificationId: String,
    val packageName: String
)

data class SendReplyPayload(
    val notificationId: String,
    val actionId: String,
    val packageName: String,
    val replyText: String
)

data class TriggerActionPayload(
    val notificationId: String,
    val actionId: String,
    val packageName: String
)

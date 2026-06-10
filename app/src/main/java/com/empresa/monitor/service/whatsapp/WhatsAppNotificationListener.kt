package com.empresa.monitor.service.whatsapp

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Build
import com.empresa.monitor.service.monitoring.WhatsAppMonitor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WhatsAppNotificationListener : NotificationListenerService() {

    @Inject lateinit var whatsappMonitor: WhatsAppMonitor

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName
        val isWhatsApp = packageName.contains("com.whatsapp")
        if (!isWhatsApp) return

        val isBusiness = packageName.contains("com.whatsapp.w4b")

        // Extract notification text
        val extras = sbn.notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()

        val messageText = bigText ?: text ?: return

        // Determine sender (title is usually the contact name)
        whatsappMonitor.onNotificationReceived(title, messageText, isBusiness)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}

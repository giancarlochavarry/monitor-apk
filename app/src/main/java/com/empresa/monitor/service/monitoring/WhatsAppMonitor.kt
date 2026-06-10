package com.empresa.monitor.service.monitoring

import android.content.Context
import com.empresa.monitor.data.model.*
import com.empresa.monitor.data.repository.MonitorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhatsAppMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MonitorRepository
) {
    private var job: Job? = null
    private val pendingMessages = mutableListOf<WhatsAppMessageRequest>()
    private var currentContactName: String? = null
    private var currentContactNumber: String? = null
    private var isBusiness: Boolean = false

    fun start(ctx: Context) {
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                flushPendingMessages()
                delay(15_000) // flush cada 15s
            }
        }
    }

    /**
     * Called from NotificationListenerService when a WhatsApp notification arrives
     */
    fun onNotificationReceived(sender: String, text: String, isBusiness: Boolean) {
        pendingMessages.add(
            WhatsAppMessageRequest(
                sender = sender,
                messageText = text,
                messageType = "text",
                source = "notification",
                recordedAt = Instant.now().toString()
            )
        )
        this.isBusiness = isBusiness
        if (currentContactNumber == null && sender.contains("@")) {
            // Try to extract number
            currentContactName = sender
        }
    }

    /**
     * Called from OCR after processing a WhatsApp screenshot
     */
    fun onOcrProcessed(contactName: String?, contactNumber: String?, messages: List<WhatsAppMessageRequest>) {
        currentContactName = contactName
        currentContactNumber = contactNumber
        pendingMessages.addAll(messages)
    }

    private suspend fun flushPendingMessages() {
        if (pendingMessages.isEmpty()) return

        val batch = pendingMessages.toList().take(50)
        pendingMessages.removeAll(batch)

        try {
            repository.sendWhatsAppOcr(
                WhatsAppOcrRequest(
                    contactName = currentContactName,
                    contactNumber = currentContactNumber,
                    isBusiness = isBusiness,
                    messages = batch
                )
            )
        } catch (e: Exception) {
            // Si falla, re-agregar al pending
            pendingMessages.addAll(0, batch)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        // flush remaining
        CoroutineScope(Dispatchers.IO).launch {
            flushPendingMessages()
        }
    }
}

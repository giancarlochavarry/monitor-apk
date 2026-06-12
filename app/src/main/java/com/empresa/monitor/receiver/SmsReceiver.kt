package com.empresa.monitor.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import com.empresa.monitor.data.db.MonitorDatabase
import com.empresa.monitor.data.db.entity.SmsEntity
import kotlinx.coroutines.*

class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("Range")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle: Bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return
        val messages = mutableListOf<SmsMessage>()

        for (pdu in pdus) {
            val format = bundle.getString("format", "3gpp")
            val message = SmsMessage.createFromPdu(pdu as ByteArray, format)
            message?.let { messages.add(it) }
        }

        scope.launch {
            val db = MonitorDatabase.getInstance(context)
            val smsDao = db.smsDao()

            for (msg in messages) {
                val address = msg.originatingAddress ?: "unknown"
                val body = msg.messageBody ?: ""
                val date = msg.timestampMillis
                val contactName = getContactName(context, address)

                val sms = SmsEntity(
                    smsId = date, // use timestamp as unique ID
                    address = address,
                    contactName = contactName,
                    body = body,
                    type = 1, // inbox
                    date = date,
                    read = false
                )
                smsDao.insert(sms)
            }
        }
    }

    @SuppressLint("Range")
    private fun getContactName(context: Context, phone: String): String? {
        try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phone)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(0)
                }
            }
        } catch (e: Exception) { /* ignore */ }
        return null
    }
}

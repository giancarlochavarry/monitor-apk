package com.empresa.monitor.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.empresa.monitor.BuildConfig
import com.empresa.monitor.MonitorApp
import com.empresa.monitor.service.MonitorForegroundService
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * MainActivity — mínima, sin login, sin email.
 * Se auto-registra y arranca el monitoreo.
 */
class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("monitor_prefs", MODE_PRIVATE)
        val isConfigured = prefs.getBoolean("configured", false)
        val deviceId = prefs.getString("device_id", null)

        if (isConfigured && deviceId != null) {
            // Ya configurado → iniciar monitoreo directo
            startMonitoring(deviceId)
            finishAffinity()
        } else {
            // Primera vez: auto-registrar e iniciar
            autoRegisterAndStart(prefs)
        }
    }

    private fun autoRegisterAndStart(prefs: android.content.SharedPreferences) {
        scope.launch {
            try {
                val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
                val devId = androidId.take(16)

                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                val json = """{
                    "device_id":"$devId",
                    "device_name":"${Build.MODEL}",
                    "device_model":"${Build.MODEL}",
                    "android_version":"${Build.VERSION.RELEASE}"
                }"""

                val request = Request.Builder()
                    .url("${BuildConfig.API_BASE_URL}devices/register-public")
                    .post(json.toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

                if (response.isSuccessful) {
                    prefs.edit().putBoolean("configured", true).putString("device_id", devId).apply()
                }
                // Iniciar monitoreo incluso si falla el registro (funciona offline)
                withContext(Dispatchers.Main) {
                    startMonitoring(devId)
                    finishAffinity()
                }
            } catch (e: Exception) {
                // Si no hay internet, inicia igual
                val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
                val devId = androidId.take(16)
                prefs.edit().putBoolean("configured", true).putString("device_id", devId).apply()
                startMonitoring(devId)
                finishAffinity()
            }
        }
    }

    private fun startMonitoring(deviceId: String) {
        startForegroundService(Intent(this, MonitorForegroundService::class.java).apply {
            action = MonitorForegroundService.ACTION_START
        })
        (application as MonitorApp).setDeviceIdForServices(deviceId)
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

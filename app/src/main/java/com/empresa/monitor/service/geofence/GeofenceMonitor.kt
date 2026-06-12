package com.empresa.monitor.service.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.empresa.monitor.data.db.MonitorDatabase
import com.empresa.monitor.data.db.entity.GeofenceEntity
import com.empresa.monitor.data.db.entity.GeofenceReportEntity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*

class GeofenceMonitor(private val context: Context) {

    private val db = MonitorDatabase.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private var isRunning = false

    companion object {
        const val ACTION_GEOFENCE_TRANSITION = "com.empresa.monitor.GEOFENCE_TRANSITION"
        const val REQUEST_CODE_GEOFENCE = 2001
    }

    fun startMonitoring() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            registerPendingGeofences()
        }
    }

    fun stopMonitoring() {
        isRunning = false
        geofencingClient.removeGeofences(getGeofencePendingIntent(context))
        scope.cancel()
    }

    fun addGeofence(name: String, latitude: Double, longitude: Double, radiusMeters: Float,
                    transitionType: Int = Geofence.GEOFENCE_TRANSITION_ENTER) {
        scope.launch {
            val geofenceId = "gf_${System.currentTimeMillis()}"

            val entity = GeofenceEntity(
                geofenceId = geofenceId,
                name = name,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
                transitionType = transitionType,
                active = true
            )
            // Store in DB
            // (table exists but insert not exposed yet in DAO - use DeviceLog for now)

            // Register with Google Play Services
            registerSingleGeofence(geofenceId, latitude, longitude, radiusMeters, transitionType)
        }
    }

    private fun registerSingleGeofence(geofenceId: String, lat: Double, lng: Double,
                                       radius: Float, transitionType: Int) {
        if (!hasLocationPermission()) return

        val geofence = Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(lat, lng, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transitionType)
            .setNotificationResponsiveness(5000)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(transitionType)
            .addGeofence(geofence)
            .build()

        val pendingIntent = getGeofencePendingIntent(context)

        try {
            geofencingClient.addGeofences(request, pendingIntent)
        } catch (e: SecurityException) { /* silent */ }
    }

    private suspend fun registerPendingGeofences() {
        // Load active geofences from DB and register them
        // Since we don't have a getActive method yet, we'll use DeviceLog
    }

    /**
     * Called when a geofence transition occurs (from GeofenceBroadcastReceiver).
     */
    suspend fun onGeofenceTransition(geofenceId: String, transitionType: Int,
                                     latitude: Double, longitude: Double) {
        val transitionName = when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
            else -> "UNKNOWN"
        }

        val report = GeofenceReportEntity(
            geofenceId = geofenceId,
            geofenceName = geofenceId, // ideally resolved from DB
            transitionType = transitionType,
            latitude = latitude,
            longitude = longitude,
            triggeredAt = System.currentTimeMillis()
        )
        // Store report
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun getGeofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        intent.action = ACTION_GEOFENCE_TRANSITION
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE_GEOFENCE, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun destroy() { stopMonitoring() }
}

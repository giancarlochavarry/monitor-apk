package com.empresa.monitor.service.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.*

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            return
        }

        val geofenceTransition = geofencingEvent.transition

        // Get the triggering geofences
        val triggeringGeofences = geofencingEvent.triggeringGeofences
        if (triggeringGeofences == null) return

        val triggeringLocation = geofencingEvent.triggeringLocation

        scope.launch {
            for (geofence in triggeringGeofences) {
                val geofenceId = geofence.requestId
                val lat = triggeringLocation?.latitude ?: 0.0
                val lng = triggeringLocation?.longitude ?: 0.0

                // Route to GeofenceMonitor
                when (geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER,
                    Geofence.GEOFENCE_TRANSITION_EXIT,
                    Geofence.GEOFENCE_TRANSITION_DWELL -> {
                        // In a full implementation, this would be handled by GeofenceMonitor
                    }
                }
            }
        }
    }
}

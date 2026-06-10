package com.empresa.monitor.service.monitoring

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.empresa.monitor.data.model.LocationRequest
import com.empresa.monitor.data.repository.MonitorRepository
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MonitorRepository
) {
    private var job: Job? = null
    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun start(ctx: Context) {
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                try {
                    if (ActivityCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        delay(30_000)
                        continue
                    }

                    fusedClient.lastLocation.addOnSuccessListener { loc: Location? ->
                        loc?.let {
                            CoroutineScope(Dispatchers.IO).launch {
                                repository.sendLocation(
                                    LocationRequest(
                                        latitude = it.latitude,
                                        longitude = it.longitude,
                                        accuracy = it.accuracy,
                                        altitude = it.altitude,
                                        speed = it.speed,
                                        provider = it.provider,
                                        recordedAt = Instant.now().toString()
                                    )
                                )
                            }
                        }
                    }
                    delay(60_000) // cada 1 minuto
                } catch (e: Exception) {
                    delay(30_000)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

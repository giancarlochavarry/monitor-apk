package com.empresa.monitor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.empresa.monitor.data.local.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                // Si el monitoreo estaba activo antes del reboot, reactivar
                if (prefs.isMonitoring && prefs.deviceId != null && prefs.authToken != null) {
                    startService(context)
                }
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Si la app se actualizó, asegurar que el servicio siga
                if (prefs.isMonitoring && prefs.deviceId != null) {
                    startService(context)
                }
            }
            Intent.ACTION_USER_PRESENT -> {
                // El usuario desbloqueó el teléfono — verificar servicio
                if (prefs.isMonitoring && prefs.deviceId != null) {
                    // No arrancar de nuevo, solo verificar que esté vivo
                }
            }
        }
    }

    private fun startService(context: Context) {
        val serviceIntent = Intent(context, MonitorForegroundService::class.java).apply {
            action = MonitorForegroundService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}

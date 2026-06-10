package com.empresa.monitor.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.empresa.monitor.data.local.PreferencesManager
import com.empresa.monitor.service.MonitorForegroundService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Actividad oculta: no tiene layout, no aparece en el launcher.
 * Sirve como punto de entrada cuando se toca la notificación.
 * Se cierra inmediatamente después de verificar el estado.
 */
@AndroidEntryPoint
class HiddenActivity : AppCompatActivity() {

    @Inject lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar si el monitoreo está activo, si no, arrancarlo
        if (prefs.isMonitoring && prefs.deviceId != null && prefs.authToken != null) {
            // Ya está andando, solo verificar
        } else if (prefs.deviceId != null && prefs.authToken != null) {
            // Intentar reactivar
            val intent = Intent(this, MonitorForegroundService::class.java)
            intent.action = MonitorForegroundService.ACTION_START
            startForegroundService(intent)
        }

        // Cerrar inmediatamente — el user no ve nada
        finish()
    }
}

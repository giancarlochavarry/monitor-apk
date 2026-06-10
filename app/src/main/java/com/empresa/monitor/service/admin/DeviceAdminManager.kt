package com.empresa.monitor.service.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceAdminManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val componentName = ComponentName(context, AdminReceiver::class.java)

    fun isActiveAdmin(): Boolean {
        return dpm.isAdminActive(componentName)
    }

    fun ensureActiveAdmin(context: Context) {
        if (!isActiveAdmin()) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Necesaria para proteger la configuración corporativa del dispositivo."
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun lockPolicies() {
        if (!isActiveAdmin()) return
        try {
            dpm.setUninstallBlocked(componentName, context.packageName, true)
        } catch (_: Exception) {}
    }

    fun removeAdmin() {
        if (isActiveAdmin()) {
            dpm.removeActiveAdmin(componentName)
        }
    }
}

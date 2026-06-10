package com.empresa.monitor.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("monitor_prefs", Context.MODE_PRIVATE)

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()

    var isMonitoring: Boolean
        get() = prefs.getBoolean(KEY_MONITORING, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING, value).apply()

    var apiUrl: String?
        get() = prefs.getString(KEY_API_URL, null)
        set(value) = prefs.edit().putString(KEY_API_URL, value).apply()

    var employeeName: String?
        get() = prefs.getString(KEY_EMPLOYEE_NAME, null)
        set(value) = prefs.edit().putString(KEY_EMPLOYEE_NAME, value).apply()

    var employeeDepartment: String?
        get() = prefs.getString(KEY_EMPLOYEE_DEPT, null)
        set(value) = prefs.edit().putString(KEY_EMPLOYEE_DEPT, value).apply()

    var lastEmail: String?
        get() = prefs.getString(KEY_LAST_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_LAST_EMAIL, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_MONITORING = "is_monitoring"
        private const val KEY_API_URL = "api_url"
        private const val KEY_EMPLOYEE_NAME = "employee_name"
        private const val KEY_EMPLOYEE_DEPT = "employee_dept"
        private const val KEY_LAST_EMAIL = "last_email"
        private const val KEY_LAST_SYNC = "last_sync"
    }
}

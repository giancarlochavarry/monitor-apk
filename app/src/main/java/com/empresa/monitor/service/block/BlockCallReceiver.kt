package com.empresa.monitor.service.block

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.empresa.monitor.data.db.MonitorDatabase
import kotlinx.coroutines.*

/**
 * Blocks incoming calls from blocked numbers.
 * Registered in manifest to intercept incoming calls.
 */
class BlockCallReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state != TelephonyManager.EXTRA_STATE_RINGING) return

        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            ?: return

        // Check if number is blocked
        scope.launch {
            val db = MonitorDatabase.getInstance(context)
            val blocked = db.blockPhoneDao().isBlocked(incomingNumber)

            if (blocked != null) {
                endCall(context)
            }
        }
    }

    private fun endCall(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Android 9+: use TelecomManager
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecomManager.endCall()
            } else {
                // Older versions: use TelephonyManager via reflection
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val method = tm.javaClass.getMethod("getITelephony")
                method.isAccessible = true
                val iTelephony = method.invoke(tm)
                iTelephony.javaClass.getMethod("endCall").invoke(iTelephony)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

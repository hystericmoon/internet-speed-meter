package com.example.speedmeter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.speedmeter.update.UpdateNotifications

/**
 * Brings the meter back after a reboot or a self-update, if the user had it on.
 * Both broadcasts are exempt from Android's background foreground-service start limits,
 * and specialUse is not among the types Android 15 blocks from BOOT_COMPLETED.
 */
class MeterRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> Unit
            Intent.ACTION_MY_PACKAGE_REPLACED -> UpdateNotifications.cancel(context)
            else -> return
        }
        if (!AppPrefs.meterRunning(context)) return
        DiagnosticsLog.log("restarting meter after ${if (intent.action == Intent.ACTION_BOOT_COMPLETED) "reboot" else "update"}")
        try {
            SpeedMeterService.start(context)
        } catch (e: Exception) {
            Log.w("SpeedMeter", "Couldn't restart meter after ${intent.action}", e)
        }
    }
}

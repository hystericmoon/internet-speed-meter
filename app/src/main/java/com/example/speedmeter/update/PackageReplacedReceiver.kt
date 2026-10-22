package com.example.speedmeter.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.speedmeter.AppPrefs
import com.example.speedmeter.SpeedMeterService

/** After a self-update, restart the meter if it was running when the old version was killed. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        UpdateNotifications.cancel(context)
        if (!AppPrefs.meterRunning(context)) return
        try {
            // MY_PACKAGE_REPLACED is exempt from background foreground-service start limits.
            SpeedMeterService.start(context)
        } catch (e: Exception) {
            Log.w("SpeedMeter", "Couldn't restart meter after update", e)
        }
    }
}

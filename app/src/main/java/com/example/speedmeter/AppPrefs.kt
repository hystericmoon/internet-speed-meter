package com.example.speedmeter

import android.content.Context

/** Small persisted settings and flags. */
object AppPrefs {
    private const val PREFS = "app_prefs"
    private const val KEY_TRANSPARENT_ICON_FALLBACK = "transparent_icon_fallback"
    private const val KEY_AUTO_UPDATE = "auto_update"
    private const val KEY_METER_RUNNING = "meter_running"

    /**
     * Spike flag. When idle, hide by drawing a fully transparent small icon on the visible
     * channel, instead of reposting on the IMPORTANCE_MIN channel. Use this on devices where
     * the MIN channel is ignored or bumped for foreground-service notifications.
     */
    fun transparentIconFallback(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TRANSPARENT_ICON_FALLBACK, false)

    fun setTransparentIconFallback(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TRANSPARENT_ICON_FALLBACK, enabled).apply()
    }

    /** Check for, download and install new builds in the background. */
    fun autoUpdate(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_UPDATE, true)

    fun setAutoUpdate(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }

    /** Whether the meter should be running; used to restart it after a self-update. */
    fun meterRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_METER_RUNNING, false)

    fun setMeterRunning(context: Context, running: Boolean) {
        prefs(context).edit().putBoolean(KEY_METER_RUNNING, running).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

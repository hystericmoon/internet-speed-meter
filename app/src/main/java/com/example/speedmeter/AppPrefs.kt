package com.example.speedmeter

import android.content.Context

/** How quiet the network must be (for 5 s) before the meter hides from the status bar. */
enum class IdleThreshold(val bytesPerSec: Long) {
    NEVER(0),
    // Decimal byte values so they read as round numbers in bits: 8 kbps, 100 kbps, 1 Mbps.
    LOW(1_000),
    MEDIUM(12_500),
    HIGH(125_000),
}

/** Small persisted settings and flags. */
object AppPrefs {
    private const val PREFS = "app_prefs"
    private const val KEY_TRANSPARENT_ICON_FALLBACK = "transparent_icon_fallback"
    private const val KEY_AUTO_UPDATE = "auto_update"
    private const val KEY_METER_RUNNING = "meter_running"
    private const val KEY_SPEED_UNIT = "speed_unit"
    private const val KEY_IDLE_THRESHOLD = "idle_threshold"

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

    /**
     * Whether the user wants the meter on. Set by every start, cleared only by an explicit
     * stop, so after a reboot, a kill or a self-update we know to bring it back.
     */
    fun meterRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_METER_RUNNING, false)

    fun setMeterRunning(context: Context, running: Boolean) {
        prefs(context).edit().putBoolean(KEY_METER_RUNNING, running).apply()
    }

    fun speedUnit(context: Context): SpeedUnit =
        enumOrDefault(prefs(context).getString(KEY_SPEED_UNIT, null), SpeedUnit.BITS)

    fun setSpeedUnit(context: Context, unit: SpeedUnit) {
        prefs(context).edit().putString(KEY_SPEED_UNIT, unit.name).apply()
    }

    fun idleThreshold(context: Context): IdleThreshold =
        enumOrDefault(prefs(context).getString(KEY_IDLE_THRESHOLD, null), IdleThreshold.LOW)

    fun setIdleThreshold(context: Context, threshold: IdleThreshold) {
        prefs(context).edit().putString(KEY_IDLE_THRESHOLD, threshold.name).apply()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

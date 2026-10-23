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
    private const val KEY_AUTO_UPDATE = "auto_update"
    private const val KEY_METER_RUNNING = "meter_running"
    private const val KEY_SPEED_UNIT = "speed_unit"
    private const val KEY_IDLE_THRESHOLD = "idle_threshold"
    const val KEY_OVERLAY = "overlay"
    const val KEY_OVERLAY_POSITION = "overlay_position"
    const val KEY_OVERLAY_VERTICAL = "overlay_vertical"
    const val KEY_OVERLAY_HIDE_LANDSCAPE = "overlay_hide_landscape"

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

    /**
     * User wants the status bar overlay instead of the notification icon. Only takes effect
     * once "Display over other apps" is granted; see [overlayActive].
     */
    fun overlay(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY, false)

    fun setOverlay(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_OVERLAY, enabled).apply()
    }

    fun overlayActive(context: Context): Boolean =
        overlay(context) && android.provider.Settings.canDrawOverlays(context)

    /** Horizontal position, 0 = left edge, 1 = right edge. Default sits left of a centre cutout. */
    fun overlayPosition(context: Context): Float =
        prefs(context).getFloat(KEY_OVERLAY_POSITION, 0.3f)

    fun setOverlayPosition(context: Context, fraction: Float) {
        prefs(context).edit().putFloat(KEY_OVERLAY_POSITION, fraction).apply()
    }

    /** Vertical position, 0 = half a bar up, 0.5 = centred on the status bar, 1 = half a bar down. */
    fun overlayVertical(context: Context): Float =
        prefs(context).getFloat(KEY_OVERLAY_VERTICAL, 0.5f)

    fun setOverlayVertical(context: Context, fraction: Float) {
        prefs(context).edit().putFloat(KEY_OVERLAY_VERTICAL, fraction).apply()
    }

    /**
     * Hide the overlay whenever the phone is in landscape. The dependable full-screen rule:
     * video and games are almost always landscape, and detecting a hidden status bar from
     * another app is unreliable on some OEMs.
     */
    fun overlayHideInLandscape(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY_HIDE_LANDSCAPE, true)

    fun setOverlayHideInLandscape(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_OVERLAY_HIDE_LANDSCAPE, enabled).apply()
    }

    /** For components that react to settings live (the service moves the overlay as you drag). */
    fun prefs(context: Context): android.content.SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}

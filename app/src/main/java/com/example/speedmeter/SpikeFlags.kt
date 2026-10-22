package com.example.speedmeter

import android.content.Context

/** Runtime toggles for the spike, so behaviours can be compared without rebuilding. */
object SpikeFlags {
    private const val PREFS = "spike_flags"
    private const val KEY_TRANSPARENT_ICON_FALLBACK = "transparent_icon_fallback"

    /**
     * When idle, hide by drawing a fully transparent small icon on the visible channel,
     * instead of reposting on the IMPORTANCE_MIN channel. Use this on devices where the
     * MIN channel is ignored or bumped for foreground-service notifications.
     */
    fun transparentIconFallback(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TRANSPARENT_ICON_FALLBACK, false)

    fun setTransparentIconFallback(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TRANSPARENT_ICON_FALLBACK, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

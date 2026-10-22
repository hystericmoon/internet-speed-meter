package com.example.speedmeter

import java.util.Locale

/** Formats byte-per-second rates. Uses binary units (1 K = 1024 B). */
object SpeedFormat {

    /** Status-bar form, at most ~4 chars: "0.0K", "512K", "1.2M", "34M". */
    fun compact(bytesPerSec: Long): String {
        val (value, unit) = scale(bytesPerSec)
        return number(value) + unit
    }

    /** Readable form for the notification shade and the activity: "1.2 MB/s". */
    fun full(bytesPerSec: Long): String {
        val (value, unit) = scale(bytesPerSec)
        return "${number(value)} ${unit}B/s"
    }

    private fun scale(bytesPerSec: Long): Pair<Double, String> {
        var value = bytesPerSec.coerceAtLeast(0) / 1024.0
        val units = arrayOf("K", "M", "G")
        var i = 0
        // Switch unit at 1000 rather than 1024 so the compact form never needs 4 digits.
        while (value >= 999.5 && i < units.lastIndex) {
            value /= 1024.0
            i++
        }
        return value to units[i]
    }

    private fun number(value: Double): String =
        if (value < 9.95) String.format(Locale.US, "%.1f", value)
        else String.format(Locale.US, "%.0f", value)
}

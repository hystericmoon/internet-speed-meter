package com.example.speedmeter

import java.util.Locale

enum class SpeedUnit {
    /** Megabits per second, decimal (1 Mbps = 1,000,000 bit/s), the way ISPs and speed tests quote it. */
    BITS,

    /** Megabytes per second, binary (1 MB/s = 1,048,576 B/s), the way file managers quote it. */
    BYTES,
}

/** Formats rates. Input is always bytes per second; [SpeedUnit] picks how it reads. */
object SpeedFormat {

    private val bitUnits = arrayOf("kbps", "Mbps", "Gbps")
    private val byteUnits = arrayOf("KB/s", "MB/s", "GB/s")
    private val compactUnits = arrayOf("K", "M", "G")

    /** Status-bar form, at most 4 chars: "0.0K", "512K", "1.2M", "34M". */
    fun compact(bytesPerSec: Long, unit: SpeedUnit): String {
        val (value, i) = scale(bytesPerSec, unit)
        return number(value) + compactUnits[i]
    }

    /** Readable form for the notification shade and the app: "9.6 Mbps", "1.2 MB/s". */
    fun full(bytesPerSec: Long, unit: SpeedUnit): String {
        val (value, i) = scale(bytesPerSec, unit)
        val units = if (unit == SpeedUnit.BITS) bitUnits else byteUnits
        return "${number(value)} ${units[i]}"
    }

    private fun scale(bytesPerSec: Long, unit: SpeedUnit): Pair<Double, Int> {
        val bytes = bytesPerSec.coerceAtLeast(0).toDouble()
        val step = if (unit == SpeedUnit.BITS) 1_000.0 else 1_024.0
        var value = if (unit == SpeedUnit.BITS) bytes * 8 / step else bytes / step
        var i = 0
        // Switch unit at 999.5 rather than at the step so the number never needs 4 digits.
        while (value >= 999.5 && i < compactUnits.lastIndex) {
            value /= step
            i++
        }
        return value to i
    }

    private fun number(value: Double): String =
        if (value < 9.95) String.format(Locale.US, "%.1f", value)
        else String.format(Locale.US, "%.0f", value)
}

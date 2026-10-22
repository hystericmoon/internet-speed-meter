package com.example.speedmeter

/**
 * Turns cumulative byte counters into per-second rates and decides when the meter is idle
 * long enough to hide. Pure logic, no Android types, so it is unit-tested directly.
 */
class RateSampler(
    private val idleThresholdBytesPerSec: Long = 1_024,
    private val idleHideDelayMs: Long = 5_000,
) {
    data class Reading(val downBytesPerSec: Long, val upBytesPerSec: Long, val hidden: Boolean)

    private var hasBaseline = false
    private var lastAt = 0L
    private var lastRx = 0L
    private var lastTx = 0L
    /** When traffic first dropped below the threshold; null while active. */
    private var idleSince: Long? = null

    var hidden = false
        private set

    /**
     * Drops the baseline so bytes moved while we weren't sampling (screen off) don't show
     * up as one spike. A hidden meter stays hidden until real traffic appears; a visible
     * one restarts its idle countdown.
     */
    fun reset() {
        hasBaseline = false
        if (!hidden) idleSince = null
    }

    /** Returns null for the first sample after construction or [reset]; it only sets the baseline. */
    fun sample(nowMs: Long, rxBytes: Long, txBytes: Long): Reading? {
        if (!hasBaseline) {
            hasBaseline = true
            lastAt = nowMs
            lastRx = rxBytes
            lastTx = txBytes
            return null
        }

        val elapsed = (nowMs - lastAt).coerceAtLeast(1)
        // Counters can go backwards when an interface resets; treat that as zero.
        val down = (rxBytes - lastRx).coerceAtLeast(0) * 1_000 / elapsed
        val up = (txBytes - lastTx).coerceAtLeast(0) * 1_000 / elapsed
        lastAt = nowMs
        lastRx = rxBytes
        lastTx = txBytes

        if (down + up >= idleThresholdBytesPerSec) {
            idleSince = null
        } else if (idleSince == null) {
            idleSince = nowMs
        }
        hidden = idleSince?.let { nowMs - it >= idleHideDelayMs } ?: false
        return Reading(down, up, hidden)
    }
}

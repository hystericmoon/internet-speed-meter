package com.example.speedmeter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RateSamplerTest {

    private val sampler = RateSampler(idleThresholdBytesPerSec = 1_024, idleHideDelayMs = 5_000)

    /** Feeds one idle sample per second from [fromMs] through [toMs] inclusive; returns the last. */
    private fun idleUntil(fromMs: Long, toMs: Long): RateSampler.Reading {
        var last: RateSampler.Reading? = null
        for (t in fromMs..toMs step 1_000) last = sampler.sample(t, 0, 0)
        return last!!
    }

    @Test
    fun `first sample only sets the baseline`() {
        assertNull(sampler.sample(0, 100, 100))
    }

    @Test
    fun `rate is bytes per second over the real elapsed time`() {
        sampler.sample(0, 0, 0)
        val r = sampler.sample(500, 1_000, 250)!!
        assertEquals(2_000, r.downBytesPerSec)
        assertEquals(500, r.upBytesPerSec)
    }

    @Test
    fun `counter going backwards reads as zero`() {
        sampler.sample(0, 10_000, 10_000)
        val r = sampler.sample(1_000, 5, 5)!!
        assertEquals(0, r.downBytesPerSec)
        assertEquals(0, r.upBytesPerSec)
    }

    @Test
    fun `hides after five seconds below threshold`() {
        sampler.sample(0, 0, 0)
        // Idle countdown starts at the first idle reading (t=1s).
        assertFalse(idleUntil(1_000, 5_000).hidden)
        assertTrue(idleUntil(6_000, 6_000).hidden)
    }

    @Test
    fun `threshold counts download and upload together`() {
        sampler.sample(0, 0, 0)
        var rx = 0L
        var tx = 0L
        for (t in 1_000L..10_000L step 1_000) {
            rx += 512
            tx += 512 // 1 KB/s combined: exactly at threshold, so never idle
            assertFalse(sampler.sample(t, rx, tx)!!.hidden)
        }
    }

    @Test
    fun `threshold zero never hides`() {
        sampler.idleThresholdBytesPerSec = IdleThreshold.NEVER.bytesPerSec
        sampler.sample(0, 0, 0)
        assertFalse(idleUntil(1_000, 60_000).hidden)
    }

    @Test
    fun `raising the threshold at runtime takes effect`() {
        sampler.sample(0, 0, 0)
        var rx = 0L
        // 50 KB/s: busy at the default threshold...
        for (t in 1_000L..10_000L step 1_000) {
            rx += 50_000
            assertFalse(sampler.sample(t, rx, 0)!!.hidden)
        }
        // ...but idle once the user picks "below 1 Mbps" (125,000 B/s).
        sampler.idleThresholdBytesPerSec = IdleThreshold.HIGH.bytesPerSec
        var last: RateSampler.Reading? = null
        for (t in 11_000L..16_000L step 1_000) {
            rx += 50_000
            last = sampler.sample(t, rx, 0)
        }
        assertTrue(last!!.hidden)
    }

    @Test
    fun `traffic unhides immediately`() {
        sampler.sample(0, 0, 0)
        assertTrue(idleUntil(1_000, 6_000).hidden)
        assertFalse(sampler.sample(7_000, 50_000, 0)!!.hidden)
    }

    @Test
    fun `reset keeps a hidden meter hidden`() {
        sampler.sample(0, 0, 0)
        assertTrue(idleUntil(1_000, 6_000).hidden)

        sampler.reset() // screen off, then on
        assertNull(sampler.sample(60_000, 0, 0))
        assertTrue(sampler.sample(61_000, 0, 0)!!.hidden)
    }

    @Test
    fun `reset restarts the countdown for a visible meter`() {
        sampler.sample(0, 0, 0)
        assertFalse(idleUntil(1_000, 4_000).hidden) // 3s into the countdown

        sampler.reset()
        sampler.sample(60_000, 0, 0)
        // Needs another full 5s, not just the remaining 2s.
        assertFalse(idleUntil(61_000, 65_000).hidden)
        assertTrue(idleUntil(66_000, 66_000).hidden)
    }

    @Test
    fun `bytes moved while paused are not counted as a spike`() {
        sampler.sample(0, 0, 0)
        sampler.reset()
        assertNull(sampler.sample(60_000, 500 * 1_024 * 1_024, 0))
        assertEquals(0, sampler.sample(61_000, 500 * 1_024 * 1_024, 0)!!.downBytesPerSec)
    }
}

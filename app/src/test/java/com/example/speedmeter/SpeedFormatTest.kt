package com.example.speedmeter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedFormatTest {

    private val kib = 1_024L
    private val mib = kib * 1_024
    private val gib = mib * 1_024

    @Test
    fun `compact uses one decimal below 10 and none above`() {
        assertEquals("0.0K", SpeedFormat.compact(0))
        assertEquals("0.5K", SpeedFormat.compact(512))
        assertEquals("9.9K", SpeedFormat.compact(10_178)) // 9.94 KiB
        assertEquals("10K", SpeedFormat.compact(10_200)) // 9.96 KiB rounds up, so no decimal
        assertEquals("512K", SpeedFormat.compact(512 * kib))
    }

    @Test
    fun `compact switches unit before needing four digits`() {
        assertEquals("999K", SpeedFormat.compact(999 * kib))
        assertEquals("1.0M", SpeedFormat.compact(1_000 * kib))
        assertEquals("1.2M", SpeedFormat.compact(1_258_291))
        assertEquals("34M", SpeedFormat.compact(34 * mib))
        assertEquals("2.0G", SpeedFormat.compact(2 * gib))
    }

    @Test
    fun `compact never exceeds four characters`() {
        // The status bar icon is only 24dp wide.
        var bytes = 1L
        while (bytes < 100 * gib) {
            val text = SpeedFormat.compact(bytes)
            assertTrue("$bytes -> $text", text.length <= 4)
            bytes = bytes * 21 / 20 + 1
        }
    }

    @Test
    fun `full adds unit suffix`() {
        assertEquals("0.0 KB/s", SpeedFormat.full(0))
        assertEquals("1.2 MB/s", SpeedFormat.full(1_258_291))
        assertEquals("35 KB/s", SpeedFormat.full(35_840))
    }

    @Test
    fun `negative rates are treated as zero`() {
        assertEquals("0.0K", SpeedFormat.compact(-5))
    }
}

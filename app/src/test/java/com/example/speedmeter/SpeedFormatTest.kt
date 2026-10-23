package com.example.speedmeter

import com.example.speedmeter.SpeedUnit.BITS
import com.example.speedmeter.SpeedUnit.BYTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedFormatTest {

    private val kib = 1_024L
    private val mib = kib * 1_024
    private val gib = mib * 1_024

    @Test
    fun `bits are decimal and eight per byte`() {
        assertEquals("0.0 kbps", SpeedFormat.full(0, BITS))
        assertEquals("8.0 kbps", SpeedFormat.full(1_000, BITS))
        assertEquals("100 kbps", SpeedFormat.full(12_500, BITS))
        assertEquals("1.0 Mbps", SpeedFormat.full(125_000, BITS))
        assertEquals("10 Mbps", SpeedFormat.full(1_250_000, BITS)) // a "10 Mbps" plan
        assertEquals("1.0 Gbps", SpeedFormat.full(125_000_000, BITS))
    }

    @Test
    fun `bytes are binary`() {
        assertEquals("0.0 KB/s", SpeedFormat.full(0, BYTES))
        assertEquals("35 KB/s", SpeedFormat.full(35_840, BYTES))
        assertEquals("1.2 MB/s", SpeedFormat.full(1_258_291, BYTES))
        assertEquals("2.0 GB/s", SpeedFormat.full(2 * gib, BYTES))
    }

    @Test
    fun `compact uses one decimal below 10 and none above`() {
        assertEquals("0.0K", SpeedFormat.compact(0, BYTES))
        assertEquals("0.5K", SpeedFormat.compact(512, BYTES))
        assertEquals("9.9K", SpeedFormat.compact(10_178, BYTES)) // 9.94 KiB
        assertEquals("10K", SpeedFormat.compact(10_200, BYTES)) // 9.96 KiB rounds up, so no decimal
        assertEquals("512K", SpeedFormat.compact(512 * kib, BYTES))
        assertEquals("9.6M", SpeedFormat.compact(1_200_000, BITS))
    }

    @Test
    fun `compact switches unit before needing four digits`() {
        assertEquals("999K", SpeedFormat.compact(999 * kib, BYTES))
        assertEquals("1.0M", SpeedFormat.compact(1_000 * kib, BYTES))
        assertEquals("34M", SpeedFormat.compact(34 * mib, BYTES))
        assertEquals("999K", SpeedFormat.compact(124_900, BITS)) // 999.2 kbps
        assertEquals("1.0M", SpeedFormat.compact(124_950, BITS)) // 999.6 kbps
    }

    @Test
    fun `compact never exceeds four characters`() {
        // The status bar icon is only 24dp wide.
        for (unit in SpeedUnit.entries) {
            var bytes = 1L
            while (bytes < 100 * gib) {
                val text = SpeedFormat.compact(bytes, unit)
                assertTrue("$unit $bytes -> $text", text.length <= 4)
                bytes = bytes * 21 / 20 + 1
            }
        }
    }

    @Test
    fun `negative rates are treated as zero`() {
        assertEquals("0.0K", SpeedFormat.compact(-5, BITS))
    }

    @Test
    fun `idle threshold labels read as round numbers in bits`() {
        assertEquals(
            listOf("Never hide", "Below 8.0 kbps", "Below 100 kbps", "Below 1.0 Mbps"),
            IdleThreshold.entries.map { idleLabel(it, BITS) },
        )
    }

    @Test
    fun `overlay mode says dim, not hide`() {
        assertEquals("Never dim", idleLabel(IdleThreshold.NEVER, BITS, dims = true))
        assertEquals("Below 8.0 kbps", idleLabel(IdleThreshold.LOW, BITS, dims = true))
    }
}

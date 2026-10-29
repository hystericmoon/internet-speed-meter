package com.example.speedmeter

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedOverlayTest {

    @Test
    fun `position maps 0 to left edge and 1 to right edge, fully on screen`() {
        assertEquals(0, SpeedOverlay.overlayX(0f, screenWidth = 1080, viewWidth = 200))
        assertEquals(440, SpeedOverlay.overlayX(0.5f, screenWidth = 1080, viewWidth = 200))
        assertEquals(880, SpeedOverlay.overlayX(1f, screenWidth = 1080, viewWidth = 200))
    }

    @Test
    fun `out of range positions and oversized views stay on screen`() {
        assertEquals(0, SpeedOverlay.overlayX(-1f, 1080, 200))
        assertEquals(880, SpeedOverlay.overlayX(2f, 1080, 200))
        assertEquals(0, SpeedOverlay.overlayX(0.5f, 100, 200))
    }

    @Test
    fun `text is one compact line in the chosen unit`() {
        assertEquals("↓9.6M ↑287K", SpeedOverlay.format(1_200_000, 35_840, SpeedUnit.BITS))
        assertEquals("↓1.2M ↑35K", SpeedOverlay.format(1_258_291, 35_840, SpeedUnit.BYTES))
    }

    @Test
    fun `hide reason reports the first matching cause`() {
        assertEquals(null, SpeedOverlay.hideReason(landscape = false, probeHidden = false))
        assertEquals("landscape", SpeedOverlay.hideReason(landscape = true, probeHidden = true))
        assertEquals("full screen (probe)", SpeedOverlay.hideReason(landscape = false, probeHidden = true))
    }

    @Test
    fun `vertical position centres at 0_5 and moves half a bar either way`() {
        assertEquals(-40, SpeedOverlay.overlayY(0f, barHeight = 80))
        assertEquals(0, SpeedOverlay.overlayY(0.5f, barHeight = 80))
        assertEquals(40, SpeedOverlay.overlayY(1f, barHeight = 80))
        assertEquals(40, SpeedOverlay.overlayY(3f, barHeight = 80))
    }

    @Test
    fun `vertical slider reads as up, centred or down`() {
        assertEquals("Centred", verticalLabel(0.5f))
        assertEquals("50% up", verticalLabel(0.25f))
        assertEquals("100% down", verticalLabel(1f))
    }
}

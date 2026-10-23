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
}

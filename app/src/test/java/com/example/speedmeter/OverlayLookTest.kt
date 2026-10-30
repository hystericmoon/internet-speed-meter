package com.example.speedmeter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the overlay draws, at 3× so it can be judged: the status bar icon's two-line image
 * on its dark pill, on a dark and a light bar, active (0.8, the pass-through cap) and dimmed
 * for idle (0.8 × 0.45).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w393dp-h120dp-xxhdpi")
class OverlayLookTest {

    @Test fun overlayLook() = captureRoboImage {
        Row(
            Modifier.background(Color(0xFF71717A)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Bar(Color(0xFF18181B), dimmed = false)
            Bar(Color(0xFF18181B), dimmed = true)
            Bar(Color(0xFFF4F4F5), dimmed = false)
            Bar(Color(0xFFF4F4F5), dimmed = true)
        }
    }

    @Composable
    private fun Bar(color: Color, dimmed: Boolean) {
        val bitmap = SpeedIconRenderer(LocalContext.current, pill = true)
            .render(1_200_000, 35_840, SpeedUnit.BITS)
            .asImageBitmap()
        Box(Modifier.background(color).size(86.dp), contentAlignment = Alignment.Center) {
            Image(
                bitmap,
                contentDescription = null,
                modifier = Modifier.size(72.dp).alpha(if (dimmed) 0.8f * SpeedOverlay.IDLE_ALPHA else 0.8f),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

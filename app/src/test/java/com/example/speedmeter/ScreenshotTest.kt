package com.example.speedmeter

import com.example.speedmeter.ui.ShadTheme
import com.example.speedmeter.update.UpdateInfo
import com.example.speedmeter.update.UpdateState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines for the main screen. `./gradlew verifyRoborazziDebug` compares
 * against src/test/screenshots; `recordRoborazziDebug` (or the "Re-record screenshots"
 * CI run) rewrites them after an intended UI change.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: SpeedMeterApp would start WorkManager, which isn't set up under Robolectric.
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w393dp-h1700dp-xxhdpi")
class ScreenshotTest {

    private val update = UpdateInfo(14, "0.1.14", "a1b2c3d", "", "")
    private val busy = MeterState(downBytesPerSec = 1_258_291, upBytesPerSec = 35_840)
    // Tolerate tiny anti-aliasing differences between machines.
    private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f))

    private val noop = object : MainActions {
        override fun start() {}
        override fun stop() {}
        override fun setUnit(unit: SpeedUnit) {}
        override fun setIdleThreshold(threshold: IdleThreshold) {}
        override fun setTransparentFallback(enabled: Boolean) {}
        override fun setAutoUpdate(enabled: Boolean) {}
        override fun addTile() {}
        override fun checkForUpdate() {}
        override fun installUpdate(state: UpdateState) {}
        override fun openNotificationSettings() {}
        override fun openInstallSettings() {}
    }

    private fun shot(
        dark: Boolean,
        meter: MeterState?,
        update: UpdateState,
        notificationsDenied: Boolean = false,
        canInstall: Boolean = true,
        settings: UiSettings = UiSettings(),
        tileOffer: TileOffer = TileOffer.CanAdd,
    ) = captureRoboImage(roborazziOptions = options) {
        ShadTheme(dark = dark) {
            MainScreen(
                meter = meter,
                update = update,
                settings = settings,
                notificationsDenied = notificationsDenied,
                canInstallUpdates = canInstall,
                tileOffer = tileOffer,
                actions = noop,
                currentVersion = "0.1.10",
            )
        }
    }

    @Test fun runningDownloadingUpdate() = shot(dark = false, busy, UpdateState.Downloading(update, 0.42f))

    @Test fun runningDarkNeedsInstallPermission() =
        shot(dark = true, busy, UpdateState.Available(update), canInstall = false)

    @Test fun stoppedNotificationsDenied() =
        shot(dark = false, meter = null, UpdateState.UpToDate, notificationsDenied = true)

    @Test fun idleDarkUpToDate() = shot(dark = true, MeterState(hidden = true), UpdateState.UpToDate)

    @Test fun updateFailed() =
        shot(dark = false, busy, UpdateState.Failed("Download failed. Check your connection and try again.", update))

    @Test fun checkFailed() =
        shot(dark = false, meter = null, UpdateState.Failed("Couldn't check for updates. Try again later.", null))

    /** MB/s, hiding off (fallback row disappears), tile already added. */
    @Test fun bytesNeverHideTileAdded() = shot(
        dark = false,
        busy,
        UpdateState.UpToDate,
        settings = UiSettings(unit = SpeedUnit.BYTES, idleThreshold = IdleThreshold.NEVER),
        tileOffer = TileOffer.Added,
    )

    /** Android 12 and older: no in-app "Add tile" button. */
    @Test fun manualTileDark() = shot(dark = true, meter = null, UpdateState.UpToDate, tileOffer = TileOffer.Manual)

    /** WCAG 1.4.4: text must survive 200% scaling without clipping or overlap. */
    @Test fun largeFont200() {
        RuntimeEnvironment.setFontScale(2f)
        // Widest realistic readout on both sides.
        shot(dark = false, MeterState(999 * 1_024L, 999 * 1_024L), UpdateState.Available(update))
    }
}

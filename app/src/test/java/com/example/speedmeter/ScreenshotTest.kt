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
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w393dp-h2300dp-xxhdpi")
class ScreenshotTest {

    private val update = UpdateInfo(14, "0.1.14", "a1b2c3d", "", "")
    private val busy = MeterState(downBytesPerSec = 1_258_291, upBytesPerSec = 35_840)
    // Tolerate tiny anti-aliasing differences between machines.
    private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f))

    private val noop = NoopActions

    private fun shot(
        dark: Boolean,
        meter: MeterState?,
        update: UpdateState,
        notificationsDenied: Boolean = false,
        canInstall: Boolean = true,
        settings: UiSettings = UiSettings(),
        tileOffer: TileOffer = TileOffer.CanAdd,
        canDrawOverlays: Boolean = false,
        diagnostics: List<String> = emptyList(),
        backgroundUnrestricted: Boolean = true,
        runningNotificationHidden: Boolean = false,
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
                canDrawOverlays = canDrawOverlays,
                diagnostics = diagnostics,
                backgroundUnrestricted = backgroundUnrestricted,
                runningNotificationHidden = runningNotificationHidden,
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

    /** Overlay picked, "Display over other apps" not granted yet. */
    @Test fun overlayNeedsPermission() =
        shot(dark = false, busy, UpdateState.UpToDate, settings = UiSettings(overlay = true))

    /** Overlay live; notifications are off on purpose, so no "notifications are off" alert. */
    @Test fun overlayActiveDark() = shot(
        dark = true,
        busy,
        UpdateState.UpToDate,
        notificationsDenied = true,
        settings = UiSettings(overlay = true, showDiagnostics = true),
        canDrawOverlays = true,
        diagnostics = listOf(
            "Oct 23 14:02:11  meter started",
            "Oct 23 14:02:11  overlay shown",
            "Oct 23 14:02:12  probe: calibrated (top=96)",
            "Oct 23 14:02:12  overlay visible",
            "Oct 23 14:05:40  orientation: landscape",
            "Oct 23 14:05:40  overlay hidden: landscape",
        ),
        backgroundUnrestricted = false,
    )

    /** Overlay live and idle: badge says Idle, the setting reads "Dim when idle", the log shows the heartbeat.
     *  Notifications on, so it offers "Hide notification". */
    @Test fun overlayIdleDims() = shot(
        dark = false,
        MeterState(downBytesPerSec = 410, upBytesPerSec = 120, hidden = true),
        UpdateState.UpToDate,
        settings = UiSettings(overlay = true, showDiagnostics = true),
        canDrawOverlays = true,
        diagnostics = listOf(
            "Oct 23 14:02:11  meter started",
            "Oct 23 14:02:11  overlay shown",
            "Oct 23 14:02:12  overlay visible",
            "Oct 23 14:02:12  status: overlay visible, ↓1.2M ↑96K",
            "Oct 23 14:09:30  overlay dimmed: idle",
            "Oct 23 14:12:12  status: overlay dimmed: idle, ↓3.3K ↑0.9K",
            "Oct 23 14:12:40  overlay window lost, re-adding",
        ),
    )

    /** Overlay live, its "Overlay running" channel switched off: the alert confirms it's hidden. */
    @Test fun overlayNotificationHidden() = shot(
        dark = false,
        busy,
        UpdateState.UpToDate,
        settings = UiSettings(overlay = true),
        canDrawOverlays = true,
        runningNotificationHidden = true,
    )

    /** WCAG 1.4.4: text must survive 200% scaling without clipping or overlap. */
    @Test fun largeFont200() {
        RuntimeEnvironment.setFontScale(2f)
        // Widest realistic readout on both sides.
        shot(dark = false, MeterState(999 * 1_024L, 999 * 1_024L), UpdateState.Available(update))
    }
}

/** Does nothing; tests override what they need. */
object NoopActions : MainActions {
    override fun start() {}
    override fun stop() {}
    override fun setUnit(unit: SpeedUnit) {}
    override fun setIdleThreshold(threshold: IdleThreshold) {}
    override fun setOverlay(enabled: Boolean) {}
    override fun setOverlayPosition(fraction: Float) {}
    override fun setOverlayVertical(fraction: Float) {}
    override fun setOverlayHideInLandscape(enabled: Boolean) {}
    override fun openOverlaySettings() {}
    override fun setAutoUpdate(enabled: Boolean) {}
    override fun addTile() {}
    override fun checkForUpdate() {}
    override fun installUpdate(state: UpdateState) {}
    override fun openNotificationSettings() {}
    override fun openRunningNotificationSettings() {}
    override fun openInstallSettings() {}
    override fun allowBackgroundRunning() {}
    override fun clearDiagnostics() {}
    override fun setShowDiagnostics(show: Boolean) {}
}

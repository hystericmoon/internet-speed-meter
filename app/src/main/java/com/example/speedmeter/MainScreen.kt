package com.example.speedmeter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speedmeter.ui.BadgeVariant
import com.example.speedmeter.ui.ButtonVariant
import com.example.speedmeter.ui.CardHeader
import com.example.speedmeter.ui.Geist
import com.example.speedmeter.ui.GeistMono
import com.example.speedmeter.ui.ShadRadius
import com.example.speedmeter.ui.Shad
import com.example.speedmeter.ui.ShadAlert
import com.example.speedmeter.ui.ShadBadge
import com.example.speedmeter.ui.ShadButton
import com.example.speedmeter.ui.ShadCard
import com.example.speedmeter.ui.ShadProgress
import com.example.speedmeter.ui.ShadRadioGroup
import com.example.speedmeter.ui.ShadSeparator
import com.example.speedmeter.ui.ShadSlider
import com.example.speedmeter.ui.ShadToggleGroup
import com.example.speedmeter.ui.SettingRow
import com.example.speedmeter.update.UpdateState

/** Persisted settings as the screen sees them. */
data class UiSettings(
    val unit: SpeedUnit = SpeedUnit.BITS,
    val idleThreshold: IdleThreshold = IdleThreshold.LOW,
    val autoUpdate: Boolean = true,
    /** User picked overlay mode; it's live only when [MainScreen]'s canDrawOverlays is true too. */
    val overlay: Boolean = false,
    val overlayPosition: Float = 0.3f,
    val overlayHideInLandscape: Boolean = true,
)

enum class TileOffer {
    /** Android 12 and older: no in-app "add tile" API, so explain how to add it by hand. */
    Manual,
    CanAdd,
    Added,
}

interface MainActions {
    fun start()
    fun stop()
    fun setUnit(unit: SpeedUnit)
    fun setIdleThreshold(threshold: IdleThreshold)
    fun setOverlay(enabled: Boolean)
    fun setOverlayPosition(fraction: Float)
    fun setOverlayHideInLandscape(enabled: Boolean)
    fun openOverlaySettings()
    fun setAutoUpdate(enabled: Boolean)
    fun addTile()
    fun checkForUpdate()
    fun installUpdate(state: UpdateState)
    fun openNotificationSettings()
    fun openInstallSettings()
    fun allowBackgroundRunning()
    fun clearDiagnostics()
}

@Composable
internal fun MainScreen(
    meter: MeterState?,
    update: UpdateState,
    settings: UiSettings,
    notificationsDenied: Boolean,
    canInstallUpdates: Boolean,
    tileOffer: TileOffer,
    actions: MainActions,
    canDrawOverlays: Boolean = false,
    diagnostics: List<String> = emptyList(),
    backgroundUnrestricted: Boolean = true,
    currentVersion: String = BuildConfig.VERSION_NAME,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Shad.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(meter)

        // Icon mode *is* a notification. In overlay mode, notifications off is the point.
        if (notificationsDenied && !(settings.overlay && canDrawOverlays)) {
            ShadAlert(
                title = "Icon mode needs notifications",
                description = "The status bar icon is a notification, so it disappears while Speed Meter's " +
                    "notifications are off. Turn them back on, or use Overlay, which doesn't need them.",
                destructive = true,
            ) {
                ShadButton("Open settings", actions::openNotificationSettings, variant = ButtonVariant.Outline)
                if (!settings.overlay) {
                    ShadButton("Use overlay", { actions.setOverlay(true) }, variant = ButtonVariant.Outline)
                }
            }
        }

        MeterCard(meter, settings.unit, actions)
        UpdateCard(update, canInstallUpdates, currentVersion, actions)
        DisplayCard(settings, canDrawOverlays, actions)
        AppCard(update, settings, tileOffer, backgroundUnrestricted, currentVersion, actions)
        DiagnosticsCard(diagnostics, actions)
    }
}

@Composable
private fun Header(meter: MeterState?) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                "Speed Meter",
                style = Shad.type.h1,
                color = Shad.colors.foreground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Live network speed in your status bar.",
                style = Shad.type.muted,
                color = Shad.colors.mutedForeground,
            )
        }
        // Status is spelled out, never colour-only.
        when {
            meter == null -> ShadBadge("Stopped", BadgeVariant.Outline)
            meter.paused -> ShadBadge("Paused", BadgeVariant.Secondary)
            meter.hidden -> ShadBadge("Idle", BadgeVariant.Secondary, dot = true)
            else -> ShadBadge("Running", BadgeVariant.Default, dot = true)
        }
    }
}

@Composable
private fun MeterCard(meter: MeterState?, unit: SpeedUnit, actions: MainActions) {
    ShadCard {
        // Side by side only fits up to ~150% text; past that, stack so values never collide.
        if (LocalDensity.current.fontScale < 1.5f) {
            Row(Modifier.fillMaxWidth()) {
                Stat("Download", meter?.downBytesPerSec, unit, Modifier.weight(1f))
                Stat("Upload", meter?.upBytesPerSec, unit, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("Download", meter?.downBytesPerSec, unit)
                Stat("Upload", meter?.upBytesPerSec, unit)
            }
        }
        Spacer(Modifier.height(20.dp))
        ShadSeparator()
        Spacer(Modifier.height(20.dp))
        if (meter == null) {
            ShadButton("Start meter", actions::start, Modifier.fillMaxWidth())
        } else {
            ShadButton("Stop meter", actions::stop, Modifier.fillMaxWidth(), variant = ButtonVariant.Outline)
        }
    }
}

@Composable
private fun Stat(label: String, bytesPerSec: Long?, unit: SpeedUnit, modifier: Modifier = Modifier) {
    val c = Shad.colors
    val full = bytesPerSec?.let { SpeedFormat.full(it, unit) }
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$label ${full ?: "not running"}" }) {
        Text(label.uppercase(), style = Shad.type.label, color = c.mutedForeground)
        Spacer(Modifier.height(6.dp))
        if (full == null) {
            Text("—", style = Shad.type.stat, color = c.mutedForeground)
        } else {
            val (number, suffix) = full.split(" ", limit = 2)
            Text(
                buildAnnotatedString {
                    append(number)
                    withStyle(
                        SpanStyle(fontFamily = Geist, fontSize = Shad.type.muted.fontSize, letterSpacing = 0.sp, color = c.mutedForeground),
                    ) {
                        append(" $suffix")
                    }
                },
                style = Shad.type.stat,
                color = c.foreground,
            )
        }
    }
}

@Composable
private fun UpdateCard(update: UpdateState, canInstall: Boolean, currentVersion: String, actions: MainActions) {
    val info = when (update) {
        is UpdateState.Available -> update.info
        is UpdateState.Downloading -> update.info
        is UpdateState.Installing -> update.info
        is UpdateState.Failed -> update.info
        else -> null
    } ?: return

    ShadCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        CardHeader(
            "Update available",
            "Version ${info.versionName} (${info.commit}). You have $currentVersion.",
        )
        Spacer(Modifier.height(16.dp))
        when (update) {
            is UpdateState.Downloading -> {
                ShadProgress(update.progress)
                Spacer(Modifier.height(8.dp))
                Text(
                    update.progress?.let { "Downloading… ${(it * 100).toInt()}%" } ?: "Downloading…",
                    style = Shad.type.muted,
                    color = Shad.colors.mutedForeground,
                )
            }
            is UpdateState.Installing -> {
                ShadProgress(null)
                Spacer(Modifier.height(8.dp))
                Text("Installing… the app will restart.", style = Shad.type.muted, color = Shad.colors.mutedForeground)
            }
            else -> {
                if (update is UpdateState.Failed) {
                    Text(update.message, style = Shad.type.muted, color = Shad.colors.destructive)
                    Spacer(Modifier.height(12.dp))
                }
                if (!canInstall) {
                    // One-time system grant; explain it before sending the user to Settings.
                    Text(
                        "First, allow Speed Meter to install updates. You only need to do this once.",
                        style = Shad.type.muted,
                        color = Shad.colors.mutedForeground,
                    )
                    Spacer(Modifier.height(12.dp))
                    ShadButton("Allow installs", actions::openInstallSettings, Modifier.fillMaxWidth())
                } else {
                    ShadButton(
                        if (update is UpdateState.Failed) "Try again" else "Install update",
                        { actions.installUpdate(update) },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(title: String, description: String) {
    Text(title, style = Shad.type.small, color = Shad.colors.foreground)
    Spacer(Modifier.height(2.dp))
    Text(description, style = Shad.type.muted, color = Shad.colors.mutedForeground)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun DisplayCard(
    settings: UiSettings,
    canDrawOverlays: Boolean,
    actions: MainActions,
) {
    ShadCard {
        CardHeader("Display")
        Spacer(Modifier.height(16.dp))

        FieldLabel(
            "Show speed as",
            if (settings.overlay) {
                "Drawn over the status bar, so it can stay out of the notification panel. Experimental."
            } else {
                "An icon in the status bar. Android also lists it in the notification panel."
            },
        )
        ShadToggleGroup(
            options = listOf(false, true),
            selected = settings.overlay,
            label = { if (it) "Overlay" else "Icon" },
            onSelect = actions::setOverlay,
        )

        if (settings.overlay) {
            Spacer(Modifier.height(16.dp))
            if (!canDrawOverlays) {
                ShadAlert(
                    title = "Permission needed",
                    description = "Allow Speed Meter to display over other apps so it can draw in the status bar. " +
                        "Until then it keeps using the icon.",
                ) {
                    ShadButton("Allow", actions::openOverlaySettings)
                }
            } else {
                FieldLabel("Position", "Drag to clear your clock, icons and camera. The overlay moves as you drag.")
                ShadSlider(
                    value = settings.overlayPosition,
                    onValueChange = actions::setOverlayPosition,
                    stateLabel = "${(settings.overlayPosition * 100).toInt()}% from the left",
                )
                SettingRow(
                    title = "Hide in landscape",
                    description = "Full-screen video and games are almost always landscape. " +
                        "Turn off to keep the overlay in landscape apps that show the status bar.",
                    checked = settings.overlayHideInLandscape,
                    onCheckedChange = actions::setOverlayHideInLandscape,
                )
                Spacer(Modifier.height(8.dp))
                ShadAlert(
                    title = "Remove it from the notification panel",
                    description = "Turn off notifications for Speed Meter. On Android 13 and later the meter " +
                        "then leaves the panel and keeps running. Update prompts go quiet too; " +
                        "automatic updates still install.",
                ) {
                    ShadButton("Notification settings", actions::openNotificationSettings, variant = ButtonVariant.Outline)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        ShadSeparator()
        Spacer(Modifier.height(16.dp))

        FieldLabel("Units", "Mbps matches speed tests and your internet plan.")
        ShadToggleGroup(
            options = SpeedUnit.entries,
            selected = settings.unit,
            label = { if (it == SpeedUnit.BITS) "Mbps" else "MB/s" },
            onSelect = actions::setUnit,
        )

        Spacer(Modifier.height(16.dp))
        ShadSeparator()
        Spacer(Modifier.height(16.dp))

        FieldLabel("Hide when idle", "Leave the status bar after 5 seconds below:")
        ShadRadioGroup(
            options = IdleThreshold.entries,
            selected = settings.idleThreshold,
            label = { idleLabel(it, settings.unit) },
            onSelect = actions::setIdleThreshold,
        )
    }
}

/** Spike-only: what the meter and overlay did and why, newest last, so results can be reported from the phone. */
@Composable
private fun DiagnosticsCard(lines: List<String>, actions: MainActions) {
    val c = Shad.colors
    ShadCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                CardHeader(
                    "Diagnostics",
                    "Testing aid. If something stops working, screenshot this. It survives restarts.",
                )
            }
            if (lines.isNotEmpty()) ShadButton("Clear", actions::clearDiagnostics, variant = ButtonVariant.Ghost)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (lines.isEmpty()) "No events yet." else lines.takeLast(15).joinToString("\n"),
            modifier = Modifier
                .fillMaxWidth()
                .background(c.muted, RoundedCornerShape(ShadRadius.lg))
                .padding(12.dp),
            style = Shad.type.muted.copy(fontFamily = GeistMono, fontSize = 11.sp, lineHeight = 16.sp),
            color = c.foreground,
        )
    }
}

internal fun idleLabel(threshold: IdleThreshold, unit: SpeedUnit): String =
    if (threshold == IdleThreshold.NEVER) "Never hide" else "Below ${SpeedFormat.full(threshold.bytesPerSec, unit)}"

@Composable
private fun AppCard(
    update: UpdateState,
    settings: UiSettings,
    tileOffer: TileOffer,
    backgroundUnrestricted: Boolean,
    currentVersion: String,
    actions: MainActions,
) {
    ShadCard {
        CardHeader("App")
        Spacer(Modifier.height(8.dp))
        SettingRow(
            title = "Automatic updates",
            description = "Download and install new builds on Wi-Fi.",
            checked = settings.autoUpdate,
            onCheckedChange = actions::setAutoUpdate,
        )
        ShadSeparator()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text("Background running", style = Shad.type.small, color = Shad.colors.foreground)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (backgroundUnrestricted) {
                        "Battery optimisation is off for Speed Meter, so Android won't stop it to save power."
                    } else {
                        "Battery optimisation can stop the meter after a while, especially on Samsung."
                    },
                    style = Shad.type.muted,
                    color = Shad.colors.mutedForeground,
                )
            }
            if (backgroundUnrestricted) {
                ShadBadge("Allowed", BadgeVariant.Secondary)
            } else {
                ShadButton("Allow", actions::allowBackgroundRunning, variant = ButtonVariant.Outline)
            }
        }
        ShadSeparator()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text("Quick Settings tile", style = Shad.type.small, color = Shad.colors.foreground)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (tileOffer == TileOffer.Manual) {
                        "Edit Quick Settings and drag in “Speed meter” to start and stop from the shade."
                    } else {
                        "Start and stop the meter from the notification shade."
                    },
                    style = Shad.type.muted,
                    color = Shad.colors.mutedForeground,
                )
            }
            when (tileOffer) {
                TileOffer.CanAdd -> ShadButton("Add", actions::addTile, variant = ButtonVariant.Outline)
                TileOffer.Added -> ShadBadge("Added", BadgeVariant.Secondary)
                TileOffer.Manual -> Unit
            }
        }
        ShadSeparator()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Version $currentVersion", style = Shad.type.small, color = Shad.colors.foreground)
                Text(
                    when (update) {
                        UpdateState.Idle -> "Not checked yet"
                        UpdateState.Checking -> "Checking…"
                        UpdateState.UpToDate -> "You're up to date"
                        is UpdateState.Failed -> if (update.info == null) update.message else "Update available"
                        else -> "Update available"
                    },
                    style = Shad.type.muted,
                    color = if (update is UpdateState.Failed && update.info == null) Shad.colors.destructive else Shad.colors.mutedForeground,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            ShadButton(
                "Check now",
                actions::checkForUpdate,
                variant = ButtonVariant.Outline,
                enabled = update != UpdateState.Checking,
            )
        }
    }
}

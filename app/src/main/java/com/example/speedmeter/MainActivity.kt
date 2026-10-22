package com.example.speedmeter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.speedmeter.ui.BadgeVariant
import com.example.speedmeter.ui.ButtonVariant
import com.example.speedmeter.ui.CardHeader
import com.example.speedmeter.ui.Geist
import com.example.speedmeter.ui.Shad
import com.example.speedmeter.ui.ShadAlert
import com.example.speedmeter.ui.ShadBadge
import com.example.speedmeter.ui.ShadButton
import com.example.speedmeter.ui.ShadCard
import com.example.speedmeter.ui.ShadProgress
import com.example.speedmeter.ui.ShadSeparator
import com.example.speedmeter.ui.ShadTheme
import com.example.speedmeter.ui.SettingRow
import com.example.speedmeter.update.UpdateManager
import com.example.speedmeter.update.UpdateState
import com.example.speedmeter.update.UpdateWorker

class MainActivity : ComponentActivity() {

    private var notificationsDenied by mutableStateOf(false)
    private var canInstallUpdates by mutableStateOf(false)
    private var transparentFallback by mutableStateOf(false)
    private var autoUpdate by mutableStateOf(true)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsDenied = !granted
            // Start regardless: the service still runs, it just can't show anything.
            SpeedMeterService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        transparentFallback = AppPrefs.transparentIconFallback(this)
        autoUpdate = AppPrefs.autoUpdate(this)

        setContent {
            ShadTheme {
                val meter by SpeedMeterService.state.collectAsStateWithLifecycle()
                val update by UpdateManager.state.collectAsStateWithLifecycle()
                MainScreen(
                    meter = meter,
                    update = update,
                    notificationsDenied = notificationsDenied,
                    canInstallUpdates = canInstallUpdates,
                    transparentFallback = transparentFallback,
                    autoUpdate = autoUpdate,
                    actions = actions,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        UpdateManager.activityVisible = true
        if (UpdateManager.state.value == UpdateState.Idle) UpdateManager.check()
    }

    override fun onResume() {
        super.onResume()
        // Pick up grants made in system settings while we were in the background.
        if (notificationsDenied && hasNotificationPermission()) notificationsDenied = false
        canInstallUpdates = packageManager.canRequestPackageInstalls()
    }

    override fun onStop() {
        UpdateManager.activityVisible = false
        super.onStop()
    }

    private val actions = object : MainActions {
        override fun start() {
            if (hasNotificationPermission()) {
                SpeedMeterService.start(this@MainActivity)
            } else {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        override fun stop() = SpeedMeterService.stop(this@MainActivity)

        override fun setTransparentFallback(enabled: Boolean) {
            transparentFallback = enabled
            AppPrefs.setTransparentIconFallback(this@MainActivity, enabled)
        }

        override fun setAutoUpdate(enabled: Boolean) {
            autoUpdate = enabled
            AppPrefs.setAutoUpdate(this@MainActivity, enabled)
            UpdateWorker.sync(this@MainActivity)
        }

        override fun checkForUpdate() = UpdateManager.check()

        override fun installUpdate(state: UpdateState) {
            val info = when (state) {
                is UpdateState.Available -> state.info
                is UpdateState.Failed -> state.info
                else -> null
            } ?: return
            UpdateManager.install(this@MainActivity, info)
        }

        override fun openNotificationSettings() {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }

        override fun openInstallSettings() {
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
            )
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

interface MainActions {
    fun start()
    fun stop()
    fun setTransparentFallback(enabled: Boolean)
    fun setAutoUpdate(enabled: Boolean)
    fun checkForUpdate()
    fun installUpdate(state: UpdateState)
    fun openNotificationSettings()
    fun openInstallSettings()
}

@Composable
internal fun MainScreen(
    meter: MeterState?,
    update: UpdateState,
    notificationsDenied: Boolean,
    canInstallUpdates: Boolean,
    transparentFallback: Boolean,
    autoUpdate: Boolean,
    actions: MainActions,
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

        if (notificationsDenied) {
            ShadAlert(
                title = "Notifications are off",
                description = "The speed can't appear in the status bar until notifications are allowed.",
                destructive = true,
            ) {
                ShadButton("Open settings", actions::openNotificationSettings, variant = ButtonVariant.Outline)
            }
        }

        MeterCard(meter, actions)
        UpdateCard(update, canInstallUpdates, currentVersion, actions)
        SettingsCard(update, transparentFallback, autoUpdate, currentVersion, actions)
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
private fun MeterCard(meter: MeterState?, actions: MainActions) {
    ShadCard {
        // Side by side only fits up to ~150% text; past that, stack so values never collide.
        if (LocalDensity.current.fontScale < 1.5f) {
            Row(Modifier.fillMaxWidth()) {
                Stat("Download", meter?.downBytesPerSec, Modifier.weight(1f))
                Stat("Upload", meter?.upBytesPerSec, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("Download", meter?.downBytesPerSec)
                Stat("Upload", meter?.upBytesPerSec)
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
private fun Stat(label: String, bytesPerSec: Long?, modifier: Modifier = Modifier) {
    val c = Shad.colors
    val full = bytesPerSec?.let(SpeedFormat::full)
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$label ${full ?: "not running"}" }) {
        Text(label.uppercase(), style = Shad.type.label, color = c.mutedForeground)
        Spacer(Modifier.height(6.dp))
        if (full == null) {
            Text("—", style = Shad.type.stat, color = c.mutedForeground)
        } else {
            val (number, unit) = full.split(" ", limit = 2)
            Text(
                buildAnnotatedString {
                    append(number)
                    withStyle(SpanStyle(fontFamily = Geist, fontSize = Shad.type.muted.fontSize, letterSpacing = 0.sp, color = c.mutedForeground)) {
                        append(" $unit")
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
private fun SettingsCard(
    update: UpdateState,
    transparentFallback: Boolean,
    autoUpdate: Boolean,
    currentVersion: String,
    actions: MainActions,
) {
    ShadCard {
        CardHeader("Settings")
        Spacer(Modifier.height(8.dp))
        SettingRow(
            title = "Automatic updates",
            description = "Download and install new builds on Wi-Fi.",
            checked = autoUpdate,
            onCheckedChange = actions::setAutoUpdate,
        )
        ShadSeparator()
        SettingRow(
            title = "Transparent-icon fallback",
            description = "When idle, blank the icon instead of moving to the minimum-importance channel.",
            checked = transparentFallback,
            onCheckedChange = actions::setTransparentFallback,
        )
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

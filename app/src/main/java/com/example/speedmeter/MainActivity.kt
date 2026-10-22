package com.example.speedmeter

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.speedmeter.ui.ShadTheme
import com.example.speedmeter.update.UpdateManager
import com.example.speedmeter.update.UpdateState
import com.example.speedmeter.update.UpdateWorker

class MainActivity : ComponentActivity() {

    companion object {
        /** Set by the Quick Settings tile when it couldn't start the meter itself. */
        const val EXTRA_START_METER = "com.example.speedmeter.extra.START_METER"
    }

    private var notificationsDenied by mutableStateOf(false)
    private var canInstallUpdates by mutableStateOf(false)
    private var settings by mutableStateOf(UiSettings())
    private var tileOffer by mutableStateOf(TileOffer.Manual)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsDenied = !granted
            // Start regardless: the service still runs, it just can't show anything.
            SpeedMeterService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = UiSettings(
            unit = AppPrefs.speedUnit(this),
            idleThreshold = AppPrefs.idleThreshold(this),
            transparentFallback = AppPrefs.transparentIconFallback(this),
            autoUpdate = AppPrefs.autoUpdate(this),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) tileOffer = TileOffer.CanAdd
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            ShadTheme {
                val meter by SpeedMeterService.state.collectAsStateWithLifecycle()
                val update by UpdateManager.state.collectAsStateWithLifecycle()
                MainScreen(
                    meter = meter,
                    update = update,
                    settings = settings,
                    notificationsDenied = notificationsDenied,
                    canInstallUpdates = canInstallUpdates,
                    tileOffer = tileOffer,
                    actions = actions,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        UpdateManager.activityVisible = true
        if (UpdateManager.state.value == UpdateState.Idle) UpdateManager.check()
        // The user left the meter on but Android stopped it (e.g. a refused sticky restart):
        // bring it back now that we're in the foreground and allowed to.
        if (AppPrefs.meterRunning(this) && SpeedMeterService.state.value == null && hasNotificationPermission()) {
            SpeedMeterService.start(this)
        }
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

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_METER, false) == true) {
            intent.removeExtra(EXTRA_START_METER)
            actions.start()
        }
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

        override fun setUnit(unit: SpeedUnit) {
            settings = settings.copy(unit = unit)
            AppPrefs.setSpeedUnit(this@MainActivity, unit)
        }

        override fun setIdleThreshold(threshold: IdleThreshold) {
            settings = settings.copy(idleThreshold = threshold)
            AppPrefs.setIdleThreshold(this@MainActivity, threshold)
        }

        override fun setTransparentFallback(enabled: Boolean) {
            settings = settings.copy(transparentFallback = enabled)
            AppPrefs.setTransparentIconFallback(this@MainActivity, enabled)
        }

        override fun setAutoUpdate(enabled: Boolean) {
            settings = settings.copy(autoUpdate = enabled)
            AppPrefs.setAutoUpdate(this@MainActivity, enabled)
            UpdateWorker.sync(this@MainActivity)
        }

        override fun addTile() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this@MainActivity, SpeedMeterTileService::class.java),
                getString(R.string.tile_label),
                Icon.createWithResource(this@MainActivity, R.drawable.ic_tile_speed),
                mainExecutor,
            ) { result ->
                if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                ) {
                    tileOffer = TileOffer.Added
                }
            }
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

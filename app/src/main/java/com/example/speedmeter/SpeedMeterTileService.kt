package com.example.speedmeter

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile: toggles the meter and shows the live download speed while the shade is open. */
class SpeedMeterTileService : TileService() {

    private var listening: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        listening = MainScope().also { scope ->
            scope.launch { SpeedMeterService.state.collect(::render) }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (SpeedMeterService.state.value != null) {
            SpeedMeterService.stop(this)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // The permission prompt needs an activity.
            openAppAndStart()
            return
        }
        try {
            SpeedMeterService.start(this)
        } catch (e: Exception) {
            // Some Android versions refuse a foreground start from a tile; the app can always do it.
            Log.w("SpeedMeter", "Tile start refused, opening app", e)
            openAppAndStart()
        }
    }

    private fun openAppAndStart() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_START_METER, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(state: MeterState?) {
        val tile = qsTile ?: return
        tile.state = if (state == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val unit = AppPrefs.speedUnit(this)
            tile.subtitle = when {
                state == null -> getString(R.string.tile_off)
                state.paused -> getString(R.string.tile_paused)
                else -> "↓ ${SpeedFormat.full(state.downBytesPerSec, unit)}"
            }
        }
        tile.updateTile()
    }

    companion object {
        /** Asks the system to rebind the tile so it picks up a start or stop. */
        fun refresh(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, SpeedMeterTileService::class.java))
            } catch (e: Exception) {
                // Not added to Quick Settings, or the system refused; nothing to refresh.
            }
        }
    }
}

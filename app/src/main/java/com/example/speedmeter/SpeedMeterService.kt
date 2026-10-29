package com.example.speedmeter

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MeterState(
    val downBytesPerSec: Long = 0,
    val upBytesPerSec: Long = 0,
    /** True once traffic has been below the idle threshold long enough to hide. */
    val hidden: Boolean = false,
    /** True while the screen is off and polling is suspended. */
    val paused: Boolean = false,
)

/**
 * Foreground service that samples [TrafficStats] once per second and shows the rate
 * as the notification small icon, so it appears in the status bar.
 */
class SpeedMeterService : Service() {

    companion object {
        private const val ACTION_STOP = "com.example.speedmeter.action.STOP"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_VISIBLE = "speed_visible"
        private const val CHANNEL_IDLE = "speed_idle"
        /** Overlay mode's static entry, on its own channel so it can be switched off without
         *  silencing update prompts. */
        const val CHANNEL_RUNNING = "meter_running"

        private const val POLL_INTERVAL_MS = 1_000L
        private const val IDLE_HIDE_DELAY_MS = 5_000L
        /** Diagnostics heartbeat: a gap in these lines shows when the meter stopped ticking. */
        private const val STATUS_INTERVAL_MS = 10 * 60_000L

        private val _state = MutableStateFlow<MeterState?>(null)

        /** Current meter state, or null when the service is not running. */
        val state: StateFlow<MeterState?> = _state.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SpeedMeterService::class.java))
        }

        /** Idempotent; also called by the app so its "Hide notification" link has a channel to open. */
        fun createChannels(context: Context) {
            // LOW: icon in the status bar, no sound or heads-up.
            val visible = NotificationChannel(
                CHANNEL_VISIBLE,
                context.getString(R.string.channel_visible_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_visible_desc)
                setShowBadge(false)
            }
            // MIN: shade only, never in the status bar.
            val idle = NotificationChannel(
                CHANNEL_IDLE,
                context.getString(R.string.channel_idle_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = context.getString(R.string.channel_idle_desc)
                setShowBadge(false)
            }
            val running = NotificationChannel(
                CHANNEL_RUNNING,
                context.getString(R.string.channel_running_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = context.getString(R.string.channel_running_desc)
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannels(listOf(visible, idle, running))
        }

        /** Explicit user stop: also clears the "keep running" flag so nothing restarts it. */
        fun stop(context: Context) {
            AppPrefs.setMeterRunning(context, false)
            context.stopService(Intent(context, SpeedMeterService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager
    private lateinit var renderer: SpeedIconRenderer
    private lateinit var overlay: SpeedOverlay

    private var polling = false
    private val sampler = RateSampler(IdleThreshold.LOW.bytesPerSec, IDLE_HIDE_DELAY_MS)
    private var meter = MeterState()
    private var lastPostedKey: String? = null
    private var lastStatusAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            sample()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    // Field, not a lambda at the call site: SharedPreferences only holds listeners weakly.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            AppPrefs.KEY_OVERLAY -> syncOverlay()
            AppPrefs.KEY_OVERLAY_POSITION -> overlay.setPosition(AppPrefs.overlayPosition(this))
            AppPrefs.KEY_OVERLAY_VERTICAL -> overlay.setVerticalPosition(AppPrefs.overlayVertical(this))
            AppPrefs.KEY_OVERLAY_HIDE_LANDSCAPE -> overlay.setHideInLandscape(AppPrefs.overlayHideInLandscape(this))
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    DiagnosticsLog.log("screen off: paused")
                    pausePolling()
                }
                Intent.ACTION_SCREEN_ON -> {
                    DiagnosticsLog.log("screen on: resumed")
                    overlay.refreshIfHiddenForFullScreen()
                    resumePolling()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        renderer = SpeedIconRenderer(this)
        overlay = SpeedOverlay(this)
        AppPrefs.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
        createChannels(this)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        SpeedMeterTileService.refresh(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            DiagnosticsLog.log("meter stopped from notification")
            AppPrefs.setMeterRunning(this, false)
            stopSelf()
            return START_NOT_STICKY
        }

        // A null intent means the system restarted us after killing the process (START_STICKY).
        DiagnosticsLog.log(if (intent == null) "meter restarted by Android after a kill" else "meter started")
        AppPrefs.setMeterRunning(this, true)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(meter),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            // Android 12+ can refuse a foreground start from the background (e.g. some sticky
            // restarts). Keep the flag set: the next boot or app open brings the meter back.
            Log.w("SpeedMeter", "Foreground start refused", e)
            DiagnosticsLog.log("Android refused to start the meter: ${e.javaClass.simpleName}")
            stopSelf()
            return START_NOT_STICKY
        }
        lastPostedKey = null
        syncOverlay()

        val interactive = getSystemService(PowerManager::class.java).isInteractive
        if (interactive) resumePolling() else pausePolling()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        polling = false
        unregisterReceiver(screenReceiver)
        DiagnosticsLog.log("meter service stopped")
        AppPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        overlay.hide()
        _state.value = null
        SpeedMeterTileService.refresh(this)
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay.relayout() // rotation changes the status bar height and screen width
    }

    /** Shows or removes the overlay to match the setting, then reposts the notification for the new mode. */
    private fun syncOverlay() {
        if (AppPrefs.overlayActive(this)) {
            overlay.show(
                AppPrefs.overlayPosition(this),
                AppPrefs.overlayVertical(this),
                AppPrefs.overlayHideInLandscape(this),
            )
            overlay.render(meter, AppPrefs.speedUnit(this))
        } else {
            overlay.hide()
        }
        lastPostedKey = null
        postNotification(meter)
    }

    private fun resumePolling() {
        if (polling) return
        polling = true
        sampler.reset()
        update(meter.copy(paused = false))
        handler.post(tick)
    }

    private fun pausePolling() {
        polling = false
        handler.removeCallbacks(tick)
        update(meter.copy(downBytesPerSec = 0, upBytesPerSec = 0, paused = true))
    }

    private fun sample() {
        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return

        sampler.idleThresholdBytesPerSec = AppPrefs.idleThreshold(this).bytesPerSec
        val reading = sampler.sample(now, rx, tx) ?: return
        update(MeterState(reading.downBytesPerSec, reading.upBytesPerSec, reading.hidden, paused = false))
        if (lastStatusAt == 0L || now - lastStatusAt >= STATUS_INTERVAL_MS) {
            lastStatusAt = now
            logStatus()
        }
    }

    private fun logStatus() {
        val unit = AppPrefs.speedUnit(this)
        val speed = SpeedOverlay.format(meter.downBytesPerSec, meter.upBytesPerSec, unit)
        val mode = if (AppPrefs.overlayActive(this)) "overlay ${overlay.statusLabel}" else "icon${if (meter.hidden) ", idle" else ""}"
        DiagnosticsLog.log("status: $mode, $speed")
    }

    private fun update(newState: MeterState) {
        meter = newState
        _state.value = newState
        // Picks up a "Display over other apps" grant made after the setting was switched on.
        // overlayActive only does the permission check while the setting is on.
        if (AppPrefs.overlayActive(this) != overlay.isShowing) syncOverlay()
        if (overlay.isShowing) overlay.render(newState, AppPrefs.speedUnit(this))
        postNotification(newState)
    }

    private fun postNotification(state: MeterState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return // Service keeps running; the system just won't show anything.
        }

        // Skip reposting when nothing visible changed; keeps notification churn down.
        val unit = AppPrefs.speedUnit(this)
        // In overlay mode the notification is static, so it never needs reposting per tick.
        val key = if (overlay.isShowing) "overlay" else listOf(
            SpeedFormat.full(state.downBytesPerSec, unit),
            SpeedFormat.full(state.upBytesPerSec, unit),
            state.hidden,
            state.paused,
        ).joinToString("|")
        if (key == lastPostedKey) return
        lastPostedKey = key

        notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: MeterState): Notification {
        if (overlay.isShowing) return buildOverlayModeNotification()

        // Idle: repost on the MIN channel, which keeps it out of the status bar. On a Samsung A55
        // that still left a gap; a transparent icon can only ever leave a gap, so that variant was dropped.
        val onIdleChannel = state.hidden
        val unit = AppPrefs.speedUnit(this)

        val icon = renderer.render(state.downBytesPerSec, state.upBytesPerSec, unit)

        val title = "↓ ${SpeedFormat.full(state.downBytesPerSec, unit)}   ↑ ${SpeedFormat.full(state.upBytesPerSec, unit)}"
        val text = when {
            state.paused -> "Paused while the screen is off"
            state.hidden -> "Idle, hidden from status bar"
            else -> null
        }

        val openApp = openAppIntent()
        val stop = stopIntent()

        return NotificationCompat.Builder(this, if (onIdleChannel) CHANNEL_IDLE else CHANNEL_VISIBLE)
            .setSmallIcon(IconCompat.createWithBitmap(icon))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notification_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(if (onIdleChannel) NotificationCompat.PRIORITY_MIN else NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * The foreground service still needs a notification. In overlay mode it goes on its own MIN
     * channel (never in the status bar, folded at the bottom of the panel). If the user turns that
     * channel or all notifications off, Android 13+ drops it from the panel and the meter keeps running.
     */
    private fun buildOverlayModeNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_tile_speed)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(openAppIntent())
            .addAction(0, getString(R.string.notification_stop), stopIntent())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, SpeedMeterService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

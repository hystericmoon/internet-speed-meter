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
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
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

        private const val POLL_INTERVAL_MS = 1_000L
        private const val IDLE_THRESHOLD_BYTES_PER_SEC = 1_024L
        private const val IDLE_HIDE_DELAY_MS = 5_000L

        private val _state = MutableStateFlow<MeterState?>(null)

        /** Current meter state, or null when the service is not running. */
        val state: StateFlow<MeterState?> = _state.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SpeedMeterService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SpeedMeterService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager
    private lateinit var renderer: SpeedIconRenderer

    private var polling = false
    private var lastSampleAt = 0L
    private var lastRx = 0L
    private var lastTx = 0L
    /** elapsedRealtime when traffic first dropped below the threshold; 0 while active. */
    private var idleSince = 0L
    private var meter = MeterState()
    private var lastPostedKey: String? = null

    private val tick = object : Runnable {
        override fun run() {
            sample()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> pausePolling()
                Intent.ACTION_SCREEN_ON -> resumePolling()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        renderer = SpeedIconRenderer(this)
        createChannels()
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        AppPrefs.setMeterRunning(this, true)
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
        lastPostedKey = null

        val interactive = getSystemService(PowerManager::class.java).isInteractive
        if (interactive) resumePolling() else pausePolling()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        polling = false
        unregisterReceiver(screenReceiver)
        // Only reached on a deliberate stop; a process kill (e.g. during a self-update)
        // skips this, so the flag stays set and PackageReplacedReceiver restarts us.
        AppPrefs.setMeterRunning(this, false)
        _state.value = null
        super.onDestroy()
    }

    private fun resumePolling() {
        if (polling) return
        polling = true
        // Fresh baseline: bytes moved while the screen was off must not count as one spike.
        lastSampleAt = 0L
        if (!meter.hidden) idleSince = 0L
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

        if (lastSampleAt == 0L) {
            lastSampleAt = now
            lastRx = rx
            lastTx = tx
            return
        }

        val elapsed = (now - lastSampleAt).coerceAtLeast(1)
        // Counters can go backwards when an interface resets; treat that as zero.
        val down = (rx - lastRx).coerceAtLeast(0) * 1_000 / elapsed
        val up = (tx - lastTx).coerceAtLeast(0) * 1_000 / elapsed
        lastSampleAt = now
        lastRx = rx
        lastTx = tx

        if (down + up >= IDLE_THRESHOLD_BYTES_PER_SEC) {
            idleSince = 0L
        } else if (idleSince == 0L) {
            idleSince = now
        }
        val hidden = idleSince != 0L && now - idleSince >= IDLE_HIDE_DELAY_MS

        update(MeterState(down, up, hidden, paused = false))
    }

    private fun update(newState: MeterState) {
        meter = newState
        _state.value = newState
        postNotification(newState)
    }

    private fun postNotification(state: MeterState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return // Service keeps running; the system just won't show anything.
        }

        // Skip reposting when nothing visible changed; keeps notification churn down.
        val key = listOf(
            SpeedFormat.full(state.downBytesPerSec),
            SpeedFormat.full(state.upBytesPerSec),
            state.hidden,
            state.paused,
            AppPrefs.transparentIconFallback(this),
        ).joinToString("|")
        if (key == lastPostedKey) return
        lastPostedKey = key

        notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: MeterState): Notification {
        val transparentFallback = state.hidden && AppPrefs.transparentIconFallback(this)
        val onIdleChannel = state.hidden && !transparentFallback

        val icon = if (transparentFallback) {
            renderer.transparentIcon
        } else {
            renderer.render(state.downBytesPerSec, state.upBytesPerSec)
        }

        val title = "↓ ${SpeedFormat.full(state.downBytesPerSec)}   ↑ ${SpeedFormat.full(state.upBytesPerSec)}"
        val text = when {
            state.paused -> "Paused while the screen is off"
            state.hidden -> "Idle, hidden from status bar"
            else -> null
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, SpeedMeterService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

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

    private fun createChannels() {
        // LOW: icon in the status bar, no sound or heads-up.
        val visible = NotificationChannel(
            CHANNEL_VISIBLE,
            getString(R.string.channel_visible_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_visible_desc)
            setShowBadge(false)
        }
        // MIN: shade only, never in the status bar.
        val idle = NotificationChannel(
            CHANNEL_IDLE,
            getString(R.string.channel_idle_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.channel_idle_desc)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannels(listOf(visible, idle))
    }
}

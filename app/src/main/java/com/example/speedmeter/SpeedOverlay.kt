package com.example.speedmeter

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import kotlin.math.roundToInt

/**
 * "Status bar overlay" mode: draws the speed in a floating window over the status bar
 * strip instead of using the notification icon, so the notification can stay out of
 * the status bar (MIN channel) or be turned off entirely.
 *
 * Since Android 12, app overlays sit *below* the status bar layer. That's fine on the
 * usual transparent bar, but a solid bar can cover it. Touches never reach us: the window
 * is not touchable and its alpha is capped at 0.8, Android's limit for letting touches
 * pass through untrusted overlays.
 *
 * Hiding for full-screen apps uses two signals:
 * 1. a 1px invisible "probe" window that fits the status bar: it sits at y = 0 only when
 *    the status bar is gone;
 * 2. orientation: optionally hide in landscape, where full-screen video and games live.
 * Insets visibility reported to the overlay window is only logged. On a Samsung A55 (One UI,
 * Android 15) it missed real full screen, then got stuck on "hidden" for 40+ minutes in
 * portrait until the meter was restarted, so it can't be trusted either way.
 *
 * When the network is idle the overlay *dims* rather than hides: a meter that vanishes
 * reads the same as a broken one. Hiding is kept for full screen, where it must go.
 */
class SpeedOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var probe: View? = null

    private var position = 0.5f
    private var verticalPosition = 0.5f
    private var hideInLandscape = true
    private var hiddenWhenIdle = false
    private var lastLandscape: Boolean? = null
    /** Last logged state, e.g. "visible" or "hidden: landscape"; "" = never logged. */
    private var lastState = ""
    /** Set once Android has attached the window; detached after that means it was dropped. */
    private var attached = false
    /** The service retries every second; log a refused addView once, not every time. */
    private var addFailureLogged = false

    // Full-screen signals; see class doc.
    private var insetsSayHidden = false
    private var probeSaysHidden = false
    /** The probe only counts once we've seen it pushed down by a visible status bar. */
    private var probeCalibrated = false

    val isShowing: Boolean get() = view != null

    fun show(positionFraction: Float, verticalFraction: Float, hideInLandscape: Boolean) {
        position = positionFraction
        verticalPosition = verticalFraction
        this.hideInLandscape = hideInLandscape
        if (view != null) {
            relayout()
            return
        }
        if (!Settings.canDrawOverlays(context)) return

        val text = TextView(context).apply {
            setTextColor(Color.WHITE)
            // Shadow keeps white text legible when the bar behind it is light.
            setShadowLayer(3f, 0f, 1f, 0x99000000.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = ResourcesCompat.getFont(context, R.font.geist_mono_medium)
            gravity = Gravity.CENTER
            maxLines = 1
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // Fixed width (widest possible readout) so the overlay doesn't shift as digits change.
            text = format(WIDEST, WIDEST, SpeedUnit.BYTES)
        }
        text.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

        val lp = WindowManager.LayoutParams(
            text.measuredWidth,
            statusBarHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = 0
            alpha = MAX_PASS_THROUGH_ALPHA
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            // Otherwise Android lays the window out *below* the status bar.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
        }

        watchInsets(text)
        text.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                attached = true
            }

            override fun onViewDetachedFromWindow(v: View) {}
        })
        try {
            windowManager.addView(text, lp)
        } catch (e: Exception) {
            Log.w("SpeedMeter", "Couldn't add overlay", e)
            if (!addFailureLogged) DiagnosticsLog.log("couldn't add overlay: ${e.javaClass.simpleName}")
            addFailureLogged = true
            return
        }
        addFailureLogged = false
        view = text
        params = lp
        addProbe()
        DiagnosticsLog.log("overlay shown")
        relayout()
    }

    fun hide() {
        if (view != null) DiagnosticsLog.log("overlay removed")
        view?.let { runCatching { windowManager.removeView(it) } }
        probe?.let { runCatching { windowManager.removeView(it) } }
        view = null
        params = null
        probe = null
        lastLandscape = null
        lastState = ""
        attached = false
        probeCalibrated = false
        insetsSayHidden = false
        probeSaysHidden = false
    }

    /**
     * Screen on: if the overlay is hidden for full screen, rebuild both windows so every signal
     * is read fresh. Stuck window state otherwise survives screen off/on (seen on One UI), and
     * only a restart cleared it. Probe calibration is kept: it's a fact about the device, and
     * keeping it means a real full-screen app stays covered after the rebuild.
     */
    fun refreshIfHiddenForFullScreen() {
        if (view == null || !lastState.startsWith("hidden: full screen")) return
        DiagnosticsLog.log("overlay rebuilt after screen on (was $lastState)")
        val calibrated = probeCalibrated
        hide()
        probeCalibrated = calibrated
        show(position, verticalPosition, hideInLandscape)
    }

    fun setPosition(fraction: Float) {
        position = fraction
        relayout()
    }

    fun setVerticalPosition(fraction: Float) {
        verticalPosition = fraction
        relayout()
    }

    fun setHideInLandscape(enabled: Boolean) {
        hideInLandscape = enabled
        applyVisibility()
    }

    /** Called every second while polling. Also re-reads the full-screen signals, so a missed
     *  or stale event (e.g. around lock/unlock) can't keep the overlay hidden for good. */
    fun render(state: MeterState, unit: SpeedUnit) {
        if (attached && view?.isAttachedToWindow == false) {
            // Android removed the window without telling us ("visible" in the log, nothing on
            // screen). Start over, so the overlay comes back within a second.
            DiagnosticsLog.log("overlay window lost, re-adding")
            hide()
            show(position, verticalPosition, hideInLandscape)
        }
        val v = view ?: return
        v.text = format(state.downBytesPerSec, state.upBytesPerSec, unit)
        hiddenWhenIdle = state.hidden
        pollSignals()
        applyVisibility()
    }

    /** Call on rotation: status bar height, screen width and orientation change. */
    fun relayout() {
        val v = view ?: return
        val lp = params ?: return
        lp.height = statusBarHeight()
        lp.x = overlayX(position, screenWidth(), lp.width)
        lp.y = overlayY(verticalPosition, lp.height)
        runCatching { windowManager.updateViewLayout(v, lp) }
        val landscape = isLandscape()
        if (landscape != lastLandscape) {
            lastLandscape = landscape
            DiagnosticsLog.log(if (landscape) "orientation: landscape" else "orientation: portrait")
        }
        applyVisibility()
    }

    private fun isLandscape(): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun applyVisibility() {
        val v = view ?: return
        val reason = hideReason(
            landscape = hideInLandscape && isLandscape(),
            probeHidden = probeCalibrated && probeSaysHidden,
        )
        val state = reason?.let { "hidden: $it" } ?: if (hiddenWhenIdle) "dimmed: idle" else "visible"
        if (state != lastState) {
            lastState = state
            DiagnosticsLog.log("overlay $state")
        }
        // INVISIBLE, not GONE: the view must stay laid out to keep receiving inset changes.
        v.visibility = if (reason == null) View.VISIBLE else View.INVISIBLE
        // View alpha, on top of the window's 0.8 cap: no relayout needed to dim.
        v.alpha = if (hiddenWhenIdle) IDLE_ALPHA else 1f
    }

    /** One-line summary for the periodic Diagnostics status line, e.g. "dimmed: idle". */
    val statusLabel: String
        get() = if (view == null) "not shown" else lastState.ifEmpty { "visible" }

    private fun pollSignals() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.rootWindowInsets?.let { setInsetsHidden(!it.isVisible(WindowInsets.Type.statusBars())) }
        }
        probe?.let(::readProbe)
    }

    private fun setInsetsHidden(hidden: Boolean) {
        if (hidden != insetsSayHidden) DiagnosticsLog.log("insets (log only): status bar ${if (hidden) "hidden" else "shown"}")
        insetsSayHidden = hidden
    }

    @Suppress("DEPRECATION")
    private fun watchInsets(v: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            v.setOnApplyWindowInsetsListener { _, insets ->
                setInsetsHidden(!insets.isVisible(WindowInsets.Type.statusBars()))
                applyVisibility()
                insets
            }
        } else {
            v.setOnSystemUiVisibilityChangeListener { flags ->
                setInsetsHidden(flags and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
                applyVisibility()
            }
        }
    }

    /**
     * 1px-wide, full-height, invisible window that *does* fit the status bar. While the bar
     * is shown it starts below it; when a full-screen app hides the bar it moves to y = 0.
     */
    private fun addProbe() {
        val p = View(context)
        val lp = WindowManager.LayoutParams(
            1,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            alpha = 0f
            // Short edges: with the bar hidden the window reaches y = 0 instead of stopping at the cutout.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        p.viewTreeObserver.addOnGlobalLayoutListener {
            readProbe(p)
            applyVisibility()
        }
        try {
            windowManager.addView(p, lp)
            probe = p
        } catch (e: Exception) {
            Log.w("SpeedMeter", "Couldn't add full-screen probe", e)
        }
    }

    private val probeLocation = IntArray(2)

    private fun readProbe(p: View) {
        p.getLocationOnScreen(probeLocation)
        val top = probeLocation[1]
        if (top > 0 && !probeCalibrated) {
            probeCalibrated = true
            DiagnosticsLog.log("probe: calibrated (top=$top)")
        }
        val hidden = top == 0
        if (probeCalibrated && hidden != probeSaysHidden) {
            DiagnosticsLog.log("probe: status bar ${if (hidden) "hidden" else "shown"} (top=$top)")
        }
        probeSaysHidden = hidden
    }

    private fun screenWidth(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            context.resources.displayMetrics.widthPixels
        }

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun statusBarHeight(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val top = windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            if (top > 0) return top
        }
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) {
            context.resources.getDimensionPixelSize(id)
        } else {
            (24 * context.resources.displayMetrics.density).roundToInt()
        }
    }

    companion object {
        /** Why the overlay is hidden, or null if it should show. First match wins (it's what the log reports).
         *  Idle isn't a reason: the overlay dims instead (see [IDLE_ALPHA]). */
        fun hideReason(landscape: Boolean, probeHidden: Boolean): String? =
            when {
                landscape -> "landscape"
                probeHidden -> "full screen (probe)"
                else -> null
            }

        /** Android 12+ blocks touches through overlays more opaque than this. */
        private const val MAX_PASS_THROUGH_ALPHA = 0.8f
        /** Idle opacity, multiplied by the window's 0.8: about 36% in total. Present, but quiet. */
        const val IDLE_ALPHA = 0.45f
        private const val WIDEST = 999L * 1_024 * 1_024

        fun format(downBytesPerSec: Long, upBytesPerSec: Long, unit: SpeedUnit): String =
            "↓${SpeedFormat.compact(downBytesPerSec, unit)} ↑${SpeedFormat.compact(upBytesPerSec, unit)}"

        /**
         * Vertical offset for a 0..1 position: 0.5 = centred on the status bar, 0 / 1 = half the
         * bar's height up / down, enough to line up with the clock on any layout.
         * (FLAG_LAYOUT_NO_LIMITS lets the window sit partly above the screen edge.)
         */
        fun overlayY(fraction: Float, barHeight: Int): Int =
            ((fraction.coerceIn(0f, 1f) - 0.5f) * barHeight).roundToInt()

        /** Left edge for a 0..1 position: 0 = flush left, 1 = flush right, always fully on screen. */
        fun overlayX(fraction: Float, screenWidth: Int, viewWidth: Int): Int =
            ((screenWidth - viewWidth).coerceAtLeast(0) * fraction.coerceIn(0f, 1f)).roundToInt()
    }
}

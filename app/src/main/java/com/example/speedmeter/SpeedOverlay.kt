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
 * Hiding for full-screen apps uses three independent signals, because no single one is
 * reliable across OEMs (window insets alone failed on a Samsung A55, One UI / Android 15):
 * 1. insets visibility reported to the overlay window;
 * 2. a 1px invisible "probe" window that fits the status bar: it sits at y = 0 only when
 *    the status bar is gone;
 * 3. orientation: optionally hide in landscape, where full-screen video and games live.
 */
class SpeedOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var probe: View? = null

    private var position = 0.5f
    private var hideInLandscape = true
    private var hiddenWhenIdle = false
    private var lastLandscape: Boolean? = null

    // Full-screen signals; see class doc.
    private var insetsSayHidden = false
    private var probeSaysHidden = false
    /** The probe only counts once we've seen it pushed down by a visible status bar. */
    private var probeCalibrated = false

    val isShowing: Boolean get() = view != null

    fun show(positionFraction: Float, hideInLandscape: Boolean) {
        position = positionFraction
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
        try {
            windowManager.addView(text, lp)
        } catch (e: Exception) {
            Log.w("SpeedMeter", "Couldn't add overlay", e)
            return
        }
        view = text
        params = lp
        addProbe()
        OverlayDiagnostics.log("overlay shown")
        relayout()
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        probe?.let { runCatching { windowManager.removeView(it) } }
        view = null
        params = null
        probe = null
        lastLandscape = null
        probeCalibrated = false
        probeSaysHidden = false
        insetsSayHidden = false
    }

    fun setPosition(fraction: Float) {
        position = fraction
        relayout()
    }

    fun setHideInLandscape(enabled: Boolean) {
        hideInLandscape = enabled
        applyVisibility()
    }

    fun render(state: MeterState, unit: SpeedUnit) {
        val v = view ?: return
        v.text = format(state.downBytesPerSec, state.upBytesPerSec, unit)
        hiddenWhenIdle = state.hidden
        applyVisibility()
    }

    /** Call on rotation: status bar height, screen width and orientation change. */
    fun relayout() {
        val v = view ?: return
        val lp = params ?: return
        lp.height = statusBarHeight()
        lp.x = overlayX(position, screenWidth(), lp.width)
        runCatching { windowManager.updateViewLayout(v, lp) }
        val landscape = isLandscape()
        if (landscape != lastLandscape) {
            lastLandscape = landscape
            OverlayDiagnostics.log(if (landscape) "orientation: landscape" else "orientation: portrait")
        }
        applyVisibility()
    }

    private fun isLandscape(): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun applyVisibility() {
        val fullScreen = insetsSayHidden || (probeCalibrated && probeSaysHidden)
        val hide = hiddenWhenIdle || fullScreen || (hideInLandscape && isLandscape())
        // INVISIBLE, not GONE: the view must stay laid out to keep receiving inset changes.
        view?.visibility = if (hide) View.INVISIBLE else View.VISIBLE
    }

    @Suppress("DEPRECATION")
    private fun watchInsets(v: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            v.setOnApplyWindowInsetsListener { _, insets ->
                val hidden = !insets.isVisible(WindowInsets.Type.statusBars())
                if (hidden != insetsSayHidden) OverlayDiagnostics.log("insets: status bar ${if (hidden) "hidden" else "shown"}")
                insetsSayHidden = hidden
                applyVisibility()
                insets
            }
        } else {
            v.setOnSystemUiVisibilityChangeListener { flags ->
                val hidden = flags and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
                if (hidden != insetsSayHidden) OverlayDiagnostics.log("ui flags: status bar ${if (hidden) "hidden" else "shown"}")
                insetsSayHidden = hidden
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
        val location = IntArray(2)
        p.viewTreeObserver.addOnGlobalLayoutListener {
            p.getLocationOnScreen(location)
            val top = location[1]
            if (top > 0 && !probeCalibrated) {
                probeCalibrated = true
                OverlayDiagnostics.log("probe: calibrated (top=$top)")
            }
            val hidden = top == 0
            if (probeCalibrated && hidden != probeSaysHidden) {
                OverlayDiagnostics.log("probe: status bar ${if (hidden) "hidden" else "shown"} (top=$top)")
            }
            probeSaysHidden = hidden
            applyVisibility()
        }
        try {
            windowManager.addView(p, lp)
            probe = p
        } catch (e: Exception) {
            Log.w("SpeedMeter", "Couldn't add full-screen probe", e)
        }
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
        /** Android 12+ blocks touches through overlays more opaque than this. */
        private const val MAX_PASS_THROUGH_ALPHA = 0.8f
        private const val WIDEST = 999L * 1_024 * 1_024

        fun format(downBytesPerSec: Long, upBytesPerSec: Long, unit: SpeedUnit): String =
            "↓${SpeedFormat.compact(downBytesPerSec, unit)} ↑${SpeedFormat.compact(upBytesPerSec, unit)}"

        /** Left edge for a 0..1 position: 0 = flush left, 1 = flush right, always fully on screen. */
        fun overlayX(fraction: Float, screenWidth: Int, viewWidth: Int): Int =
            ((screenWidth - viewWidth).coerceAtLeast(0) * fraction.coerceIn(0f, 1f)).roundToInt()
    }
}

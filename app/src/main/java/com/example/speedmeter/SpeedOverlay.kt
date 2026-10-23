package com.example.speedmeter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
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
 */
class SpeedOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    private var position = 0.5f
    private var hiddenWhenIdle = false
    /** False while a full-screen app has hidden the status bar (video, games). */
    private var statusBarShown = true

    val isShowing: Boolean get() = view != null

    fun show(positionFraction: Float) {
        position = positionFraction
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

        watchStatusBar(text)
        try {
            windowManager.addView(text, lp)
        } catch (e: Exception) {
            Log.w("SpeedMeter", "Couldn't add overlay", e)
            return
        }
        view = text
        params = lp
        relayout()
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        params = null
    }

    fun setPosition(fraction: Float) {
        position = fraction
        relayout()
    }

    fun render(state: MeterState, unit: SpeedUnit) {
        val v = view ?: return
        v.text = format(state.downBytesPerSec, state.upBytesPerSec, unit)
        hiddenWhenIdle = state.hidden
        applyVisibility()
    }

    /** Call on rotation: status bar height and screen width change. */
    fun relayout() {
        val v = view ?: return
        val lp = params ?: return
        lp.height = statusBarHeight()
        lp.x = overlayX(position, screenWidth(), lp.width)
        runCatching { windowManager.updateViewLayout(v, lp) }
    }

    private fun applyVisibility() {
        // INVISIBLE, not GONE: the view must stay laid out to keep receiving inset changes.
        view?.visibility = if (hiddenWhenIdle || !statusBarShown) View.INVISIBLE else View.VISIBLE
    }

    @Suppress("DEPRECATION")
    private fun watchStatusBar(v: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            v.setOnApplyWindowInsetsListener { _, insets ->
                statusBarShown = insets.isVisible(WindowInsets.Type.statusBars())
                applyVisibility()
                insets
            }
        } else {
            v.setOnSystemUiVisibilityChangeListener { flags ->
                statusBarShown = flags and View.SYSTEM_UI_FLAG_FULLSCREEN == 0
                applyVisibility()
            }
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

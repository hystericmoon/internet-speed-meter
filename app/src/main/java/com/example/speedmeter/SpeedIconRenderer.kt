package com.example.speedmeter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.roundToInt

/**
 * Draws download (top line) and upload (bottom line) speed into a square bitmap
 * used as the notification small icon. The system treats small icons as an alpha
 * mask and tints them, so we only draw opaque white on transparent.
 *
 * Overlay mode draws the same bitmap itself, so it looks like the icon. There nothing
 * tints it, so [pill] puts a dark rounded background behind the numbers, like Android's
 * own call and recording chips, so they read on any bar, light or dark. (In the icon the
 * tint would turn the pill into a white blob.)
 */
class SpeedIconRenderer(context: Context, private val pill: Boolean = false) {

    private val density = context.resources.displayMetrics.density

    /** Status bar icons are 24dp; render at the device's native density. */
    val sizePx = (24 * density).roundToInt()

    /** Text inset inside the pill, so digits don't touch its rounded edges. */
    private val padXPx = if (pill) 2 * density else 0f
    private val padYPx = if (pill) density else 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    /**
     * zinc-800 at 90%: light enough to stand out on a black bar (zinc-950 disappeared there),
     * dark enough that, with the overlay window's 0.8 alpha applied to the whole image, white
     * text on it is ~6:1 even over a pure white bar (WCAG AA needs 4.5:1), ~10:1 over a dark one.
     */
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE627272A.toInt() }
    private val pillRect = RectF()
    private val digitBounds = Rect()

    fun render(downBytesPerSec: Long, upBytesPerSec: Long, unit: SpeedUnit): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (pill) {
            pillRect.set(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
            canvas.drawRoundRect(pillRect, 6 * density, 6 * density, pillPaint)
        }
        val lineHeight = (sizePx - 2 * padYPx) / 2f
        drawLine(canvas, SpeedFormat.compact(downBytesPerSec, unit), top = padYPx, lineHeight)
        drawLine(canvas, SpeedFormat.compact(upBytesPerSec, unit), top = padYPx + lineHeight, lineHeight)
        return bitmap
    }

    private fun drawLine(canvas: Canvas, text: String, top: Float, lineHeight: Float) {
        // Digits are ~0.7 of the text size, so this fills ~80% of the line height.
        paint.textSize = lineHeight * 1.15f
        paint.textScaleX = 1f
        val width = paint.measureText(text)
        val room = sizePx - 2 * padXPx
        if (width > room) paint.textScaleX = room / width

        // Center on digit height (no descenders in our strings) rather than font metrics.
        paint.getTextBounds("0", 0, 1, digitBounds)
        val baseline = top + (lineHeight + digitBounds.height()) / 2f
        canvas.drawText(text, sizePx / 2f, baseline, paint)
    }
}

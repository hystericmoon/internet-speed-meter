package com.example.speedmeter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.roundToInt

/**
 * Draws download (top line) and upload (bottom line) speed into a square bitmap
 * used as the notification small icon. The system treats small icons as an alpha
 * mask and tints them, so we only draw opaque white on transparent.
 *
 * Overlay mode draws the same bitmap itself, so it looks like the icon. There nothing
 * tints it, so [shadow] adds a soft dark shadow to keep it legible on light bars. (A
 * shadow in the icon would be turned into a white blur by the tint.)
 */
class SpeedIconRenderer(context: Context, private val shadow: Boolean = false) {

    private val density = context.resources.displayMetrics.density

    /** Status bar icons are 24dp; render at the device's native density. */
    val sizePx = (24 * density).roundToInt()

    /** Room either side for the shadow, so it isn't clipped at the bitmap edge. */
    private val padPx = if (shadow) density else 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        if (shadow) setShadowLayer(density, 0f, density / 2, 0x99000000.toInt())
    }
    private val digitBounds = Rect()

    fun render(downBytesPerSec: Long, upBytesPerSec: Long, unit: SpeedUnit): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val lineHeight = sizePx / 2f
        drawLine(canvas, SpeedFormat.compact(downBytesPerSec, unit), top = 0f, lineHeight)
        drawLine(canvas, SpeedFormat.compact(upBytesPerSec, unit), top = lineHeight, lineHeight)
        return bitmap
    }

    private fun drawLine(canvas: Canvas, text: String, top: Float, lineHeight: Float) {
        // Digits are ~0.7 of the text size, so this fills ~80% of the line height.
        paint.textSize = lineHeight * 1.15f
        paint.textScaleX = 1f
        val width = paint.measureText(text)
        val room = sizePx - 2 * padPx
        if (width > room) paint.textScaleX = room / width

        // Center on digit height (no descenders in our strings) rather than font metrics.
        paint.getTextBounds("0", 0, 1, digitBounds)
        val baseline = top + (lineHeight + digitBounds.height()) / 2f
        canvas.drawText(text, sizePx / 2f, baseline, paint)
    }
}

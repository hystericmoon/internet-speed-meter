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
 */
class SpeedIconRenderer(context: Context) {

    /** Status bar icons are 24dp; render at the device's native density. */
    private val sizePx = (24 * context.resources.displayMetrics.density).roundToInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val digitBounds = Rect()

    val transparentIcon: Bitmap by lazy {
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }

    fun render(downBytesPerSec: Long, upBytesPerSec: Long): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val lineHeight = sizePx / 2f
        drawLine(canvas, SpeedFormat.compact(downBytesPerSec), top = 0f, lineHeight)
        drawLine(canvas, SpeedFormat.compact(upBytesPerSec), top = lineHeight, lineHeight)
        return bitmap
    }

    private fun drawLine(canvas: Canvas, text: String, top: Float, lineHeight: Float) {
        // Digits are ~0.7 of the text size, so this fills ~80% of the line height.
        paint.textSize = lineHeight * 1.15f
        paint.textScaleX = 1f
        val width = paint.measureText(text)
        if (width > sizePx) paint.textScaleX = sizePx / width

        // Center on digit height (no descenders in our strings) rather than font metrics.
        paint.getTextBounds("0", 0, 1, digitBounds)
        val baseline = top + (lineHeight + digitBounds.height()) / 2f
        canvas.drawText(text, sizePx / 2f, baseline, paint)
    }
}

package com.akashark.agentbuddy.android.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

private const val PATTERN_SIZE = 1080

/** Draws and caches the theme pattern bitmaps behind [WallpaperManager.generatePatternBitmap]. */
internal object WallpaperPatternRenderer {
    private val bitmapCache = LinkedHashMap<String, Bitmap>(8, 0.75f, true)

    fun generatePatternBitmap(
        background: Color,
        accent: Color,
        patternType: PatternType,
    ): Bitmap {
        val cacheKey = "${background.toArgb()}_${accent.toArgb()}_${patternType.name}"
        bitmapCache[cacheKey]?.let { return it }

        val bitmap = Bitmap.createBitmap(PATTERN_SIZE, PATTERN_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint().apply { color = background.toArgb() }
        canvas.drawRect(0f, 0f, PATTERN_SIZE.toFloat(), PATTERN_SIZE.toFloat(), bgPaint)

        val patternPaint = Paint().apply {
            color = accent.toArgb()
            alpha = 25 // ~10% opacity
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val fillPaint = Paint().apply {
            color = accent.toArgb()
            alpha = 20
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val size = PATTERN_SIZE.toFloat()
        when (patternType) {
            PatternType.DOT_GRID -> {
                val spacing = 24f
                var x = spacing
                while (x < size) {
                    var y = spacing
                    while (y < size) {
                        canvas.drawCircle(x, y, 1.5f, fillPaint)
                        y += spacing
                    }
                    x += spacing
                }
            }
            PatternType.DIAGONAL_LINES -> {
                val spacing = 20f
                var offset = -size
                while (offset < size * 2) {
                    canvas.drawLine(offset, 0f, offset + size, size, patternPaint)
                    offset += spacing
                }
            }
            PatternType.CONCENTRIC_CIRCLES -> {
                val cx = size / 2
                val cy = size / 2
                var r = 30f
                while (r < size) {
                    canvas.drawCircle(cx, cy, r, patternPaint)
                    r += 40f
                }
            }
            PatternType.HEXAGONAL_MESH -> {
                val hexSize = 30f
                val w = hexSize * 1.732f
                val h = hexSize * 2f
                var row = 0
                var y = 0f
                while (y < size + h) {
                    var x = if (row % 2 == 0) 0f else w / 2f
                    while (x < size + w) {
                        drawHexagon(canvas, x, y, hexSize, patternPaint)
                        x += w
                    }
                    y += h * 0.75f
                    row++
                }
            }
            PatternType.CROSS_HATCH -> {
                val spacing = 20f
                var pos = 0f
                while (pos < size) {
                    canvas.drawLine(pos, 0f, pos, size, patternPaint)
                    canvas.drawLine(0f, pos, size, pos, patternPaint)
                    pos += spacing
                }
            }
            PatternType.WAVE_LINES -> {
                val amplitude = 15f
                val wavelength = 60f
                var y = 20f
                while (y < size) {
                    val path = android.graphics.Path()
                    path.moveTo(0f, y)
                    var x = 0f
                    while (x < size) {
                        val nextX = x + wavelength / 4f
                        val controlY = y + if (((x / (wavelength / 2f)).toInt() % 2) == 0) -amplitude else amplitude
                        path.quadTo(x + wavelength / 8f, controlY, nextX, y)
                        x = nextX
                    }
                    canvas.drawPath(path, patternPaint)
                    y += 30f
                }
            }
        }

        bitmapCache[cacheKey] = bitmap
        return bitmap
    }

    private fun drawHexagon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val path = android.graphics.Path()
        for (i in 0 until 6) {
            val angle = Math.toRadians((60.0 * i) - 30.0)
            val x = cx + size * cos(angle).toFloat()
            val y = cy + size * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }
}

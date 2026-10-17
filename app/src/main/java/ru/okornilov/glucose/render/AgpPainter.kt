package ru.okornilov.glucose.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import ru.okornilov.glucose.data.Band

/**
 * «Средний день» (AGP) на android.graphics.Canvas — для экрана отчёта и PDF.
 * Светлая полоса — 10–90% значений, тёмная — 25–75%, линия — медиана.
 */
object AgpPainter {
    private const val DAY_MINUTES = 1440

    fun draw(
        canvas: Canvas,
        area: RectF,
        bands: List<Band>,
        low: Double,
        high: Double,
        scale: Float,
        textColor: Int,
        gridColor: Int,
    ) {
        val labelW = 26 * scale
        val labelH = 18 * scale
        val left = area.left + labelW
        val right = area.right
        val top = area.top
        val bottom = area.bottom - labelH
        val yMin = 2.0
        val yMax = maxOf(16.0, (bands.maxOfOrNull { it.p90 } ?: 0.0) + 1)
        fun x(minute: Int) = left + minute.toFloat() / DAY_MINUTES * (right - left)
        fun y(v: Double) = top + ((yMax - v) / (yMax - yMin) * (bottom - top)).toFloat()

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; textSize = 10 * scale }
        val grid = Paint().apply { color = gridColor; strokeWidth = 0.6f * scale }
        var mark = 4
        while (mark <= yMax) {
            canvas.drawLine(left, y(mark.toDouble()), right, y(mark.toDouble()), grid)
            canvas.drawText("$mark", area.left, y(mark.toDouble()) + 4 * scale, text)
            mark += 4
        }
        for (h in 0..24 step 3) {
            val xx = x(h * 60)
            canvas.drawLine(xx, top, xx, bottom, grid)
            if (h < 24) canvas.drawText("%02d".format(h), xx + 2 * scale, bottom + 13 * scale, text)
        }

        // Целевой диапазон
        canvas.drawRect(left, y(high), right, y(low), Paint().apply { color = ChartPainter.IN_RANGE_COLOR; alpha = 26 })
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = scale
            pathEffect = DashPathEffect(floatArrayOf(4 * scale, 3 * scale), 0f)
        }
        dash.color = ChartPainter.LOW_COLOR
        canvas.drawLine(left, y(low), right, y(low), dash)
        dash.color = ChartPainter.HIGH_COLOR
        canvas.drawLine(left, y(high), right, y(high), dash)

        if (bands.size < 2) {
            text.textAlign = Paint.Align.CENTER
            text.textSize = 13 * scale
            canvas.drawText("Мало данных для среднего дня", (left + right) / 2, (top + bottom) / 2, text)
            return
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFF5C9DFF.toInt() }
        fun band(lowOf: (Band) -> Double, highOf: (Band) -> Double): Path = Path().apply {
            bands.forEachIndexed { i, b -> if (i == 0) moveTo(x(b.minuteOfDay), y(highOf(b))) else lineTo(x(b.minuteOfDay), y(highOf(b))) }
            bands.asReversed().forEach { lineTo(x(it.minuteOfDay), y(lowOf(it))) }
            close()
        }
        fill.alpha = 50
        canvas.drawPath(band({ it.p10 }, { it.p90 }), fill)
        fill.alpha = 110
        canvas.drawPath(band({ it.p25 }, { it.p75 }), fill)

        val median = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * scale
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = 0xFF1E63D6.toInt()
        }
        canvas.drawPath(
            Path().apply {
                bands.forEachIndexed { i, b -> if (i == 0) moveTo(x(b.minuteOfDay), y(b.p50)) else lineTo(x(b.minuteOfDay), y(b.p50)) }
            },
            median,
        )
    }
}

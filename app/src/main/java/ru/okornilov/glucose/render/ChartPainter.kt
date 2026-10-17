package ru.okornilov.glucose.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import ru.okornilov.glucose.data.Reading
import ru.okornilov.glucose.data.Zone
import ru.okornilov.glucose.data.zoneOf
import ru.okornilov.glucose.data.zonePieces
import java.time.Instant
import java.time.ZoneId

/** График на android.graphics.Canvas — для виджета и картинки «поделиться». Линия окрашена по зонам. */
object ChartPainter {
    const val LOW_COLOR = 0xFFEF5350.toInt()
    const val HIGH_COLOR = 0xFFFFA726.toInt()
    const val IN_RANGE_COLOR = 0xFF66BB6A.toInt()

    // Разрыв линии, если между точками больше этого интервала
    private const val GAP_MS = 20 * 60_000L

    fun zoneColor(zone: Zone): Int = when (zone) {
        Zone.LOW -> LOW_COLOR
        Zone.IN_RANGE -> IN_RANGE_COLOR
        Zone.HIGH -> HIGH_COLOR
    }

    fun levelColor(mmol: Double, low: Double, high: Double): Int = zoneColor(zoneOf(mmol, low, high))

    /**
     * scale — множитель толщин и размеров (density для виджета). labels — подписи осей и сетка;
     * в маленьком виджете их нет. outline — цвет обводки точки последнего замера (цвет фона).
     */
    fun draw(
        canvas: Canvas,
        area: RectF,
        readings: List<Reading>,
        start: Long,
        end: Long,
        low: Double,
        high: Double,
        scale: Float,
        labels: Boolean,
        outline: Int = 0xFF202124.toInt(),
    ) {
        val labelW = if (labels) 30 * scale else 0f
        val labelH = if (labels) 20 * scale else 0f
        val left = area.left + labelW
        val right = area.right
        val top = area.top
        val bottom = area.bottom - labelH
        val values = readings.map { it.mmol }
        val yMin = minOf(2.0, (values.minOrNull() ?: 2.0) - 0.5)
        val yMax = maxOf(14.0, (values.maxOrNull() ?: 14.0) + 0.5)
        fun x(t: Long) = left + (t - start).toFloat() / (end - start) * (right - left)
        fun y(v: Double) = top + ((yMax - v) / (yMax - yMin) * (bottom - top)).toFloat()

        canvas.drawRect(left, y(high), right, y(low), Paint().apply { color = IN_RANGE_COLOR; alpha = 26 })

        if (labels) {
            val grid = Paint().apply { color = 0x22FFFFFF; strokeWidth = scale * 0.6f }
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); textSize = 11 * scale }
            var mark = 2
            while (mark <= yMax) {
                val yy = y(mark.toDouble())
                canvas.drawLine(left, yy, right, yy, grid)
                canvas.drawText("$mark", area.left, yy + 4 * scale, text)
                mark += 2
            }
            val hours = ((end - start) / 3_600_000L).toInt().coerceAtLeast(1)
            val step = if (hours > 12) 3 else 1
            for (h in 0..hours step step) {
                val xx = left + h.toFloat() / hours * (right - left)
                canvas.drawLine(xx, top, xx, bottom, grid)
                if (h < hours) {
                    val hour = Instant.ofEpochMilli(start + h * 3_600_000L).atZone(ZoneId.systemDefault()).hour
                    canvas.drawText("%02d".format(hour), xx + 3 * scale, bottom + 15 * scale, text)
                }
            }
        }

        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = scale
            pathEffect = DashPathEffect(floatArrayOf(4 * scale, 3 * scale), 0f)
        }
        dash.color = LOW_COLOR
        canvas.drawLine(left, y(low), right, y(low), dash)
        dash.color = HIGH_COLOR
        canvas.drawLine(left, y(high), right, y(high), dash)

        // Подряд идущие участки одной зоны — одним путём, без стыков в заливке
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        var run = mutableListOf<PointF>()
        var runZone: Zone? = null
        fun flush() {
            val zone = runZone
            if (zone != null && run.size >= 2) {
                // Заливка только между линией и порогом
                val threshold = when (zone) {
                    Zone.HIGH -> y(high)
                    Zone.LOW -> y(low)
                    Zone.IN_RANGE -> null
                }
                if (threshold != null) {
                    val area = Path().apply {
                        moveTo(run.first().x, threshold)
                        run.forEach { lineTo(it.x, it.y) }
                        lineTo(run.last().x, threshold)
                        close()
                    }
                    fill.color = zoneColor(zone)
                    fill.alpha = 56
                    canvas.drawPath(area, fill)
                }
                val path = Path().apply {
                    moveTo(run.first().x, run.first().y)
                    run.drop(1).forEach { lineTo(it.x, it.y) }
                }
                line.color = zoneColor(zone)
                canvas.drawPath(path, line)
            }
            run = mutableListOf()
            runZone = null
        }
        readings.forEachIndexed { i, r ->
            val next = readings.getOrNull(i + 1)
            val prev = readings.getOrNull(i - 1)
            if (next == null || next.ts - r.ts > GAP_MS) {
                flush()
                // Одиночный замер без соседей — точкой, иначе его не было бы видно
                if (prev == null || r.ts - prev.ts > GAP_MS) {
                    dot.color = levelColor(r.mmol, low, high)
                    canvas.drawCircle(x(r.ts), y(r.mmol), 2f * scale, dot)
                }
                return@forEachIndexed
            }
            for (piece in zonePieces(r.mmol, next.mmol, low, high)) {
                val p0 = PointF(x(r.ts) + (x(next.ts) - x(r.ts)) * piece.from, y(r.mmol + (next.mmol - r.mmol) * piece.from))
                val p1 = PointF(x(r.ts) + (x(next.ts) - x(r.ts)) * piece.to, y(r.mmol + (next.mmol - r.mmol) * piece.to))
                if (piece.zone != runZone) {
                    flush()
                    runZone = piece.zone
                    run.add(p0)
                }
                run.add(p1)
            }
        }
        flush()

        // Последний замер — крупная точка с обводкой цветом фона
        readings.lastOrNull()?.let { r ->
            dot.color = outline
            canvas.drawCircle(x(r.ts), y(r.mmol), 5f * scale, dot)
            dot.color = levelColor(r.mmol, low, high)
            canvas.drawCircle(x(r.ts), y(r.mmol), 3.5f * scale, dot)
        }
    }
}

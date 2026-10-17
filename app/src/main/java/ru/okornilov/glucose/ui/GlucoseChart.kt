package ru.okornilov.glucose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.okornilov.glucose.data.Reading
import ru.okornilov.glucose.data.Zone
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.hhmm
import ru.okornilov.glucose.data.zonePieces
import kotlin.math.abs

// Разрыв линии, если между точками больше этого интервала
private const val GAP_MS = 20 * 60_000L

// Касание выбирает ближайший замер, но не дальше этого по времени
private const val TOUCH_RANGE_MS = 30 * 60_000L

private val LEFT = 24.dp
private val BOTTOM = 16.dp

fun zoneColor(zone: Zone): Color = when (zone) {
    Zone.LOW -> LowColor
    Zone.IN_RANGE -> InRangeColor
    Zone.HIGH -> HighColor
}

/** График за день: линия, окрашенная по зонам, точка последнего замера и подсказка по касанию. */
@Composable
fun GlucoseChart(
    readings: List<Reading>,
    start: Long,
    end: Long,
    low: Double,
    high: Double,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val labelStyle = TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant)
    val tipStyle = TextStyle(fontSize = 12.sp, color = colors.inverseOnSurface, fontWeight = FontWeight.SemiBold)
    val leftPx = with(LocalDensity.current) { LEFT.toPx() }
    var selected by remember(start, end) { mutableStateOf<Reading?>(null) }

    fun nearest(x: Float, width: Int): Reading? {
        if (readings.isEmpty()) return null
        val t = start + ((x - leftPx) / (width - leftPx) * (end - start)).toLong()
        return readings.minBy { abs(it.ts - t) }.takeIf { abs(it.ts - t) <= TOUCH_RANGE_MS }
    }

    Canvas(
        modifier
            .pointerInput(readings, start, end) {
                detectTapGestures { pos ->
                    val r = nearest(pos.x, size.width)
                    selected = if (r == selected) null else r
                }
            }
            .pointerInput(readings, start, end) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    nearest(change.position.x, size.width)?.let { selected = it }
                }
            },
    ) {
        val values = readings.map { it.mmol }
        val yMin = minOf(2.0, (values.minOrNull() ?: 2.0) - 0.5)
        val yMax = maxOf(14.0, (values.maxOrNull() ?: 14.0) + 0.5)
        val left = leftPx
        val h = size.height - BOTTOM.toPx()
        val w = size.width - left
        fun x(t: Long) = left + (t - start).toFloat() / (end - start) * w
        fun y(v: Double) = ((yMax - v) / (yMax - yMin) * h).toFloat()

        // Сетка, подписи, полоса целевого диапазона и пороги
        drawRect(InRangeColor.copy(alpha = 0.08f), topLeft = Offset(left, y(high)), size = Size(w, y(low) - y(high)))
        var mark = 2
        while (mark <= yMax) {
            val yy = y(mark.toDouble())
            drawLine(colors.outlineVariant.copy(alpha = 0.5f), Offset(left, yy), Offset(size.width, yy), strokeWidth = 1f)
            drawText(measurer, "$mark", topLeft = Offset(0f, yy - 7.sp.toPx()), style = labelStyle)
            mark += 2
        }
        for (hour in 0..24 step 3) {
            val xx = left + hour / 24f * w
            drawLine(colors.outlineVariant.copy(alpha = 0.5f), Offset(xx, 0f), Offset(xx, h), strokeWidth = 1f)
            if (hour < 24) drawText(measurer, "%02d".format(hour), topLeft = Offset(xx + 2f, h + 2f), style = labelStyle)
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        drawLine(LowColor.copy(alpha = 0.7f), Offset(left, y(low)), Offset(size.width, y(low)), strokeWidth = 1.5f, pathEffect = dash)
        drawLine(HighColor.copy(alpha = 0.7f), Offset(left, y(high)), Offset(size.width, y(high)), strokeWidth = 1.5f, pathEffect = dash)

        // Линия по зонам. Подряд идущие участки одной зоны собираем в один путь — без стыков в заливке
        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        var run = mutableListOf<Offset>()
        var runZone: Zone? = null
        fun flush() {
            val zone = runZone
            if (zone != null && run.size >= 2) {
                val color = zoneColor(zone)
                // Заливка только между линией и порогом — видно, насколько и как долго сахар был за порогом
                val threshold = when (zone) {
                    Zone.HIGH -> y(high)
                    Zone.LOW -> y(low)
                    Zone.IN_RANGE -> null
                }
                if (threshold != null) {
                    val fill = Path().apply {
                        moveTo(run.first().x, threshold)
                        run.forEach { lineTo(it.x, it.y) }
                        lineTo(run.last().x, threshold)
                        close()
                    }
                    drawPath(fill, color.copy(alpha = 0.22f))
                }
                val line = Path().apply {
                    moveTo(run.first().x, run.first().y)
                    run.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(line, color, style = stroke)
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
                if (prev == null || r.ts - prev.ts > GAP_MS) drawCircle(levelColor(r.mmol, low, high), 2.dp.toPx(), Offset(x(r.ts), y(r.mmol)))
                return@forEachIndexed
            }
            for (piece in zonePieces(r.mmol, next.mmol, low, high)) {
                val p0 = Offset(x(r.ts) + (x(next.ts) - x(r.ts)) * piece.from, y(r.mmol + (next.mmol - r.mmol) * piece.from))
                val p1 = Offset(x(r.ts) + (x(next.ts) - x(r.ts)) * piece.to, y(r.mmol + (next.mmol - r.mmol) * piece.to))
                if (piece.zone != runZone) {
                    flush()
                    runZone = piece.zone
                    run.add(p0)
                }
                run.add(p1)
            }
        }
        flush()

        // Последний замер — крупная точка с обводкой
        readings.lastOrNull()?.let { r ->
            val c = Offset(x(r.ts), y(r.mmol))
            drawCircle(colors.surface, 6.dp.toPx(), c)
            drawCircle(levelColor(r.mmol, low, high), 4.5.dp.toPx(), c)
        }

        // Подсказка по касанию
        selected?.let { r ->
            val c = Offset(x(r.ts), y(r.mmol))
            drawLine(
                colors.onSurfaceVariant, Offset(c.x, 0f), Offset(c.x, h),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            drawCircle(colors.surface, 6.dp.toPx(), c)
            drawCircle(levelColor(r.mmol, low, high), 4.5.dp.toPx(), c)

            val text = measurer.measure("${r.mmol.fmt()} ммоль/л ${r.arrow}\n${hhmm(r.ts)}", tipStyle)
            val pad = Offset(8.dp.toPx(), 6.dp.toPx())
            val box = Size(text.size.width + pad.x * 2, text.size.height + pad.y * 2)
            val gap = 10.dp.toPx()
            val bx = (c.x - box.width / 2).coerceIn(left, size.width - box.width)
            val by = (c.y - box.height - gap).let { if (it < 0f) c.y + gap else it }
            drawRoundRect(colors.inverseSurface, Offset(bx, by), box, CornerRadius(8.dp.toPx()))
            drawText(text, topLeft = Offset(bx + pad.x, by + pad.y))
        }
    }
}

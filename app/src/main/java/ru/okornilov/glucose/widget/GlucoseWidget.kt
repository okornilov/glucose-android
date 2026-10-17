package ru.okornilov.glucose.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import ru.okornilov.glucose.R
import ru.okornilov.glucose.app
import ru.okornilov.glucose.data.Reading
import ru.okornilov.glucose.data.Zone
import ru.okornilov.glucose.data.zonePieces
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.hhmm
import ru.okornilov.glucose.profile.ProfileReceiver
import ru.okornilov.glucose.ui.MainActivity

/** Виджет рабочего стола: текущее значение, время замера и график за последние 3 часа. */
class GlucoseWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        update(context, manager, appWidgetId) // изменили размер — перерисовать график под новые пропорции
    }

    companion object {
        private const val WINDOW_MS = 3 * 3600_000L
        private const val FRESH_MINUTES = 5
        private const val GAP_MS = 20 * 60_000L

        // Ширина колонки со значением и отступы, dp — чтобы оценить место под график
        private const val VALUE_COLUMN_DP = 104
        private const val PADDING_DP = 34

        private const val LOW_COLOR = 0xFFEF5350.toInt()
        private const val HIGH_COLOR = 0xFFFFA726.toInt()
        private const val IN_RANGE_COLOR = 0xFF66BB6A.toInt()

        /** Вызывается после каждого опроса LibreView. */
        fun updateAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            manager.getAppWidgetIds(ComponentName(ctx, GlucoseWidget::class.java)).forEach { update(ctx, manager, it) }
        }

        private fun update(ctx: Context, manager: AppWidgetManager, id: Int) {
            val app = ctx.app
            val prefs = app.prefs
            val now = System.currentTimeMillis()
            val last = app.repo.last.value
            val views = RemoteViews(ctx.packageName, R.layout.widget_glucose)
            views.setInt(R.id.widget_bg, "setImageAlpha", prefs.widgetOpacity * 255 / 100)

            if (last == null) {
                views.setTextViewText(R.id.widget_value, "—")
                views.setTextColor(R.id.widget_value, 0xFFFFFFFF.toInt())
                views.setTextViewText(R.id.widget_time, "нет данных")
            } else {
                val ageMinutes = (now - last.ts) / 60_000
                // Устаревшее значение (запрос не удался или замер старше 5 мин) — приглушённым цветом
                val stale = app.repo.error.value != null || ageMinutes >= FRESH_MINUTES
                val level = color(last.mmol, prefs.low, prefs.high)
                views.setTextViewText(R.id.widget_value, "${last.mmol.fmt()} ${last.arrow}")
                views.setTextColor(R.id.widget_value, if (stale) (level and 0x00FFFFFF) or 0x73000000 else level)
                views.setTextViewText(
                    R.id.widget_time,
                    if (stale) "⚠ замер $ageMinutes мин назад" else "замер в ${hhmm(last.ts)}",
                )
                views.setTextColor(R.id.widget_time, if (stale) LOW_COLOR else 0xE6FFFFFF.toInt())
            }

            val options = manager.getAppWidgetOptions(id)
            // В портретной ориентации ширина — MIN_WIDTH, высота — MAX_HEIGHT
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 60
            val chartWidthDp = widthDp - VALUE_COLUMN_DP - PADDING_DP
            if (chartWidthDp < 50) {
                views.setViewVisibility(R.id.widget_chart, View.GONE)
            } else {
                val density = ctx.resources.displayMetrics.density
                val w = (chartWidthDp * density).toInt().coerceIn(1, 900)
                val h = ((heightDp - 24) * density).toInt().coerceIn(1, 450)
                val readings = app.db.between(now - WINDOW_MS, now + 60_000)
                views.setViewVisibility(R.id.widget_chart, View.VISIBLE)
                views.setImageViewBitmap(R.id.widget_chart, chart(readings, now - WINDOW_MS, now, prefs.low, prefs.high, w, h, density))
            }

            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            // Профиль показываем, только если их несколько; нажатие — следующий профиль
            if (prefs.profiles().size > 1) {
                views.setViewVisibility(R.id.widget_profile, View.VISIBLE)
                views.setTextViewText(R.id.widget_profile, "⇄ ${prefs.active.name}")
                views.setOnClickPendingIntent(R.id.widget_profile, ProfileReceiver.nextIntent(ctx))
            } else {
                views.setViewVisibility(R.id.widget_profile, View.GONE)
            }
            manager.updateAppWidget(id, views)
        }

        private fun color(mmol: Double, low: Double, high: Double) = when {
            mmol <= low -> LOW_COLOR
            mmol > high -> HIGH_COLOR
            else -> IN_RANGE_COLOR
        }

        private fun chart(
            readings: List<Reading>, start: Long, end: Long, low: Double, high: Double,
            w: Int, h: Int, density: Float,
        ): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val values = readings.map { it.mmol }
            val yMin = minOf(2.0, (values.minOrNull() ?: 2.0) - 0.5)
            val yMax = maxOf(14.0, (values.maxOrNull() ?: 14.0) + 0.5)
            fun x(t: Long) = (t - start).toFloat() / (end - start) * w
            fun y(v: Double) = ((yMax - v) / (yMax - yMin) * h).toFloat()

            val band = Paint().apply { color = IN_RANGE_COLOR; alpha = 40 }
            canvas.drawRect(0f, y(high), w.toFloat(), y(low), band)
            val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = density
                pathEffect = DashPathEffect(floatArrayOf(4 * density, 3 * density), 0f)
            }
            dash.color = LOW_COLOR
            canvas.drawLine(0f, y(low), w.toFloat(), y(low), dash)
            dash.color = HIGH_COLOR
            canvas.drawLine(0f, y(high), w.toFloat(), y(high), dash)

            // Линия по зонам: цвет меняется ровно на линии порога
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f * density
                strokeCap = Paint.Cap.ROUND
            }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG)
            readings.forEachIndexed { i, r ->
                val next = readings.getOrNull(i + 1)
                val prev = readings.getOrNull(i - 1)
                if (next == null || next.ts - r.ts > GAP_MS) {
                    // Одиночный замер без соседей — точкой, иначе его не было бы видно
                    if (prev == null || r.ts - prev.ts > GAP_MS) {
                        dot.color = color(r.mmol, low, high)
                        canvas.drawCircle(x(r.ts), y(r.mmol), 2f * density, dot)
                    }
                    return@forEachIndexed
                }
                for (piece in zonePieces(r.mmol, next.mmol, low, high)) {
                    line.color = when (piece.zone) {
                        Zone.LOW -> LOW_COLOR
                        Zone.IN_RANGE -> IN_RANGE_COLOR
                        Zone.HIGH -> HIGH_COLOR
                    }
                    canvas.drawLine(
                        x(r.ts) + (x(next.ts) - x(r.ts)) * piece.from, y(r.mmol + (next.mmol - r.mmol) * piece.from),
                        x(r.ts) + (x(next.ts) - x(r.ts)) * piece.to, y(r.mmol + (next.mmol - r.mmol) * piece.to),
                        line,
                    )
                }
            }
            // Последний замер — крупная точка с тёмной обводкой
            readings.lastOrNull()?.let { r ->
                dot.color = 0xFF202124.toInt()
                canvas.drawCircle(x(r.ts), y(r.mmol), 5f * density, dot)
                dot.color = color(r.mmol, low, high)
                canvas.drawCircle(x(r.ts), y(r.mmol), 3.5f * density, dot)
            }
            return bmp
        }
    }
}

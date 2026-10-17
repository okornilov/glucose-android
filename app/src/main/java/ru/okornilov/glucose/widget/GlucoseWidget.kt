package ru.okornilov.glucose.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import ru.okornilov.glucose.R
import ru.okornilov.glucose.app
import ru.okornilov.glucose.data.Freshness
import ru.okornilov.glucose.data.Reading
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.freshness
import ru.okornilov.glucose.profile.ProfileReceiver
import ru.okornilov.glucose.render.ChartPainter
import ru.okornilov.glucose.ui.MainActivity

/** Виджет рабочего стола: текущее значение, давность замера и график за последние 3 часа. */
class GlucoseWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        update(context, manager, appWidgetId) // изменили размер — перерисовать график под новые пропорции
    }

    companion object {
        private const val WINDOW_MS = 3 * 3600_000L

        // Ширина колонки со значением и отступы, dp — чтобы оценить место под график
        private const val VALUE_COLUMN_DP = 104
        private const val PADDING_DP = 34


        /** Вызывается после каждого опроса LibreView: обновляет виджеты рабочего стола и экрана блокировки. */
        fun updateAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            manager.getAppWidgetIds(ComponentName(ctx, GlucoseWidget::class.java)).forEach { update(ctx, manager, it) }
            LockWidget.updateAll(ctx, manager)
        }

        /** Цвет значения: по зоне, устаревшее — приглушённое. */
        internal fun valueColor(last: Reading, freshness: Freshness, low: Double, high: Double): Int {
            val level = ChartPainter.levelColor(last.mmol, low, high)
            return if (freshness == Freshness.FRESH) level else (level and 0x00FFFFFF) or 0x73000000
        }

        /** Строка под значением: сколько минут назад замер и, если он устарел, — почему. */
        internal fun ageText(last: Reading, freshness: Freshness, now: Long, short: Boolean = false): String {
            val minutes = (now - last.ts) / 60_000
            val age = if (minutes < 1) "сейчас" else "$minutes мин"
            return when (freshness) {
                Freshness.FRESH -> if (short) age else if (minutes < 1) "замер только что" else "замер $minutes мин назад"
                Freshness.NO_SERVER -> if (short) "⚠ $age" else "⚠ нет связи · $age назад"
                Freshness.NO_NEW_DATA -> if (short) "⚠ $age" else "⚠ нет данных · $age назад"
            }
        }

        internal fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

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
                views.setTextViewText(R.id.widget_arrow, "")
                views.setTextViewText(R.id.widget_time, if (app.repo.error.value != null) "нет связи" else "нет данных")
            } else {
                val freshness = freshness(last, app.repo.error.value, now)
                val color = valueColor(last, freshness, prefs.low, prefs.high)
                views.setTextViewText(R.id.widget_value, last.mmol.fmt())
                views.setTextColor(R.id.widget_value, color)
                views.setTextViewText(R.id.widget_arrow, last.arrow)
                views.setTextColor(R.id.widget_arrow, color)
                views.setTextViewText(R.id.widget_time, ageText(last, freshness, now))
                views.setTextColor(R.id.widget_time, if (freshness == Freshness.FRESH) 0xE6FFFFFF.toInt() else ChartPainter.LOW_COLOR)
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

            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(ctx))

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

        private fun chart(
            readings: List<Reading>, start: Long, end: Long, low: Double, high: Double,
            w: Int, h: Int, density: Float,
        ): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            ChartPainter.draw(Canvas(bmp), RectF(0f, 0f, w.toFloat(), h.toFloat()), readings, start, end, low, high, density, labels = false)
            return bmp
        }
    }
}

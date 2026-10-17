package ru.okornilov.glucose.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import ru.okornilov.glucose.R
import ru.okornilov.glucose.app
import ru.okornilov.glucose.data.Freshness
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.freshness
import ru.okornilov.glucose.render.ChartPainter

/** Маленький виджет экрана блокировки (One UI): значение, стрелка и сколько минут назад замер. */
class LockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
    }

    companion object {
        fun updateAll(ctx: Context, manager: AppWidgetManager = AppWidgetManager.getInstance(ctx)) {
            manager.getAppWidgetIds(ComponentName(ctx, LockWidget::class.java)).forEach { update(ctx, manager, it) }
        }

        private fun update(ctx: Context, manager: AppWidgetManager, id: Int) {
            val app = ctx.app
            val prefs = app.prefs
            val now = System.currentTimeMillis()
            val last = app.repo.last.value
            val error = app.repo.error.value
            val views = RemoteViews(ctx.packageName, R.layout.widget_lock)
            if (last == null) {
                views.setTextViewText(R.id.lock_value, "—")
                views.setTextViewText(R.id.lock_arrow, "")
                views.setTextViewText(R.id.lock_time, if (error != null) "нет связи" else "нет данных")
            } else {
                val freshness = freshness(last, error, now)
                val color = GlucoseWidget.valueColor(last, freshness, prefs.low, prefs.high)
                views.setTextViewText(R.id.lock_value, last.mmol.fmt())
                views.setTextColor(R.id.lock_value, color)
                views.setTextViewText(R.id.lock_arrow, last.arrow)
                views.setTextColor(R.id.lock_arrow, color)
                views.setTextViewText(R.id.lock_time, GlucoseWidget.ageText(last, freshness, now, short = true))
                views.setTextColor(R.id.lock_time, if (freshness == Freshness.FRESH) 0xE6FFFFFF.toInt() else ChartPainter.LOW_COLOR)
            }
            views.setOnClickPendingIntent(R.id.lock_root, GlucoseWidget.openAppIntent(ctx))
            manager.updateAppWidget(id, views)
        }
    }
}

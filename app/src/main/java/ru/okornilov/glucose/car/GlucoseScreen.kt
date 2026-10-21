package ru.okornilov.glucose.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.okornilov.glucose.R
import ru.okornilov.glucose.alerts.AlarmPlayer
import ru.okornilov.glucose.alerts.AlertEngine
import ru.okornilov.glucose.alerts.Notifications
import ru.okornilov.glucose.app
import ru.okornilov.glucose.data.Freshness
import ru.okornilov.glucose.data.Zone
import ru.okornilov.glucose.data.ago
import ru.okornilov.glucose.data.dayStats
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.forecast
import ru.okornilov.glucose.data.freshness
import ru.okornilov.glucose.data.hhmm
import ru.okornilov.glucose.data.percent
import ru.okornilov.glucose.data.zoneOf
import ru.okornilov.glucose.render.ChartPainter
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Экран Android Auto: значение и тренд, прогноз, итоги дня и график за 3 часа.
 * Шаблон PaneTemplate — свои экраны в машине рисовать нельзя, только строки, картинка и до двух кнопок.
 * Перерисовывается после каждого опроса LibreView.
 */
class GlucoseScreen(carContext: CarContext) : Screen(carContext) {
    private val app = carContext.app
    private var refreshing: Job? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                lifecycleScope.launch {
                    combine(app.repo.last, app.repo.error, app.repo.dataVersion) { _, _, _ -> }.collect { invalidate() }
                }
                // Если мониторинг выключен, хотя бы раз обновить при открытии
                refresh()
            }
        })
    }

    private fun refresh() {
        if (refreshing?.isActive == true) return
        refreshing = lifecycleScope.launch { app.repo.refresh() }
    }

    // Исключение здесь уронило бы весь процесс приложения вместе с фоновым мониторингом — ловим и показываем сообщение
    override fun onGetTemplate(): Template = try {
        buildTemplate()
    } catch (e: Exception) {
        Log.e("GlucoseScreen", "Не удалось построить экран Android Auto", e)
        MessageTemplate.Builder("Не удалось показать данные: ${e.message ?: e.javaClass.simpleName}")
            .setTitle("Сахар")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun buildTemplate(): Template {
        val prefs = app.prefs
        val now = System.currentTimeMillis()
        val last = app.repo.last.value
        val error = app.repo.error.value
        val pane = Pane.Builder()

        if (last == null) {
            pane.addRow(
                Row.Builder()
                    .setTitle("Нет данных")
                    .addText(error ?: "Ожидание данных LibreView")
                    .build()
            )
        } else {
            val fresh = freshness(last, error, now)
            val zone = zoneOf(last.mmol, prefs.low, prefs.high)
            val status = when (zone) {
                Zone.LOW -> "низкий"
                Zone.IN_RANGE -> "в норме"
                Zone.HIGH -> "высокий"
            }
            // Цветной текст в строках Android Auto запрещён — зона показана цветным кружком слева
            val dot = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_dot))
                .setTint(if (fresh == Freshness.FRESH) carColor(zone) else CarColor.DEFAULT)
                .build()
            val sub = when (fresh) {
                Freshness.FRESH -> "Замер ${ago(last.ts, now)} · $status"
                Freshness.NO_SERVER -> "⚠ Нет связи с сервером · замер ${ago(last.ts, now)}"
                Freshness.NO_NEW_DATA -> "⚠ Нет новых данных · замер ${ago(last.ts, now)}"
            }
            pane.addRow(
                Row.Builder()
                    .setTitle("${last.mmol.fmt()} ммоль/л ${last.arrow}")
                    .addText(sub)
                    .setImage(dot, Row.IMAGE_TYPE_SMALL)
                    .build()
            )

            val recent = app.db.between(now - 20 * 60_000L, now + 60_000L)
            forecast(recent, now, prefs.predictMinutes)?.let { f ->
                val delta = f.slope * 15
                val sign = if (delta > 0) "+" else if (delta < 0) "−" else ""
                pane.addRow(
                    Row.Builder()
                        .setTitle("Тренд $sign${abs(delta).fmt()} за 15 мин")
                        .addText("Прогноз ≈ ${f.predicted.coerceAtLeast(1.0).fmt()} в ${hhmm(now + prefs.predictMinutes * 60_000L)}")
                        .build()
                )
            }
        }

        val zoneId = ZoneId.systemDefault()
        val dayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
        dayStats(app.db.between(dayStart, now + 60_000L), prefs.low, prefs.high)?.let { s ->
            pane.addRow(
                Row.Builder()
                    .setTitle("Сегодня: в диапазоне ${percent(s.inRange)}")
                    .addText("Мин ${s.min.fmt()} · среднее ${s.avg.fmt()} · макс ${s.max.fmt()}")
                    .build()
            )
        }

        // График за 3 часа — картинкой (на хостах с Car API 4+, на старых просто без неё)
        if (carContext.carAppApiLevel >= 4) {
            val window = 3 * 3600_000L
            // Android Auto обрезает картинку панели по бокам до квадрата —
            // рисуем сразу квадратную и крупным шрифтом: её сильно уменьшают
            val bmp = Bitmap.createBitmap(560, 560, Bitmap.Config.ARGB_8888)
            ChartPainter.draw(
                Canvas(bmp), RectF(6f, 10f, 550f, 552f),
                app.db.between(now - window, now + 60_000L), now - window, now, prefs.low, prefs.high,
                scale = 3.2f, labels = true,
            )
            pane.setImage(CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build())
        }

        pane.addAction(
            Action.Builder()
                .setTitle("Обновить")
                .setOnClickListener { refresh() }
                .build()
        )
        if (app.alerts.isActive(AlertEngine.KIND_LEVEL)) {
            pane.addAction(
                Action.Builder()
                    .setTitle("Отложить 30 мин")
                    .setBackgroundColor(CarColor.RED)
                    .setOnClickListener {
                        app.alerts.snooze(AlertEngine.KIND_LEVEL, 30)
                        AlarmPlayer.stop()
                        Notifications.cancel(carContext, AlertEngine.KIND_LEVEL)
                        invalidate()
                    }
                    .build()
            )
        }

        return PaneTemplate.Builder(pane.build())
            .setTitle("Сахар · ${prefs.active.name}")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun carColor(zone: Zone): CarColor = when (zone) {
        Zone.LOW -> CarColor.RED
        Zone.IN_RANGE -> CarColor.GREEN
        Zone.HIGH -> CarColor.YELLOW
    }
}

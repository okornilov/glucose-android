package ru.okornilov.glucose.alerts

import android.content.Context
import ru.okornilov.glucose.app
import ru.okornilov.glucose.data.boolPref
import ru.okornilov.glucose.data.coveredMinutes
import ru.okornilov.glucose.data.dayStats
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.hhmm
import ru.okornilov.glucose.data.intPref
import ru.okornilov.glucose.data.longPref
import ru.okornilov.glucose.data.lowEpisodes
import ru.okornilov.glucose.data.percent
import ru.okornilov.glucose.data.stringPref
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Неспешные напоминания, которые проверяются после каждого опроса: замена сенсора и утренняя сводка за ночь.
 * Отдельного будильника не нужно — при мониторинге опрос идёт раз в минуту.
 */
class Reminders(private val context: Context) {
    private val st = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    // Для какого сенсора и какое напоминание уже отправлено: 1 — за сутки, 2 — за 2 часа
    private var sensorNotifiedStart by st.longPref("sensor_start", 0L)
    private var sensorStage by st.intPref("sensor_stage", 0)
    private var summaryDate by st.stringPref("summary_date", "")
    private var summaryInit by st.boolPref("summary_init", false)

    @Synchronized
    fun check(now: Long = System.currentTimeMillis()) {
        checkSensor(now)
        checkSummary(now)
    }

    private fun checkSensor(now: Long) {
        val prefs = context.app.prefs
        val start = prefs.sensorStart
        if (!prefs.notifySensor || start <= 0 || prefs.muted(now)) return
        if (start != sensorNotifiedStart) {
            sensorNotifiedStart = start
            sensorStage = 0
        }
        val end = start + prefs.sensorDays * DAY_MS
        val left = end - now
        val stage = when {
            left <= 0 -> return // срок уже вышел — LibreLink сам об этом говорит
            left <= 2 * HOUR_MS -> 2
            left <= DAY_MS -> 1
            else -> 0
        }
        if (stage <= sensorStage) return
        sensorStage = stage
        val endText = "${dayText(end)} в ${hhmm(end)}"
        if (stage == 2) {
            Notifications.sensor(context, "Сенсор закончится через ${(left + HOUR_MS / 2) / HOUR_MS} ч", "Срок работы истекает $endText — приготовьте новый сенсор")
        } else {
            Notifications.sensor(context, "Завтра заменить сенсор", "Срок работы истекает $endText")
        }
    }

    /** Включили сводку или сменили время, а сегодняшнее время уже прошло — первая сводка завтра, а не сразу. */
    @Synchronized
    fun summaryChanged() {
        val zone = ZoneId.systemDefault()
        if (LocalTime.now(zone).toSecondOfDay() / 60 >= context.app.prefs.summaryMinute) {
            summaryDate = LocalDate.now(zone).toString()
        }
    }

    private fun checkSummary(now: Long) {
        val app = context.app
        val prefs = app.prefs
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // Первый запуск после установки — не присылаем сводку за ночь, которой не было в базе
        if (!summaryInit) {
            summaryInit = true
            if (LocalTime.now(zone).toSecondOfDay() / 60 >= prefs.summaryMinute) summaryDate = today.toString()
        }
        if (!prefs.summaryEnabled || summaryDate == today.toString()) return
        val time = LocalTime.of(prefs.summaryMinute / 60, prefs.summaryMinute % 60)
        val at = today.atTime(time).atZone(zone).toInstant().toEpochMilli()
        if (now < at) return
        summaryDate = today.toString()
        // Телефон был выключен всё утро — устаревшая сводка не нужна
        if (now > at + LATE_MS) return

        val from = today.minusDays(1).atTime(NIGHT_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        val readings = app.db.between(from, at)
        val stats = dayStats(readings, prefs.low, prefs.high) ?: return
        if (coveredMinutes(readings) < 60) return
        val minutes = coveredMinutes(readings)
        val belowMin = Math.round(stats.below * minutes)
        val aboveMin = Math.round(stats.above * minutes)
        val episodes = lowEpisodes(readings, prefs.low)
        val title = when {
            episodes > 0 -> "🌙 Ночь: низкий сахар $episodes раз, всего $belowMin мин"
            else -> "🌙 Ночь: в диапазоне ${percent(stats.inRange)}"
        }
        val text = buildString {
            append("Мин ${stats.min.fmt()} · среднее ${stats.avg.fmt()} · макс ${stats.max.fmt()}\n")
            append("В диапазоне ${percent(stats.inRange)}")
            if (belowMin > 0) append(" · ниже ${prefs.low.fmt()}: $belowMin мин")
            if (aboveMin > 0) append(" · выше ${prefs.high.fmt()}: $aboveMin мин")
            append("\nС $NIGHT_START_HOUR:00 до ${hhmm(at)}")
        }
        Notifications.summary(context, title, text)
    }

    private fun dayText(ts: Long): String {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (day) {
            today -> "сегодня"
            today.plusDays(1) -> "завтра"
            else -> DATE.format(day)
        }
    }

    companion object {
        private const val HOUR_MS = 3600_000L
        private const val DAY_MS = 24 * HOUR_MS
        private const val LATE_MS = 4 * HOUR_MS
        const val NIGHT_START_HOUR = 23
        private val DATE = DateTimeFormatter.ofPattern("d MMMM")
    }
}

package ru.okornilov.glucose.alerts

import android.content.Context
import ru.okornilov.glucose.data.Prefs
import ru.okornilov.glucose.data.Reading
import ru.okornilov.glucose.data.boolPref
import ru.okornilov.glucose.data.describe
import ru.okornilov.glucose.data.forecast
import ru.okornilov.glucose.data.longPref
import ru.okornilov.glucose.data.stringPref

/**
 * Правила уведомлений. Событие kind: level (низкий/высокий сахар), predict (скоро низкий) или stale (нет данных).
 * Состояние хранится в SharedPreferences, чтобы «отложить»/«до нормализации» переживали перезапуск процесса.
 */
class AlertEngine(private val context: Context, private val prefs: Prefs) {
    private val st = context.getSharedPreferences("alert_state", Context.MODE_PRIVATE)

    private var level by st.stringPref("level", OK) // ok | low | high
    private var levelAlertAt by st.longPref("level_alert_at", 0L)
    private var levelSilenced by st.boolPref("level_silenced", false) // отключено до нормализации
    private var levelSnoozeUntil by st.longPref("level_snooze_until", 0L)
    private var levelRepeat by st.longPref("level_repeat", 0L) // сколько раз напомнили без реакции
    private var predictActive by st.boolPref("predict_active", false) // идёт падение к низкому
    private var predictAlertAt by st.longPref("predict_alert_at", 0L)
    private var predictSilenced by st.boolPref("predict_silenced", false) // «понятно» до конца падения
    private var staleAlertAt by st.longPref("stale_alert_at", 0L)
    private var staleSilenced by st.boolPref("stale_silenced", false) // отключено до появления данных
    private var staleSnoozeUntil by st.longPref("stale_snooze_until", 0L)

    private fun isActive(kind: String) = when (kind) {
        KIND_LEVEL -> level != OK
        KIND_PREDICT -> predictActive
        else -> staleAlertAt != 0L
    }

    @Synchronized
    fun snooze(kind: String, minutes: Int): Boolean {
        if (!isActive(kind)) return false
        val until = System.currentTimeMillis() + minutes * 60_000L
        when (kind) {
            KIND_LEVEL -> {
                levelSnoozeUntil = until
                levelRepeat = 0L // отреагировали — после отсрочки снова начнём с обычного уведомления
            }
            KIND_PREDICT -> predictSilenced = true
            else -> staleSnoozeUntil = until
        }
        return true
    }

    @Synchronized
    fun silence(kind: String): Boolean {
        if (!isActive(kind)) return false
        when (kind) {
            KIND_LEVEL -> levelSilenced = true
            KIND_PREDICT -> predictSilenced = true
            else -> staleSilenced = true
        }
        return true
    }

    /** true — отсрочка ещё идёт. Когда она закончилась, напоминаем сразу, не дожидаясь интервала повтора. */
    private fun snoozed(kind: String, now: Long): Boolean {
        val until = if (kind == KIND_LEVEL) levelSnoozeUntil else staleSnoozeUntil
        if (until == 0L) return false
        if (now < until) return true
        if (kind == KIND_LEVEL) {
            levelSnoozeUntil = 0L
            levelAlertAt = 0L
        } else {
            staleSnoozeUntil = 0L
            staleAlertAt = 0L
        }
        return false
    }

    /** recent — замеры за последние минуты, по ним считается прогноз. */
    @Synchronized
    fun onReading(r: Reading, now: Long, recent: List<Reading> = emptyList()) {
        if (staleAlertAt != 0L || staleSilenced) {
            val notified = staleAlertAt != 0L
            staleAlertAt = 0L
            staleSilenced = false
            staleSnoozeUntil = 0L
            Notifications.cancel(context, KIND_STALE)
            if (notified && prefs.notifyStale && !prefs.muted(now)) {
                Notifications.info(context, AlertType.RESUMED, "✅ Данные снова поступают", describe(r, now))
            }
        }
        checkLevel(r, now)
        checkForecast(r, recent, now)
    }

    /** Предупреждение заранее: сахар ещё не низкий, но падает и через predictMinutes будет ниже порога. */
    private fun checkForecast(r: Reading, recent: List<Reading>, now: Long) {
        // Уже низкий — работает обычная тревога
        if (level == LOW) {
            clearPredict()
            return
        }
        val f = forecast(recent, now, prefs.predictMinutes)
        val falling = f != null && r.mmol > prefs.low && f.slope <= -MIN_FALL_PER_MINUTE && f.predicted <= prefs.low
        if (!falling) {
            // Падение закончилось — с запасом, чтобы не мигать у границы прогноза
            if (predictActive && (f == null || f.slope > -MIN_FALL_PER_MINUTE / 2 || f.predicted > prefs.low + HYSTERESIS)) {
                clearPredict()
            }
            return
        }
        predictActive = true
        if (!prefs.notifyPredictLow || predictSilenced || prefs.muted(now)) return
        if (predictAlertAt != 0L && now - predictAlertAt < PREDICT_REPEAT_MINUTES * 60_000L) return
        predictAlertAt = now
        Notifications.predictLow(context, r, f!!.minutesTo(r.mmol, prefs.low), f.predicted, f.horizonMinutes)
    }

    private fun clearPredict() {
        if (predictActive || predictAlertAt != 0L) Notifications.cancel(context, KIND_PREDICT)
        predictActive = false
        predictAlertAt = 0L
        predictSilenced = false
    }

    private fun checkLevel(r: Reading, now: Long) {
        val v = r.mmol
        val outside = when {
            v <= prefs.low -> LOW
            v > prefs.high -> HIGH
            else -> null
        }
        if (outside != null) {
            if (outside != level) {
                // Новое событие: прежние «отложить»/«до нормализации» к нему не относятся
                level = outside
                levelAlertAt = 0L
                levelSilenced = false
                levelSnoozeUntil = 0L
                levelRepeat = 0L
            }
            val low = outside == LOW
            val enabled = if (low) prefs.notifyLow else prefs.notifyHigh
            if (!enabled || levelSilenced || prefs.muted(now) || snoozed(KIND_LEVEL, now)) return
            val repeatMinutes = if (low) prefs.lowRepeatMinutes else prefs.repeatMinutes
            if (levelAlertAt != 0L && now - levelAlertAt < repeatMinutes * 60_000L) return
            val repeat = if (levelAlertAt != 0L) (levelRepeat + 1).toInt() else 0
            levelAlertAt = now
            levelRepeat = repeat.toLong()
            // Низкий без реакции — со второго напоминания громкий сигнал будильника
            Notifications.level(context, low, r, repeat, escalate = low && repeat >= 1 && prefs.lowEscalate)
        } else if (level != OK && v >= prefs.low + HYSTERESIS && v <= prefs.high - HYSTERESIS) {
            val notified = levelAlertAt != 0L || levelSilenced
            level = OK
            levelAlertAt = 0L
            levelSilenced = false
            levelSnoozeUntil = 0L
            levelRepeat = 0L
            AlarmPlayer.stop()
            Notifications.cancel(context, KIND_LEVEL)
            if (notified && prefs.notifyRecovery && !prefs.muted(now)) {
                Notifications.info(context, AlertType.RECOVERY, "✅ Сахар в норме", describe(r, now))
            }
        }
    }

    @Synchronized
    fun checkStale(last: Reading?, startedAt: Long, error: String?, now: Long) {
        val age = now - (last?.ts ?: startedAt)
        if (age < prefs.staleMinutes * 60_000L) return
        if (!prefs.notifyStale || staleSilenced || prefs.muted(now) || snoozed(KIND_STALE, now)) return
        if (staleAlertAt != 0L && now - staleAlertAt < Prefs.STALE_REPEAT_MINUTES * 60_000L) return
        staleAlertAt = now
        Notifications.stale(context, age / 60_000, last, error)
    }

    companion object {
        const val KIND_LEVEL = "level"
        const val KIND_STALE = "stale"
        const val KIND_PREDICT = "predict"
        private const val OK = "ok"
        private const val LOW = "low"
        private const val HIGH = "high"

        // Чтобы не получать «в норме»/«низкий» при колебаниях у самого порога
        private const val HYSTERESIS = 0.3

        // Падение быстрее 0,05 ммоль/л в минуту (0,75 за 15 мин) — иначе прогноз на плавном спуске шумит
        private const val MIN_FALL_PER_MINUTE = 0.05
        private const val PREDICT_REPEAT_MINUTES = 15
    }
}

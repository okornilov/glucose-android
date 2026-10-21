package ru.okornilov.glucose.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Журнал фоновых опросов: время последнего опроса и задержки за сутки.
 * По нему видно, не останавливает ли система опрос (особенно ночью и на Xiaomi), и на него смотрит сторож.
 */
class PollLog(context: Context) {
    private val sp = context.getSharedPreferences("poll_log", Context.MODE_PRIVATE)

    /** Задержка: между опросами прошло больше [DELAY_MS] вместо минуты. */
    data class Gap(val end: Long, val minutes: Long)

    val lastPollAt: Long get() = sp.getLong("last_poll_at", 0L)

    @Synchronized
    fun onPoll(now: Long = System.currentTimeMillis()) {
        val last = lastPollAt
        val gaps = gaps(now).toMutableList()
        if (last != 0L && now - last > DELAY_MS) gaps += Gap(now, (now - last) / 60_000)
        sp.edit()
            .putLong("last_poll_at", now)
            .putString("gaps", JSONArray(gaps.map { JSONObject().put("end", it.end).put("min", it.minutes) }).toString())
            .apply()
    }

    /** Мониторинг выключили — пауза до включения не считается задержкой. */
    @Synchronized
    fun reset() {
        sp.edit().remove("last_poll_at").apply()
    }

    /** Задержки за последние сутки, по времени. */
    fun gaps(now: Long = System.currentTimeMillis()): List<Gap> {
        val array = JSONArray(sp.getString("gaps", "[]"))
        return (0 until array.length())
            .map { array.getJSONObject(it).let { o -> Gap(o.getLong("end"), o.getLong("min")) } }
            .filter { now - it.end < DAY_MS }
    }

    companion object {
        const val DELAY_MS = 5 * 60_000L
        private const val DAY_MS = 24 * 3600_000L
    }
}

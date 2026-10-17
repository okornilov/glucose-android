package ru.okornilov.glucose.data

import java.time.Instant
import java.time.ZoneId

/** Разброс значений в одном получасовом интервале суток: процентили 10/25/50/75/90. */
data class Band(val minuteOfDay: Int, val p10: Double, val p25: Double, val p50: Double, val p75: Double, val p90: Double)

/** Отчёт за период: итоги и «средний день» (AGP — амбулаторный профиль глюкозы). */
data class Report(
    val days: Int,
    val from: Long,
    val to: Long,
    /** Сколько дней реально покрыто данными — приложение копит историю с момента установки. */
    val coveredDays: Double,
    val stats: DayStatsData,
    val gmi: Double,
    val cv: Double?,
    val lowEpisodes: Int,
    val belowMinutes: Long,
    val aboveMinutes: Long,
    val bands: List<Band>,
)

private const val BUCKET_MINUTES = 30

fun buildReport(readings: List<Reading>, days: Int, from: Long, to: Long, low: Double, high: Double): Report? {
    val stats = dayStats(readings, low, high) ?: return null
    val minutes = coveredMinutes(readings)
    val zone = ZoneId.systemDefault()
    val buckets = readings.groupBy {
        val t = Instant.ofEpochMilli(it.ts).atZone(zone)
        (t.hour * 60 + t.minute) / BUCKET_MINUTES
    }
    val bands = buckets.toSortedMap().mapNotNull { (bucket, list) ->
        if (list.size < 3) return@mapNotNull null
        val v = list.map { it.mmol }.sorted()
        Band(bucket * BUCKET_MINUTES + BUCKET_MINUTES / 2, v.pct(10), v.pct(25), v.pct(50), v.pct(75), v.pct(90))
    }
    return Report(
        days = days,
        from = from,
        to = to,
        coveredDays = minutes / 1440.0,
        stats = stats,
        gmi = gmi(stats.avg),
        cv = variation(readings),
        lowEpisodes = lowEpisodes(readings, low),
        belowMinutes = Math.round(stats.below * minutes).toLong(),
        aboveMinutes = Math.round(stats.above * minutes).toLong(),
        bands = bands,
    )
}

/** Процентиль по отсортированному списку с линейной интерполяцией. */
private fun List<Double>.pct(p: Int): Double {
    val pos = (size - 1) * p / 100.0
    val i = pos.toInt()
    val frac = pos - i
    return if (i + 1 < size) this[i] + (this[i + 1] - this[i]) * frac else this[i]
}

/** «3 ч 20 мин», «45 мин». */
fun durationText(minutes: Long): String = when {
    minutes < 60 -> "$minutes мин"
    minutes % 60 == 0L -> "${minutes / 60} ч"
    else -> "${minutes / 60} ч ${minutes % 60} мин"
}

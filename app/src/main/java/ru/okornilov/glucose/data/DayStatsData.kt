package ru.okornilov.glucose.data

private const val MINUTE_MS = 60_000L
private const val MAX_WEIGHT_MS = 15 * MINUTE_MS

/** Статистика за период. Доли — от 0 до 1. */
data class DayStatsData(
    val count: Int,
    val min: Double,
    val max: Double,
    val avg: Double,
    val below: Float,
    val inRange: Float,
    val above: Float,
)

/**
 * Замеры неравномерные (история LibreView — раз в 15 мин, мониторинг — раз в минуту),
 * поэтому доли и среднее считаем по времени: замер действует до следующего, но не дольше 15 мин.
 */
fun dayStats(readings: List<Reading>, low: Double, high: Double): DayStatsData? {
    if (readings.isEmpty()) return null
    val values = readings.map { it.mmol }
    val weights = readings.mapIndexed { i, r ->
        val next = readings.getOrNull(i + 1)?.ts ?: (r.ts + MINUTE_MS)
        (next - r.ts).coerceIn(MINUTE_MS, MAX_WEIGHT_MS).toDouble()
    }
    val total = weights.sum()
    fun share(match: (Double) -> Boolean) =
        (values.indices.filter { match(values[it]) }.sumOf { weights[it] } / total).toFloat()
    val below = share { it <= low }
    val above = share { it > high }
    return DayStatsData(
        count = values.size,
        min = values.min(),
        max = values.max(),
        avg = values.indices.sumOf { values[it] * weights[it] } / total,
        below = below,
        inRange = 1f - below - above,
        above = above,
    )
}

fun percent(share: Float): String = "${Math.round(share * 100)}%"

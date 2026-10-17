package ru.okornilov.glucose.data

private const val MINUTE_MS = 60_000L

/** По скольким последним минутам считается тренд. */
private const val WINDOW_MINUTES = 15

/** Минимальный разброс замеров по времени, чтобы тренду можно было верить. */
private const val MIN_SPAN_MINUTES = 8

/** Тренд и прогноз. slope — ммоль/л в минуту, predicted — ожидаемое значение через horizon минут. */
data class Forecast(val slope: Double, val predicted: Double, val horizonMinutes: Int) {
    /** Через сколько минут значение дойдёт до level при текущей скорости; null — если не падает. */
    fun minutesTo(from: Double, level: Double): Int? =
        if (slope >= 0 || from <= level) null else Math.round((from - level) / -slope).toInt()
}

/**
 * Линейная регрессия по замерам за последние 15 минут (при мониторинге они приходят раз в минуту).
 * Прогноз отсчитывается от последнего значения, а не от линии — так он не отстаёт от текущего сахара.
 * null — мало данных: меньше трёх замеров или они покрывают меньше 8 минут.
 */
fun forecast(readings: List<Reading>, now: Long, horizonMinutes: Int): Forecast? {
    val recent = readings.filter { it.ts in (now - WINDOW_MINUTES * MINUTE_MS)..(now + MINUTE_MS) }
    if (recent.size < 3 || recent.last().ts - recent.first().ts < MIN_SPAN_MINUTES * MINUTE_MS) return null
    val t0 = recent.last().ts
    val xs = recent.map { (it.ts - t0).toDouble() / MINUTE_MS }
    val ys = recent.map { it.mmol }
    val mx = xs.average()
    val my = ys.average()
    val sxx = xs.sumOf { (it - mx) * (it - mx) }
    if (sxx == 0.0) return null
    val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / sxx
    return Forecast(slope, recent.last().mmol + slope * horizonMinutes, horizonMinutes)
}

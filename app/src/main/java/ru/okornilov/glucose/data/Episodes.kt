package ru.okornilov.glucose.data

/** Сколько раз сахар опускался до порога low и ниже (подряд идущие низкие замеры — один эпизод). */
fun lowEpisodes(readings: List<Reading>, low: Double): Int {
    var count = 0
    var inLow = false
    for (r in readings) {
        val isLow = r.mmol <= low
        if (isLow && !inLow) count++
        inLow = isLow
    }
    return count
}

/** Длительность периода, который покрывают замеры, в минутах. */
fun coveredMinutes(readings: List<Reading>): Long =
    if (readings.size < 2) 0 else (readings.last().ts - readings.first().ts) / 60_000

/** Коэффициент вариации, %: стандартное отклонение / среднее. Цель — не выше 36%. */
fun variation(readings: List<Reading>): Double? {
    if (readings.size < 2) return null
    val values = readings.map { it.mmol }
    val mean = values.average()
    val sd = Math.sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    return sd / mean * 100
}

/** Оценка HbA1c по средней глюкозе (GMI), %. */
fun gmi(avgMmol: Double): Double = 3.31 + 0.02392 * avgMmol * 18

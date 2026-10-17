package ru.okornilov.glucose.data

enum class Zone { LOW, IN_RANGE, HIGH }

/** Низкий — значение <= low, высокий — значение > high. */
fun zoneOf(mmol: Double, low: Double, high: Double): Zone = when {
    mmol <= low -> Zone.LOW
    mmol > high -> Zone.HIGH
    else -> Zone.IN_RANGE
}

/** Кусок отрезка между двумя замерами: доли [from, to] его длины и зона. */
data class ZonePiece(val from: Float, val to: Float, val zone: Zone)

/**
 * Делит отрезок между значениями a и b в точках пересечения порогов — чтобы линия меняла цвет
 * ровно на линии порога, а не скачком между замерами.
 */
fun zonePieces(a: Double, b: Double, low: Double, high: Double): List<ZonePiece> {
    val cuts = listOf(low, high)
        .mapNotNull { t -> if ((a - t) * (b - t) < 0) ((t - a) / (b - a)).toFloat() else null }
        .sorted()
    return (listOf(0f) + cuts + 1f).zipWithNext { f0, f1 ->
        ZonePiece(f0, f1, zoneOf(a + (b - a) * (f0 + f1) / 2, low, high))
    }
}

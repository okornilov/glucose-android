package ru.okornilov.glucose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ForecastTest {
    private val now = 1_700_000_000_000L

    /** Замеры раз в минуту за последние minutes минут: значение от start, меняется на step в минуту. */
    private fun series(minutes: Int, start: Double, step: Double) = (0..minutes).map { i ->
        val mmol = start + step * i
        Reading(now - (minutes - i) * 60_000L, Math.round(mmol * 18).toInt())
    }

    @Test
    fun fallingPredictsBelowCurrent() {
        // 6,0 → 5,0 за 10 минут: 0,1 ммоль/л в минуту
        val f = forecast(series(10, 6.0, -0.1), now, 20)
        assertNotNull(f)
        assertEquals(-0.1, f!!.slope, 0.01)
        assertEquals(3.0, f.predicted, 0.2)
        assertEquals(12, f.minutesTo(5.0, 3.8))
    }

    @Test
    fun flatIsNotFalling() {
        val f = forecast(series(12, 6.0, 0.0), now, 20)!!
        assertEquals(0.0, f.slope, 0.01)
        assertNull(f.minutesTo(6.0, 3.8))
    }

    @Test
    fun tooFewOrTooShortGivesNoForecast() {
        assertNull(forecast(series(1, 6.0, -0.1), now, 20))
        assertNull(forecast(series(5, 6.0, -0.1), now, 20)) // 5 минут — меньше 8
    }

    @Test
    fun ignoresOldReadings() {
        // Час назад было падение, последние 15 минут ровно
        val old = series(10, 9.0, -0.3).map { it.copy(ts = it.ts - 3_600_000L) }
        val f = forecast(old + series(14, 5.0, 0.0), now, 20)!!
        assertEquals(0.0, f.slope, 0.01)
    }
}

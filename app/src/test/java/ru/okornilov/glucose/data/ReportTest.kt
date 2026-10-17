package ru.okornilov.glucose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTest {
    private val start = 1_790_000_000_000L

    private fun at(minute: Int, mmol: Double) = Reading(start + minute * 60_000L, Math.round(mmol * 18).toInt())

    @Test
    fun emptyGivesNoReport() {
        assertNull(buildReport(emptyList(), 7, start, start + 7 * 86_400_000L, 3.8, 12.0))
    }

    @Test
    fun countsEpisodesAndBands() {
        // Двое суток раз в 15 минут: ровно 6.0, кроме двух провалов до 3.2
        val readings = (0 until 2 * 96).map { i ->
            val low = i in 10..12 || i in 120..121
            at(i * 15, if (low) 3.2 else 6.0)
        }
        val r = buildReport(readings, 7, start, start + 7 * 86_400_000L, 3.8, 12.0)
        assertNotNull(r)
        r!!
        assertEquals(2, r.lowEpisodes)
        assertTrue(r.coveredDays in 1.9..2.0)
        assertTrue(r.belowMinutes in 60..90)
        assertTrue(r.bands.isNotEmpty())
        assertEquals(6.0, r.bands.first { it.p10 == it.p90 }.p50, 0.01)
    }

    @Test
    fun episodesAndGmi() {
        assertEquals(1, lowEpisodes(listOf(at(0, 5.0), at(1, 3.5), at(2, 3.4), at(3, 5.0)), 3.8))
        assertEquals(6.32, gmi(7.0), 0.01) // 7.0 ммоль/л = 126 мг/дл
        assertEquals("3 ч 20 мин", durationText(200))
    }
}

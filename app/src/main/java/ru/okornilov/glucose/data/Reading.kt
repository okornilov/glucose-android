package ru.okornilov.glucose.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Показание сенсора. ts — момент замера, epoch millis (UTC). */
data class Reading(val ts: Long, val mgdl: Int, val trend: Int? = null) {
    /** Округлено до 0.1, как показывают приложения: пороги сравниваются с тем, что видит пользователь. */
    val mmol: Double get() = Math.round(mgdl / 18.0 * 10) / 10.0

    val arrow: String
        get() = when (trend) {
            1 -> "↓"
            2 -> "↘"
            3 -> "→"
            4 -> "↗"
            5 -> "↑"
            else -> ""
        }
}

/** Одна цифра после точки — разделитель всегда точка, независимо от языка системы. */
fun Double.fmt(): String = String.format(Locale.US, "%.1f", this)

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

fun hhmm(ts: Long): String = HHMM.format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))

fun ago(ts: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - ts) / 60_000
    return if (minutes < 1) "только что" else "$minutes мин назад"
}

fun describe(r: Reading, now: Long = System.currentTimeMillis()): String =
    "${r.mmol.fmt()} ммоль/л ${r.arrow} · ${hhmm(r.ts)} (${ago(r.ts, now)})"

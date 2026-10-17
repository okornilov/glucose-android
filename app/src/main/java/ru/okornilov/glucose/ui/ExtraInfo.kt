package ru.okornilov.glucose.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.okornilov.glucose.data.ReadingsDb
import ru.okornilov.glucose.data.dayStats
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.forecast
import ru.okornilov.glucose.data.hhmm
import ru.okornilov.glucose.data.percent
import kotlin.math.abs
import kotlin.math.ceil

private const val MINUTE_MS = 60_000L
private const val DAY_MS = 24 * 3600_000L

/** Тренд меньше этого (ммоль/л за 15 мин) считаем ровным. */
private const val FLAT_DELTA = 0.3

/** Нижняя строка главного экрана: тренд с прогнозом, срок сенсора и итоги за 7 дней. */
@Composable
fun ExtraInfo(db: ReadingsDb, dataVersion: Int, now: Long, low: Double, high: Double, predictMinutes: Int, sensorStart: Long, sensorDays: Int) {
    // now меняется каждые 20 с — данные перечитываем только после нового опроса
    val recent by produceState(emptyList(), dataVersion) {
        value = withContext(Dispatchers.IO) { System.currentTimeMillis().let { db.between(it - 20 * MINUTE_MS, it + MINUTE_MS) } }
    }
    val week by produceState(emptyList(), dataVersion) {
        value = withContext(Dispatchers.IO) { System.currentTimeMillis().let { db.between(it - 7 * DAY_MS, it + MINUTE_MS) } }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Тренд: изменение за 15 минут и где будет сахар через predictMinutes
        val f = forecast(recent, now, predictMinutes)
        if (f == null) {
            InfoTile("Тренд 15 мин", "—", muted, "мало данных", Modifier.weight(1f))
        } else {
            val delta = f.slope * 15
            val sign = if (delta > 0) "+" else if (delta < 0) "−" else ""
            InfoTile(
                "Тренд 15 мин",
                "$sign${abs(delta).fmt()}",
                if (abs(delta) < FLAT_DELTA) MaterialTheme.colorScheme.onSurface else levelColor(f.predicted, low, high),
                // Время, а не «через N мин» — короче и помещается в узкую плитку
                "≈ ${f.predicted.coerceAtLeast(1.0).fmt()} в ${hhmm(now + predictMinutes * MINUTE_MS)}",
                Modifier.weight(1f),
            )
        }

        // Сенсор: какой день и когда менять
        if (sensorStart <= 0) {
            InfoTile("Сенсор", "—", muted, "нет сведений", Modifier.weight(1f))
        } else {
            val elapsed = now - sensorStart
            val left = sensorDays * DAY_MS - elapsed
            val sub = when {
                left <= 0 -> "срок истёк"
                left < DAY_MS -> "замена через ${ceil(left / 3_600_000.0).toInt()} ч"
                else -> "замена через ${left / DAY_MS} дн"
            }
            InfoTile(
                "Сенсор",
                "${(elapsed / DAY_MS + 1).coerceAtMost(sensorDays.toLong())} / $sensorDays дн",
                if (left < DAY_MS) LowColor else MaterialTheme.colorScheme.onSurface,
                sub,
                Modifier.weight(1f),
            )
        }

        // Итоги за неделю (или за сколько есть данных): время в диапазоне и оценка HbA1c по GMI
        val stats = dayStats(week, low, high)
        if (stats == null) {
            InfoTile("За 7 дней", "—", muted, "нет данных", Modifier.weight(1f))
        } else {
            val days = ceil((week.last().ts - week.first().ts).toDouble() / DAY_MS).toInt().coerceIn(1, 7)
            // GMI (%) = 3,31 + 0,02392 × средняя глюкоза в мг/дл
            val gmi = 3.31 + 0.02392 * stats.avg * 18
            InfoTile(
                if (days == 7) "За 7 дней" else "За $days дн.",
                percent(stats.inRange),
                InRangeColor,
                "HbA1c ≈ ${gmi.fmt()}%",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String, valueColor: Color, sub: String, modifier: Modifier) {
    Card(
        modifier,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

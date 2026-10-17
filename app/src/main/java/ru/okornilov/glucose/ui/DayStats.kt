package ru.okornilov.glucose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.okornilov.glucose.data.Reading
import ru.okornilov.glucose.data.dayStats
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.percent

/** Статистика за день: мин/среднее/макс и время в диапазоне. */
@Composable
fun DayStats(readings: List<Reading>, low: Double, high: Double) {
    if (readings.isEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Text(
                "Нет данных за этот день",
                Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val stats = dayStats(readings, low, high) ?: return
    val n = stats.count
    val belowShare = stats.below
    val aboveShare = stats.above
    val inRangeShare = stats.inRange
    val avg = stats.avg

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Мин", stats.min, low, high, Modifier.weight(1f))
        StatTile("Среднее", avg, low, high, Modifier.weight(1f))
        StatTile("Макс", stats.max, low, high, Modifier.weight(1f))
    }

    // Без карточки — прозрачный блок на фоне экрана
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Время в диапазоне", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(
                "$n замеров · ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                percent(inRangeShare),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = InRangeColor,
            )
        }
        RangeBar(belowShare, inRangeShare, aboveShare)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Legend(LowColor, "ниже ${low.fmt()}", percent(belowShare))
            Legend(InRangeColor, "в диапазоне", percent(inRangeShare))
            Legend(HighColor, "выше ${high.fmt()}", percent(aboveShare))
        }
    }
}

@Composable
private fun StatTile(label: String, value: Double, low: Double, high: Double, modifier: Modifier) {
    val color = levelColor(value, low, high)
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.fmt(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

/** Полоса из трёх сегментов: ниже / в диапазоне / выше. */
@Composable
private fun RangeBar(below: Float, inRange: Float, above: Float) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(below to LowColor, inRange to InRangeColor, above to HighColor)
            .filter { it.first > 0f }
            .forEach { (share, color) ->
                Box(
                    Modifier
                        .weight(share)
                        .fillMaxHeight()
                        .background(color),
                )
            }
    }
}

@Composable
private fun Legend(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


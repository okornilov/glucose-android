package ru.okornilov.glucose.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.okornilov.glucose.app
import ru.okornilov.glucose.data.Report
import ru.okornilov.glucose.data.buildReport
import ru.okornilov.glucose.data.durationText
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.percent
import ru.okornilov.glucose.render.AgpPainter
import ru.okornilov.glucose.share.ReportPdf

private const val DAY_MS = 24 * 3600_000L

/** Отчёт за 7/14/30 дней: итоги, время в диапазоне, «средний день» и отправка PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen() {
    val ctx = LocalContext.current
    val app = ctx.app
    val dataVersion by app.repo.dataVersion.collectAsStateWithLifecycle()
    app.prefs.changes.collectAsStateWithLifecycle().value
    val low = app.prefs.low
    val high = app.prefs.high
    val scope = rememberCoroutineScope()
    var days by rememberSaveable { mutableIntStateOf(14) }

    val report by produceState<Report?>(null, days, dataVersion, low, high) {
        value = withContext(Dispatchers.IO) {
            val to = System.currentTimeMillis()
            val from = to - days * DAY_MS
            buildReport(app.db.between(from, to), days, from, to, low, high)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val options = listOf(7, 14, 30)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, d ->
                SegmentedButton(
                    selected = days == d,
                    onClick = { days = d },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    icon = {},
                ) { Text("$d дней") }
            }
        }

        val r = report
        if (r == null) {
            Text("Нет данных за этот период", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        Column {
            Text(ReportPdf.period(r), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.coveredDays < r.days - 0.5) {
                Text(
                    "Данные есть за ${r.coveredDays.fmt()} дн. — история копится с момента установки, LibreView отдаёт только 12 часов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsGroup("Итоги") {
            SettingRow("Среднее", "Мин ${r.stats.min.fmt()} · макс ${r.stats.max.fmt()}") { Value("${r.stats.avg.fmt()} ммоль/л") }
            GroupDivider()
            SettingRow("HbA1c (GMI)", "Оценка по средней глюкозе, не анализ крови") { Value("≈ ${r.gmi.fmt()}%") }
            GroupDivider()
            SettingRow("Вариабельность", "Цель — не выше 36%") {
                Value(r.cv?.let { "${it.fmt()}%" } ?: "—", if ((r.cv ?: 0.0) > 36) HighColor else null)
            }
            GroupDivider()
            SettingRow("Эпизоды низкого", "Всего ниже ${low.fmt()}: ${durationText(r.belowMinutes)}") {
                Value("${r.lowEpisodes}", if (r.lowEpisodes > 0) LowColor else null)
            }
        }

        SettingsGroup("Время в диапазоне ${low.fmt()}–${high.fmt()}") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RangeBar(r.stats.below, r.stats.inRange, r.stats.above)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Legend(LowColor, "ниже", percent(r.stats.below))
                    Legend(InRangeColor, "в диапазоне", percent(r.stats.inRange))
                    Legend(HighColor, "выше", percent(r.stats.above))
                }
                Text(
                    "Цели: в диапазоне больше 70%, ниже меньше 4%. Выше порога — ${durationText(r.aboveMinutes)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsGroup("Средний день") {
            val density = LocalDensity.current.density
            val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f).toArgb()
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(12.dp),
            ) {
                drawIntoCanvas {
                    AgpPainter.draw(
                        it.nativeCanvas, RectF(0f, 0f, size.width, size.height), r.bands, low, high,
                        scale = density, textColor = textColor, gridColor = gridColor,
                    )
                }
            }
            Text(
                "Линия — медиана, тёмная полоса — половина значений (25–75%), светлая — 10–90%.",
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FilledTonalButton(
            onClick = {
                scope.launch {
                    val intent = withContext(Dispatchers.Default) { ReportPdf.intent(ctx, r, low, high) }
                    ctx.startActivity(intent)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Text("  Отправить PDF врачу")
        }
    }
}

@Composable
private fun Value(text: String, color: androidx.compose.ui.graphics.Color? = null) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = color ?: MaterialTheme.colorScheme.onSurface,
    )
}

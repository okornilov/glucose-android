package ru.okornilov.glucose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.okornilov.glucose.app
import ru.okornilov.glucose.data.Reading
import ru.okornilov.glucose.data.ago
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.hhmm
import ru.okornilov.glucose.profile.ProfileSwitcher
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy")

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app
    val last by app.repo.last.collectAsStateWithLifecycle()
    val error by app.repo.error.collectAsStateWithLifecycle()
    val dataVersion by app.repo.dataVersion.collectAsStateWithLifecycle()
    app.prefs.changes.collectAsStateWithLifecycle().value // перерисовать после смены порогов
    val low = app.prefs.low
    val high = app.prefs.high

    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }
    LaunchedEffect(Unit) {
        refreshing = true
        app.repo.refresh()
        refreshing = false
    }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var day by rememberSaveable { mutableStateOf(today) }
    val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val readings by produceState(emptyList<Reading>(), start, end, dataVersion) {
        value = withContext(Dispatchers.IO) { app.db.between(start, end) }
    }

    // Всё на одном экране: график забирает оставшееся место. Если экран слишком низкий
    // (или крупный шрифт в системе) — обычная прокрутка с графиком фиксированной высоты.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fits = maxHeight >= 560.dp
        val halfScreen = LocalConfiguration.current.screenHeightDp.dp / 2
        Column(
            Modifier
                .fillMaxSize()
                .then(if (fits) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val profiles = app.prefs.profiles()
            if (profiles.size > 1) {
                val active = app.prefs.active
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    profiles.forEach { p ->
                        FilterChip(
                            selected = p.id == active.id,
                            onClick = { ProfileSwitcher.activate(ctx, p.id) },
                            label = { Text(p.name) },
                        )
                    }
                }
            }

            CurrentCard(last, error, now, low, high)

            Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { day = day.minusDays(1) }) { Text("◀") }
                Text(if (day == today) "Сегодня" else DAY_FORMAT.format(day), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { day = day.plusDays(1) }, enabled = day < today) { Text("▶") }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        scope.launch {
                            refreshing = true
                            app.repo.refresh()
                            refreshing = false
                        }
                    },
                    enabled = !refreshing,
                ) { Text(if (refreshing) "Обновляю…" else "Обновить") }
                Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Text("⋮", fontSize = 22.sp) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("⚙️  Настройки") },
                            onClick = {
                                menu = false
                                onOpenSettings()
                            },
                        )
                    }
                }
            }

            GlucoseChart(
                readings, start, end, low, high,
                Modifier
                    .fillMaxWidth()
                    // Не выше половины экрана; если места меньше — сжимается
                    .then(if (fits) Modifier.weight(1f, fill = false) else Modifier)
                    .height(if (fits) halfScreen else 240.dp),
            )

            DayStats(readings, low, high)
        }
    }
}

/** Значение считается устаревшим, если запрос к серверу не удался или замеру больше стольких минут. */
private const val FRESH_MINUTES = 5

/** Текущее значение: крупно слева, время замера справа. Устаревшее значение выделено рамкой и приглушено. */
@Composable
private fun CurrentCard(last: Reading?, error: String?, now: Long, low: Double, high: Double) {
    val prefs = LocalContext.current.app.prefs
    val colors = MaterialTheme.colorScheme
    val ageMinutes = last?.let { (now - it.ts) / 60_000 }
    val stale = last != null && (error != null || ageMinutes!! >= FRESH_MINUTES)
    Card(
        Modifier.fillMaxWidth(),
        border = if (stale) BorderStroke(2.dp, colors.error) else null,
        colors = if (stale) CardDefaults.cardColors(containerColor = colors.errorContainer.copy(alpha = 0.25f)) else CardDefaults.cardColors(),
    ) {
        if (stale) {
            Text(
                "⚠ Нет свежих данных · последний замер в ${hhmm(last!!.ts)} ($ageMinutes мин назад)",
                Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.error,
            )
        }
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (last == null) {
                Text("Нет данных", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            } else {
                val level = levelColor(last.mmol, low, high)
                val color = if (stale) level.copy(alpha = 0.45f) else level
                Text(last.mmol.fmt(), fontSize = 52.sp, fontWeight = FontWeight.Bold, color = color)
                Text(" ${last.arrow}", fontSize = 36.sp, color = color)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("ммоль/л", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Text(hhmm(last.ts), style = MaterialTheme.typography.titleMedium, color = if (stale) colors.error else Color.Unspecified)
                    Text(ago(last.ts, now), style = MaterialTheme.typography.labelSmall, color = if (stale) colors.error else colors.onSurfaceVariant)
                }
            }
        }
        val notes = buildList {
            error?.let { add("Ошибка: $it") }
            if (!prefs.monitoring) add("Фоновый мониторинг выключен — уведомлений не будет")
            if (prefs.muted(now)) add("🔕 Тишина до ${hhmm(prefs.muteUntil)}")
        }
        if (notes.isNotEmpty()) {
            Text(
                notes.joinToString("\n"),
                Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
            )
        }
    }
}

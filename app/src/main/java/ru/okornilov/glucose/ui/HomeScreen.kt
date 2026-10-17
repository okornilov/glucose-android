package ru.okornilov.glucose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.okornilov.glucose.app
import ru.okornilov.glucose.data.Freshness
import ru.okornilov.glucose.data.Reading
import ru.okornilov.glucose.data.freshness
import ru.okornilov.glucose.data.ago
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.hhmm
import ru.okornilov.glucose.profile.ProfileSwitcher
import ru.okornilov.glucose.share.Sharer
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

    // Картинка рисуется не на главном потоке, затем — системное меню «Поделиться»
    val share: () -> Unit = {
        scope.launch {
            val intent = withContext(Dispatchers.Default) {
                Sharer.intent(ctx, last, error, day, readings, start, end, low, high)
            }
            ctx.startActivity(intent)
        }
    }

    // Всё на одном экране: график забирает оставшееся место. Если экран слишком низкий
    // (или крупный шрифт в системе) — обычная прокрутка с графиком фиксированной высоты.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fits = maxHeight >= 560.dp
        val thirdScreen = LocalConfiguration.current.screenHeightDp.dp / 3
        Column(
            Modifier
                .fillMaxSize()
                .then(if (fits) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Верхняя строка: профили слева (прокручиваются), действия справа
            val profiles = app.prefs.profiles()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Профиль — выпадающим списком, чтобы строка помещалась при любом числе профилей
                Box(Modifier.weight(1f)) {
                    var profileMenu by remember { mutableStateOf(false) }
                    val active = app.prefs.active
                    OutlinedButton(
                        onClick = { profileMenu = true },
                        contentPadding = PaddingValues(start = 14.dp, end = 6.dp),
                    ) {
                        Text(active.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать профиль")
                    }
                    DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                        profiles.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                leadingIcon = {
                                    if (p.id == active.id) Icon(Icons.Default.Check, contentDescription = "Активный")
                                },
                                onClick = {
                                    profileMenu = false
                                    ProfileSwitcher.activate(ctx, p.id)
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Настроить профили…") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                profileMenu = false
                                onOpenSettings()
                            },
                        )
                    }
                }
                IconButton(onClick = share) { Icon(Icons.Default.Share, contentDescription = "Поделиться") }
                IconButton(
                    onClick = {
                        scope.launch {
                            refreshing = true
                            app.repo.refresh()
                            refreshing = false
                        }
                    },
                    enabled = !refreshing,
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
                Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Меню") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Поделиться") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                menu = false
                                share()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Настройки") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                menu = false
                                onOpenSettings()
                            },
                        )
                    }
                }
            }

            CurrentCard(last, error, now, low, high)

            Row(Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { day = day.minusDays(1) }) { Text("◀") }
                Text(if (day == today) "Сегодня" else DAY_FORMAT.format(day), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { day = day.plusDays(1) }, enabled = day < today) { Text("▶") }
            }

            GlucoseChart(
                readings, start, end, low, high,
                Modifier
                    .fillMaxWidth()
                    // Не выше трети экрана; если места меньше — сжимается
                    .then(if (fits) Modifier.weight(1f, fill = false) else Modifier)
                    .height(if (fits) thirdScreen else 240.dp),
            )

            DayStats(readings, low, high)
        }
    }
}

/** Текущее значение: крупно слева, время замера справа. Устаревшее значение выделено рамкой и приглушено. */
@Composable
private fun CurrentCard(last: Reading?, error: String?, now: Long, low: Double, high: Double) {
    val prefs = LocalContext.current.app.prefs
    val colors = MaterialTheme.colorScheme
    val ageMinutes = last?.let { (now - it.ts) / 60_000 }
    val freshness = freshness(last, error, now)
    val stale = last != null && freshness != Freshness.FRESH
    val notes = buildList {
        error?.let { add("Ошибка связи: $it") }
        if (freshness == Freshness.NO_NEW_DATA && last != null) {
            add("LibreView отвечает, но новых замеров нет — проверьте сенсор и телефон с LibreLink")
        }
        if (!prefs.monitoring) add("Фоновый мониторинг выключен — уведомлений не будет")
        if (prefs.muted(now)) add("🔕 Тишина до ${hhmm(prefs.muteUntil)}")
    }
    // Фон и рамка — только когда есть о чём предупредить; в обычном состоянии блок прозрачный
    val container = when {
        stale -> colors.errorContainer.copy(alpha = 0.25f)
        last == null || notes.isNotEmpty() -> CardDefaults.cardColors().containerColor
        else -> Color.Transparent
    }
    Card(
        Modifier.fillMaxWidth(),
        border = if (stale) BorderStroke(2.dp, colors.error) else null,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        if (stale) {
            Text(
                when (freshness) {
                    Freshness.NO_SERVER -> "⚠ Нет связи с сервером LibreView · показан замер $ageMinutes мин назад"
                    else -> "⚠ На сервере нет новых данных · последний замер $ageMinutes мин назад"
                },
                Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.error,
            )
        }
        Row(Modifier.padding(horizontal = 18.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            if (last == null) {
                Text("Нет данных", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            } else {
                val level = levelColor(last.mmol, low, high)
                val color = if (stale) level.copy(alpha = 0.45f) else level
                Text(last.mmol.fmt(), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = color)
                Text(" ${last.arrow}", fontSize = 44.sp, color = color)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("ммоль/л", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Text(hhmm(last.ts), style = MaterialTheme.typography.titleMedium, color = if (stale) colors.error else Color.Unspecified)
                    Text(ago(last.ts, now), style = MaterialTheme.typography.labelSmall, color = if (stale) colors.error else colors.onSurfaceVariant)
                }
            }
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

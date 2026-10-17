package ru.okornilov.glucose.ui

import android.app.NotificationManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.okornilov.glucose.R
import ru.okornilov.glucose.alerts.Notifications
import ru.okornilov.glucose.app
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.hhmm
import ru.okornilov.glucose.profile.ProfileTileService
import ru.okornilov.glucose.service.MonitorService
import ru.okornilov.glucose.widget.GlucoseWidget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val prefs = ctx.app.prefs
    prefs.changes.collectAsStateWithLifecycle().value

    // Разрешения меняются в системных настройках — перечитываем при возврате в приложение
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumed++
        onPauseOrDispose { }
    }
    val unrestricted = remember(resumed) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    val notificationsOn = remember(resumed) {
        ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }
    val warning: @Composable () -> Unit = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsGroup("Мониторинг", Icons.Default.Refresh) {
            SwitchSetting(
                "Фоновый мониторинг",
                prefs.monitoring,
                subtitle = "Опрос LibreView раз в минуту и уведомления",
                leading = { Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            ) { on ->
                prefs.monitoring = on
                if (on) MonitorService.start(ctx) else MonitorService.stop(ctx)
            }
            if (!unrestricted) {
                GroupDivider()
                SettingRow(
                    "Работа в фоне ограничена",
                    "Samsung может останавливать опрос — разрешите работу без ограничений батареи",
                    leading = warning,
                    titleColor = MaterialTheme.colorScheme.error,
                ) {
                    FilledTonalButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                        )
                    }) { Text("Разрешить") }
                }
            }
            GroupDivider()
            SwitchSetting(
                "Сахар в уведомлении",
                prefs.showOngoing,
                subtitle = if (prefs.showOngoing) {
                    "Значение в шторке и строке состояния"
                } else {
                    "Вместо сахара — тихое «мониторинг работает» без значка"
                },
                leading = { Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            ) { on ->
                prefs.showOngoing = on
                if (prefs.monitoring) MonitorService.start(ctx) // перерисовать постоянное уведомление
            }
            if (!prefs.showOngoing) {
                SettingRow(
                    "Скрыть совсем",
                    "Выключите канал «Фоновая работа» — опрос и тревоги продолжат работать",
                    leading = {},
                ) {
                    FilledTonalButton(onClick = { openNotificationSettings(ctx, Notifications.CH_MONITOR_QUIET) }) { Text("Открыть") }
                }
            }
            if (Build.VERSION.SDK_INT >= 36 && prefs.showOngoing) {
                GroupDivider()
                SwitchSetting(
                    "Сахар в строке состояния",
                    prefs.liveUpdate,
                    subtitle = "Live Update: значение у часов и на экране блокировки (Now Bar)",
                    leading = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                ) { on ->
                    prefs.liveUpdate = on
                    if (prefs.monitoring) MonitorService.start(ctx) // перерисовать постоянное уведомление
                }
            }
            if (isXiaomi()) {
                GroupDivider()
                SettingRow(
                    "Автозапуск (Xiaomi)",
                    "Без него HyperOS не перезапустит опрос после перезагрузки",
                    leading = warning,
                ) {
                    FilledTonalButton(onClick = { openXiaomiAutostart(ctx) }) { Text("Открыть") }
                }
                GroupDivider()
                SettingRow(
                    "Экономия батареи (Xiaomi)",
                    "Выберите «Нет ограничений», а приложение закрепите в недавних",
                    leading = warning,
                ) {
                    FilledTonalButton(onClick = { openXiaomiBattery(ctx) }) { Text("Открыть") }
                }
            }
            if (!notificationsOn) {
                GroupDivider()
                SettingRow(
                    "Уведомления выключены",
                    "Тревоги о сахаре не будут приходить",
                    leading = warning,
                    titleColor = MaterialTheme.colorScheme.error,
                ) {
                    FilledTonalButton(onClick = { openNotificationSettings(ctx, null) }) { Text("Включить") }
                }
            }
        }

        AccountGroup()

        ProfilesSection()

        SettingsGroup("Сенсор и сводки", Icons.Default.DateRange) {
            SettingRow(
                "Срок службы сенсора",
                "14 дней — Libre 2 и 3, 15 — модели Plus",
                leading = { Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            ) {
                SingleChoiceSegmentedButtonRow {
                    listOf(14, 15).forEachIndexed { i, days ->
                        SegmentedButton(
                            selected = prefs.sensorDays == days,
                            onClick = { prefs.sensorDays = days },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            icon = {},
                        ) { Text("$days") }
                    }
                }
            }
            GroupDivider()
            SwitchSetting(
                "Напоминать о замене сенсора",
                prefs.notifySensor,
                subtitle = "За сутки и за 2 часа до конца срока",
                leading = { Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            ) { prefs.notifySensor = it }
            GroupDivider()
            SwitchSetting(
                "Утренняя сводка за ночь",
                prefs.summaryEnabled,
                subtitle = "Мин, среднее, время в диапазоне и низкий сахар с 23:00",
                leading = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            ) {
                prefs.summaryEnabled = it
                if (it) ctx.app.reminders.summaryChanged()
            }
            if (prefs.summaryEnabled) {
                var picking by remember { mutableStateOf(false) }
                val time = "%02d:%02d".format(prefs.summaryMinute / 60, prefs.summaryMinute % 60)
                SettingRow(
                    "Время уведомления",
                    "Сводка за ночь с 23:00 до $time",
                    leading = {},
                    onClick = { picking = true },
                ) {
                    FilledTonalButton(onClick = { picking = true }) { Text(time) }
                }
                if (picking) {
                    SummaryTimeDialog(prefs.summaryMinute, onDismiss = { picking = false }) {
                        prefs.summaryMinute = it
                        ctx.app.reminders.summaryChanged()
                    }
                }
            }
        }

        SettingsGroup("Тишина", Icons.Default.Notifications) {
            if (prefs.muted()) {
                SettingRow(
                    "Уведомления выключены",
                    "До ${hhmm(prefs.muteUntil)} тревоги не придут",
                    leading = { Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                ) {
                    FilledTonalButton(onClick = { prefs.muteUntil = 0L }) { Text("Снять") }
                }
            } else {
                SettingRow(
                    "Временно выключить уведомления",
                    "Все тревоги, включая низкий сахар",
                    leading = { Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 54.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(30 to "30 мин", 60 to "1 час", 120 to "2 часа").forEach { (minutes, label) ->
                        OutlinedButton(
                            onClick = { prefs.muteUntil = System.currentTimeMillis() + minutes * 60_000L },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text(label, maxLines = 1) }
                    }
                }
            }
        }

        SettingsGroup("Виджет", Icons.Default.Home) {
            SettingRow("Прозрачность фона", "0% — полностью прозрачный")
            val options = listOf(0, 25, 50, 75, 100)
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 54.dp, end = 16.dp, bottom = 12.dp),
            ) {
                options.forEachIndexed { i, opacity ->
                    SegmentedButton(
                        selected = prefs.widgetOpacity == opacity,
                        onClick = {
                            prefs.widgetOpacity = opacity
                            GlucoseWidget.updateAll(ctx)
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        icon = {},
                    ) { Text("$opacity", maxLines = 1) }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            SettingsGroup("Быстрые настройки", Icons.Default.Settings) {
                SettingRow(
                    "Плитка «Профиль»",
                    "Переключает профили одним нажатием из шторки",
                    leading = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                ) {
                    FilledTonalButton(onClick = { requestTile(ctx) }) { Text("Добавить") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Учётная запись LibreLinkUp: свёрнута в строку, по нажатию раскрывается форма входа. */
@Composable
private fun AccountGroup() {
    val ctx = LocalContext.current
    val app = ctx.app
    val prefs = app.prefs
    val scope = rememberCoroutineScope()
    val error by app.repo.error.collectAsStateWithLifecycle()
    val last by app.repo.last.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(prefs.email.isBlank()) }

    SettingsGroup("Учётная запись LibreLinkUp", Icons.Default.AccountCircle) {
        val status = when {
            prefs.email.isBlank() -> "Не настроена"
            error != null -> "Ошибка: $error"
            last != null -> "Подключено"
            else -> "Подключение…"
        }
        SettingRow(
            prefs.email.ifBlank { "Войти в LibreLinkUp" },
            "Регион: ${prefs.region.ifBlank { "авто" }} · $status",
            leading = {
                Icon(
                    if (error == null && last != null) Icons.Default.CheckCircle else Icons.Default.Person,
                    contentDescription = null,
                    tint = when {
                        error != null -> MaterialTheme.colorScheme.error
                        last != null -> InRangeColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            onClick = { expanded = !expanded },
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Свернуть" else "Изменить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            var email by remember { mutableStateOf(prefs.email) }
            var password by remember { mutableStateOf(prefs.password) }
            var region by remember { mutableStateOf(prefs.region) }
            var showPassword by remember { mutableStateOf(false) }
            var checkResult by remember { mutableStateOf<String?>(null) }
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    email, { email = it },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    password, { password = it },
                    label = { Text("Пароль") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Скрыть" else "Показать") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    region, { region = it.trim().lowercase() },
                    label = { Text("Регион") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    supportingText = { Text("ru — для России, пусто — определить автоматически") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        prefs.email = email.trim()
                        prefs.password = password
                        prefs.region = region
                        checkResult = "Проверяю…"
                        scope.launch {
                            val r = app.repo.refresh()
                            checkResult = app.repo.error.value?.let { "⚠ $it" }
                                ?: r?.let { "✅ Вход выполнен, сейчас ${it.mmol.fmt()} ммоль/л" }
                                ?: "✅ Вход выполнен"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сохранить и проверить") }
                checkResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@RequiresApi(33)
private fun requestTile(ctx: Context) {
    ctx.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(ctx, ProfileTileService::class.java),
        ctx.getString(R.string.tile_label),
        Icon.createWithResource(ctx, R.drawable.ic_stat),
        ctx.mainExecutor,
    ) { }
}

internal fun openNotificationSettings(ctx: Context, channel: String?) {
    val intent = if (channel == null) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    } else {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, channel)
    }
    ctx.startActivity(intent.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
}

private fun isXiaomi(): Boolean =
    Build.MANUFACTURER.lowercase().let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }

/** Открыть первое подходящее окно из списка; если на прошивке ни одного нет — карточку приложения. */
private fun openFirst(ctx: Context, intents: List<Intent>) {
    for (intent in intents) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
            // Окно есть не во всех версиях MIUI/HyperOS — пробуем следующее
        }
    }
    ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun openXiaomiAutostart(ctx: Context) = openFirst(
    ctx,
    listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
    ),
)

private fun openXiaomiBattery(ctx: Context) = openFirst(
    ctx,
    listOf(
        Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
            .putExtra("package_name", ctx.packageName)
            .putExtra("package_label", ctx.getString(R.string.app_name)),
        Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            .putExtra("package_name", ctx.packageName)
            .putExtra("package_label", ctx.getString(R.string.app_name)),
    ),
)

/** Выбор времени утренней сводки. Ночь считается с 23:00, поэтому время — с 03:00 до 14:00. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryTimeDialog(minuteOfDay: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(minuteOfDay / 60, minuteOfDay % 60, is24Hour = true)
    val picked = state.hour * 60 + state.minute
    val ok = picked in 3 * 60..14 * 60
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Время утренней сводки") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state)
                if (!ok) Text("Выберите время с 03:00 до 14:00", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(picked)
                    onDismiss()
                },
                enabled = ok,
            ) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

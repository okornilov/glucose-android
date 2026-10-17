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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import ru.okornilov.glucose.app
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.hhmm
import ru.okornilov.glucose.profile.ProfileTileService
import ru.okornilov.glucose.service.MonitorService
import ru.okornilov.glucose.widget.GlucoseWidget

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val app = ctx.app
    val prefs = app.prefs
    val scope = rememberCoroutineScope()
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Мониторинг")
        SwitchRow("Фоновый мониторинг и уведомления", prefs.monitoring) { on ->
            prefs.monitoring = on
            if (on) MonitorService.start(ctx) else MonitorService.stop(ctx)
        }
        if (!unrestricted) {
            Text("Чтобы Samsung не останавливал опрос в фоне, разрешите приложению работать без ограничений батареи.")
            Button(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                )
            }) { Text("Разрешить работу в фоне") }
        }
        if (!notificationsOn) {
            Text("Уведомления приложения выключены.", color = MaterialTheme.colorScheme.error)
            Button(onClick = { openNotificationSettings(ctx, null) }) { Text("Разрешить уведомления") }
        }

        Section("Учётная запись LibreLinkUp")
        var email by remember { mutableStateOf(prefs.email) }
        var password by remember { mutableStateOf(prefs.password) }
        var region by remember { mutableStateOf(prefs.region) }
        var checkResult by remember { mutableStateOf<String?>(null) }
        OutlinedTextField(
            email, { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            password, { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            region, { region = it.trim().lowercase() },
            label = { Text("Регион: ru для России, пусто — автоматически") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
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
        }) { Text("Сохранить и проверить") }
        checkResult?.let { Text(it) }

        ProfilesSection()

        Section("Тишина")
        if (prefs.muted()) {
            Text("🔕 Уведомления выключены до ${hhmm(prefs.muteUntil)}")
            OutlinedButton(onClick = { prefs.muteUntil = 0L }) { Text("🔔 Снять тишину") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "30 мин", 60 to "1 час", 120 to "2 часа").forEach { (minutes, label) ->
                    OutlinedButton(onClick = { prefs.muteUntil = System.currentTimeMillis() + minutes * 60_000L }) {
                        Text("🔕 $label")
                    }
                }
            }
        }

        Section("Виджет")
        Text("Непрозрачность фона (0% — полностью прозрачный)", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 25, 50, 75, 100).forEach { opacity ->
                FilterChip(
                    selected = prefs.widgetOpacity == opacity,
                    onClick = {
                        prefs.widgetOpacity = opacity
                        GlucoseWidget.updateAll(ctx)
                    },
                    label = { Text("$opacity%") },
                )
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            Section("Плитка в быстрых настройках")
            Text("Плитка «Профиль» в шторке переключает профили одним нажатием.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { requestTile(ctx) }) { Text("Добавить плитку") }
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

@Composable
internal fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Поле ввода числа: сохраняет значение, как только оно корректно. */
@Composable
internal fun <T : Any> NumberField(
    label: String,
    initial: String,
    hint: String,
    parse: (String) -> T?,
    valid: (T) -> Boolean,
    onValid: (T) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val ok = parse(text)?.let(valid) ?: false
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            parse(input)?.takeIf(valid)?.let(onValid)
        },
        label = { Text(label) },
        isError = !ok,
        supportingText = { if (!ok) Text(hint) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun openNotificationSettings(ctx: Context, channel: String?) {
    val intent = if (channel == null) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    } else {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, channel)
    }
    ctx.startActivity(intent.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
}

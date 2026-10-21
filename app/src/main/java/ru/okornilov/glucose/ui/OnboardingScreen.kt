package ru.okornilov.glucose.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import ru.okornilov.glucose.app
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.service.MonitorService

/** Первый запуск: вход в LibreLinkUp, затем разрешения и включение мониторинга. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (step == 0) LoginStep(onLoggedIn = { step = 1 }) else SetupStep(onDone)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginStep(onLoggedIn: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app
    val prefs = app.prefs
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf(prefs.email) }
    var password by remember { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf(prefs.region.ifBlank { "ru" }) }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Text("Сахар", fontSize = 40.sp, fontWeight = FontWeight.Black, color = InRangeColor)
        Text(
            "Сахар в реальном времени, тревоги о низком и высоком, виджеты и отчёты — по данным LibreLinkUp",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }

    SettingsGroup("Вход через LibreLinkUp", Icons.Default.Person) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("ru" to "Россия", "" to "Другие страны").forEachIndexed { i, (code, label) ->
                    SegmentedButton(
                        selected = region == code,
                        onClick = { region = code },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        icon = {},
                    ) { Text(label) }
                }
            }
            OutlinedTextField(
                email, { email = it },
                label = { Text("Email LibreLinkUp") },
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
            error?.let {
                Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = {
                    prefs.email = email.trim()
                    prefs.password = password
                    prefs.region = region
                    busy = true
                    error = null
                    scope.launch {
                        app.repo.refresh()
                        busy = false
                        error = app.repo.error.value
                        if (error == null) onLoggedIn()
                    }
                },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Войти")
            }
        }
    }

    SettingsGroup("Нет аккаунта?", Icons.Default.Share) {
        SettingRow(
            "1. Создайте аккаунт в LibreLinkUp",
            "Регистрация — в официальном приложении Abbott. Сюда входите с тем же email и паролем",
        ) {
            FilledTonalButton(onClick = { openLibreLinkUp(ctx) }) { Text("Открыть") }
        }
        GroupDivider()
        SettingRow(
            "2. Примите приглашение",
            "Человек с сенсором в приложении LibreLink: Меню → Подключённые приложения → LibreLinkUp → Добавить, " +
                "указывает ваш email. Приглашение принимается в LibreLinkUp",
        )
    }
}

@Composable
private fun SetupStep(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app
    val prefs = app.prefs
    val last = app.repo.last.value

    // Разрешения меняются в системных окнах — перечитываем при возврате
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumed++
        onPauseOrDispose { }
    }
    val notificationsOn = remember(resumed) { ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled() }
    val unrestricted = remember(resumed) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumed++ }
    var monitoring by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, Modifier.size(56.dp), tint = InRangeColor)
        Spacer(Modifier.height(8.dp))
        Text("Вход выполнен", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        last?.let {
            Text(
                "Сейчас ${it.mmol.fmt()} ммоль/л ${it.arrow}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SettingsGroup("Чтобы тревоги приходили вовремя") {
        val ok: @Composable () -> Unit = { Icon(Icons.Default.CheckCircle, contentDescription = "Готово", tint = InRangeColor) }
        val warn: @Composable () -> Unit = { Icon(Icons.Default.Warning, contentDescription = null, tint = HighColor) }
        SettingRow(
            "Уведомления",
            if (notificationsOn) "Разрешены" else "Без них тревоги не придут",
            leading = if (notificationsOn) ok else warn,
        ) {
            if (!notificationsOn) {
                FilledTonalButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(ctx, null)
                    }
                }) { Text("Разрешить") }
            }
        }
        GroupDivider()
        SettingRow(
            "Работа в фоне",
            if (unrestricted) "Без ограничений батареи" else "Иначе система будет останавливать опрос",
            leading = if (unrestricted) ok else warn,
        ) {
            if (!unrestricted) {
                FilledTonalButton(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                    )
                }) { Text("Разрешить") }
            }
        }
        GroupDivider()
        SwitchSetting(
            "Фоновый мониторинг",
            monitoring,
            subtitle = "Опрос раз в минуту и тревоги, даже когда приложение закрыто",
            leading = { Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        ) { monitoring = it }
    }

    Text(
        "Пороги, мелодии и профили уведомлений можно настроить позже в «Настройках».",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Button(
        onClick = {
            prefs.monitoring = monitoring
            if (monitoring) MonitorService.start(ctx)
            prefs.onboarded = true
            onDone()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Icon(Icons.Default.Notifications, contentDescription = null, Modifier.size(18.dp))
        Text("  Начать")
    }
}

/** Открыть LibreLinkUp (российскую или глобальную версию), а если его нет — страницу в Google Play. */
private fun openLibreLinkUp(ctx: Context) {
    val pm = ctx.packageManager
    val packages = listOf("org.newyu.librelinkup.ru", "org.nativescript.LibreLinkUp")
    packages.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }?.let {
        ctx.startActivity(it)
        return
    }
    val store = packages.last()
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$store")))
    } catch (_: Exception) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$store")))
    }
}

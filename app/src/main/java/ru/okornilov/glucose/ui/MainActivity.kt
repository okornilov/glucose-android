package ru.okornilov.glucose.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ru.okornilov.glucose.alerts.AlarmPlayer
import ru.okornilov.glucose.app
import ru.okornilov.glucose.service.MonitorService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Сервис мог быть остановлен системой или принудительной остановкой — поднимаем снова
        if (app.prefs.monitoring) MonitorService.start(this)
        val openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
        setContent { GlucoseTheme { MainScreen(openSettings) } }
    }

    override fun onResume() {
        super.onResume()
        // Открыли приложение — тревогу увидели, громкий сигнал больше не нужен
        AlarmPlayer.stop()
    }

    companion object {
        /** Открыть сразу настройки (например, из уведомления или adb). */
        const val EXTRA_OPEN_SETTINGS = "open_settings"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(openSettings: Boolean = false) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(if (openSettings) 1 else 0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // 0 — главный экран, 1 — настройки, 2 — отчёт
    val inner = tab != 0
    // Системная «Назад» из настроек и отчёта возвращает на главный экран
    BackHandler(enabled = inner) { tab = 0 }
    Scaffold(
        topBar = {
            // Заголовка на главном экране нет — экономим место; в настройках только «назад»
            if (inner) {
                TopAppBar(
                    title = { Text(if (tab == 1) "Настройки" else "Отчёт", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { IconButton(onClick = { tab = 0 }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    expandedHeight = 48.dp,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                1 -> SettingsScreen()
                2 -> ReportScreen()
                else -> HomeScreen(onOpenSettings = { tab = 1 }, onOpenReport = { tab = 2 })
            }
        }
    }
}

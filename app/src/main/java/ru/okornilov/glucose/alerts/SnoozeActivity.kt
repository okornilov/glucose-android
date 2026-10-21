package ru.okornilov.glucose.alerts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import ru.okornilov.glucose.app
import ru.okornilov.glucose.ui.GlucoseTheme

/** Окно «Отложить…» из уведомления: все варианты отсрочки, которые не поместились в кнопки. */
class SnoozeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Нажали на уведомление — громкий сигнал больше не нужен, пока выбирают
        AlarmPlayer.stop()
        val kind = intent.getStringExtra(AlertActionReceiver.EXTRA_KIND) ?: run {
            finish()
            return
        }
        setContent { GlucoseTheme { SnoozeDialog(kind) } }
    }

    @Composable
    private fun SnoozeDialog(kind: String) {
        val options = listOf(15 to "15 минут", 30 to "30 минут", 45 to "45 минут", 60 to "1 час") +
            (0 to if (kind == AlertEngine.KIND_LEVEL) "До нормализации" else "До появления данных")
        AlertDialog(
            onDismissRequest = { finish() },
            title = { Text(if (kind == AlertEngine.KIND_LEVEL) "Отложить тревогу" else "Отложить «нет данных»") },
            text = {
                Column {
                    options.forEach { (minutes, label) ->
                        TextButton(onClick = { choose(kind, minutes) }) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { finish() }) { Text("Отмена") } },
        )
    }

    private fun choose(kind: String, minutes: Int) {
        val alerts = app.alerts
        if (minutes == 0) alerts.silence(kind) else alerts.snooze(kind, minutes)
        Notifications.cancel(this, kind)
        finish()
    }
}

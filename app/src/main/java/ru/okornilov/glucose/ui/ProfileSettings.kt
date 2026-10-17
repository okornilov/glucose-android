package ru.okornilov.glucose.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.okornilov.glucose.alerts.AlertType
import ru.okornilov.glucose.alerts.Notifications
import ru.okornilov.glucose.alerts.Vibration
import ru.okornilov.glucose.app
import ru.okornilov.glucose.data.Profile
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.profile.ProfileSwitcher

private enum class ProfileDialog { NEW, RENAME, DELETE }

/** Список профилей и редактор выбранного. Редактировать можно и неактивный профиль. */
@Composable
fun ProfilesSection() {
    val ctx = LocalContext.current
    val prefs = ctx.app.prefs
    prefs.changes.collectAsStateWithLifecycle().value
    val profiles = prefs.profiles()
    val active = prefs.active
    var editId by rememberSaveable { mutableStateOf(active.id) }
    val edit = prefs.profile(editId) ?: active
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }

    Section("Профили уведомлений")
    Text(
        "Профиль — пороги, какие уведомления слать, мелодии и вибрация. Быстро переключать: на главном экране, " +
            "кнопками в уведомлении в шторке, плиткой в быстрых настройках и нажатием на профиль в виджете.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        profiles.forEach { p ->
            FilterChip(
                selected = p.id == edit.id,
                onClick = { editId = p.id },
                label = { Text(if (p.id == active.id) "✓ ${p.name}" else p.name) },
            )
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (edit.id != active.id) {
            Button(onClick = { ProfileSwitcher.activate(ctx, edit.id) }) { Text("Включить") }
        }
        OutlinedButton(onClick = { dialog = ProfileDialog.NEW }) { Text("Новый") }
        OutlinedButton(onClick = { dialog = ProfileDialog.RENAME }) { Text("Переименовать") }
        if (profiles.size > 1) OutlinedButton(onClick = { dialog = ProfileDialog.DELETE }) { Text("Удалить") }
    }

    when (dialog) {
        ProfileDialog.NEW -> NameDialog("Новый профиль", "Профиль ${profiles.size + 1}", onDismiss = { dialog = null }) { name ->
            val created = prefs.addProfile(name, base = edit)
            Notifications.syncChannels(ctx)
            ProfileSwitcher.refresh(ctx)
            editId = created.id
        }
        ProfileDialog.RENAME -> NameDialog("Переименовать профиль", edit.name, onDismiss = { dialog = null }) { name ->
            prefs.updateProfile(edit.id) { it.copy(name = name) }
            Notifications.syncChannels(ctx)
            ProfileSwitcher.refresh(ctx)
        }
        ProfileDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Удалить профиль «${edit.name}»?") },
            text = { Text(if (edit.id == active.id) "Он сейчас включён — включится другой профиль." else "Его настройки будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    prefs.deleteProfile(edit.id)
                    Notifications.syncChannels(ctx)
                    ProfileSwitcher.refresh(ctx)
                    editId = prefs.active.id
                    dialog = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
        )
        null -> Unit
    }

    // key — чтобы поля ввода заново заполнились при выборе другого профиля
    key(edit.id) { ProfileEditor(edit) }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Название") }) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name.trim())
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ProfileEditor(p: Profile) {
    val ctx = LocalContext.current
    val prefs = ctx.app.prefs
    val update: ((Profile) -> Profile) -> Unit = { change -> prefs.updateProfile(p.id, change) }
    val current = { prefs.profile(p.id) ?: p }

    Text("Пороги, ммоль/л", style = MaterialTheme.typography.titleSmall)
    NumberField(
        label = "Низкий сахар: значение ≤",
        initial = p.low.fmt(),
        hint = "от 2 до 8 и меньше высокого порога",
        parse = { it.replace(',', '.').toDoubleOrNull() },
        valid = { it in 2.0..8.0 && it < current().high },
    ) { v -> update { it.copy(low = v) } }
    NumberField(
        label = "Высокий сахар: значение >",
        initial = p.high.fmt(),
        hint = "от 6 до 25 и больше низкого порога",
        parse = { it.replace(',', '.').toDoubleOrNull() },
        valid = { it in 6.0..25.0 && it > current().low },
    ) { v -> update { it.copy(high = v) } }

    Text("Какие уведомления слать", style = MaterialTheme.typography.titleSmall)
    SwitchRow("🔴 Низкий сахар", p.notifyLow) { v -> update { it.copy(notifyLow = v) } }
    SwitchRow("🟠 Высокий сахар", p.notifyHigh) { v -> update { it.copy(notifyHigh = v) } }
    SwitchRow("⚠️ Нет данных", p.notifyStale) { v -> update { it.copy(notifyStale = v) } }
    SwitchRow("✅ Сахар нормализовался", p.notifyRecovery) { v -> update { it.copy(notifyRecovery = v) } }
    NumberField(
        label = "Повторять, пока сахар вне диапазона, каждые N мин",
        initial = p.repeatMinutes.toString(),
        hint = "от 5 до 240",
        parse = { it.trim().toIntOrNull() },
        valid = { it in 5..240 },
    ) { v -> update { it.copy(repeatMinutes = v) } }
    NumberField(
        label = "Предупреждать, если нет данных дольше N мин",
        initial = p.staleMinutes.toString(),
        hint = "от 10 до 240",
        parse = { it.trim().toIntOrNull() },
        valid = { it in 10..240 },
    ) { v -> update { it.copy(staleMinutes = v) } }

    Text("Мелодия и вибрация", style = MaterialTheme.typography.titleSmall)
    Text(
        "«⚙» — важность, экран блокировки и «Не беспокоить» в настройках системы.",
        style = MaterialTheme.typography.bodySmall,
    )
    AlertType.entries.forEach { type -> AlertTypeRow(p, type) }
}

/** Мелодия и вибрация одного типа уведомлений в профиле. */
@Composable
private fun AlertTypeRow(p: Profile, type: AlertType) {
    val ctx = LocalContext.current
    var vibrationMenu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val picked = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        val value = when (picked) {
            null -> Profile.SILENT
            RingtoneManager.getDefaultUri(type.ringtoneType) -> ""
            else -> picked.toString()
        }
        Notifications.changeSound(ctx, p.id, type, value)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(type.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { openNotificationSettings(ctx, Notifications.channelId(p, type)) }) { Text("⚙") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "🎵 ${Notifications.soundTitle(ctx, p, type)}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = {
                picker.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type.ringtoneType)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "${type.title} · ${p.name}")
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(type.ringtoneType))
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Notifications.soundUri(p, type))
                )
            }) { Text("Мелодия") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "📳 ${Notifications.vibration(p, type).title}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            Box {
                TextButton(onClick = { vibrationMenu = true }) { Text("Вибрация") }
                DropdownMenu(expanded = vibrationMenu, onDismissRequest = { vibrationMenu = false }) {
                    Vibration.entries.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v.title) },
                            onClick = {
                                vibrationMenu = false
                                Notifications.changeVibration(ctx, p.id, type, v)
                                v.preview(ctx)
                            },
                        )
                    }
                }
            }
        }
    }
}

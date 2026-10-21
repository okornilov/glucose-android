package ru.okornilov.glucose.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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

    SettingsGroup("Профили уведомлений", Icons.Default.Person) {
        // Нажатие на строку — открыть профиль для настройки ниже; «Включить» — сделать активным
        profiles.forEachIndexed { i, p ->
            if (i > 0) GroupDivider()
            val selected = p.id == edit.id
            Box(
                Modifier.background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else Color.Transparent
                )
            ) {
                SettingRow(
                    p.name,
                    when {
                        p.id == active.id && selected -> "Включён · настраивается ниже"
                        p.id == active.id -> "Включён"
                        selected -> "Настраивается ниже"
                        else -> null
                    },
                    leading = {
                        if (p.id == active.id) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Включён", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Dot(MaterialTheme.colorScheme.outlineVariant)
                        }
                    },
                    onClick = { editId = p.id },
                ) {
                    if (p.id != active.id) {
                        TextButton(onClick = { ProfileSwitcher.activate(ctx, p.id) }) { Text("Включить") }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { dialog = ProfileDialog.NEW }) {
                Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Новый")
            }
            TextButton(onClick = { dialog = ProfileDialog.RENAME }) {
                Icon(Icons.Default.Edit, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Имя")
            }
            TextButton(onClick = { dialog = ProfileDialog.DELETE }, enabled = profiles.size > 1) {
                Icon(Icons.Default.Delete, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Удалить")
            }
        }
    }
    Text(
        "Переключать профили быстро: на главном экране и плиткой в быстрых настройках.",
        Modifier.padding(horizontal = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

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
    val nested: @Composable () -> Unit = {} // пустое место слева — вложенная настройка
    val minutes: (String) -> Int? = { it.trim().toIntOrNull() }

    SettingsGroup("Пороги · ${p.name}") {
        NumberSetting(
            "Низкий сахар",
            "Значение ≤ порога, от 2 до 8",
            p.low.fmt(),
            parse = { it.replace(',', '.').toDoubleOrNull() },
            valid = { it in 2.0..8.0 && it < current().high },
            leading = { Dot(LowColor) },
        ) { v -> update { it.copy(low = v) } }
        GroupDivider()
        NumberSetting(
            "Высокий сахар",
            "Значение > порога, от 6 до 25",
            p.high.fmt(),
            parse = { it.replace(',', '.').toDoubleOrNull() },
            valid = { it in 6.0..25.0 && it > current().low },
            leading = { Dot(HighColor) },
        ) { v -> update { it.copy(high = v) } }
    }

    SettingsGroup("Уведомления · ${p.name}") {
        SwitchSetting("Низкий сахар", p.notifyLow, leading = { Dot(LowColor) }) { v -> update { it.copy(notifyLow = v) } }
        if (p.notifyLow) {
            NumberSetting(
                "Повтор без реакции",
                "От 1 до 30 мин",
                p.lowRepeatMinutes.toString(), minutes, { it in 1..30 }, suffix = "мин", leading = nested,
            ) { v -> update { it.copy(lowRepeatMinutes = v) } }
            SwitchSetting(
                "Громкий сигнал",
                p.lowEscalate,
                subtitle = "Со второго напоминания — через будильник, слышно в беззвучном режиме",
                leading = nested,
            ) { v -> update { it.copy(lowEscalate = v) } }
        }
        GroupDivider()
        SwitchSetting(
            "Скоро низкий",
            p.notifyPredictLow,
            subtitle = "Прогноз: сахар быстро падает и скоро станет низким",
            leading = { Dot(LowColor.copy(alpha = 0.45f)) },
        ) { v -> update { it.copy(notifyPredictLow = v) } }
        if (p.notifyPredictLow) {
            NumberSetting(
                "Смотреть вперёд",
                "От 10 до 45 мин",
                p.predictMinutes.toString(), minutes, { it in 10..45 }, suffix = "мин", leading = nested,
            ) { v -> update { it.copy(predictMinutes = v) } }
        }
        GroupDivider()
        SwitchSetting("Высокий сахар", p.notifyHigh, leading = { Dot(HighColor) }) { v -> update { it.copy(notifyHigh = v) } }
        if (p.notifyHigh) {
            NumberSetting(
                "Повтор",
                "От 5 до 240 мин",
                p.repeatMinutes.toString(), minutes, { it in 5..240 }, suffix = "мин", leading = nested,
            ) { v -> update { it.copy(repeatMinutes = v) } }
        }
        GroupDivider()
        SwitchSetting("Нет данных", p.notifyStale, leading = { Dot(MaterialTheme.colorScheme.outline) }) { v -> update { it.copy(notifyStale = v) } }
        if (p.notifyStale) {
            NumberSetting(
                "Без замеров дольше",
                "От 10 до 240 мин",
                p.staleMinutes.toString(), minutes, { it in 10..240 }, suffix = "мин", leading = nested,
            ) { v -> update { it.copy(staleMinutes = v) } }
        }
        GroupDivider()
        SwitchSetting("Сахар нормализовался", p.notifyRecovery, leading = { Dot(InRangeColor) }) { v -> update { it.copy(notifyRecovery = v) } }
    }

    SettingsGroup("Мелодии и вибрация · ${p.name}") {
        AlertType.entries.forEachIndexed { i, type ->
            if (i > 0) GroupDivider()
            AlertTypeRow(p, type)
        }
    }
}

/** Мелодия и вибрация одного типа уведомлений в профиле. */
@Composable
private fun AlertTypeRow(p: Profile, type: AlertType) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
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
    val pickSound = {
        picker.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type.ringtoneType)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "${type.title} · ${p.name}")
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(type.ringtoneType))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Notifications.soundUri(p, type))
        )
    }

    // Нажатие на строку — выбрать мелодию; «⋮» — вибрация и системные настройки канала
    Box {
        SettingRow(
            type.title,
            "${Notifications.soundTitle(ctx, p, type)} · ${Notifications.vibration(p, type).title}",
            leading = { Dot(typeColor(type)) },
            onClick = { pickSound() },
        ) {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Вибрация и настройки", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.align(Alignment.TopEnd)) {
            DropdownMenuItem(
                text = { Text("Мелодия…") },
                onClick = {
                    menu = false
                    pickSound()
                },
            )
            HorizontalDivider()
            Text(
                "Вибрация",
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            val currentVibration = Notifications.vibration(p, type)
            Vibration.entries.forEach { v ->
                DropdownMenuItem(
                    text = { Text(v.title) },
                    leadingIcon = { if (v == currentVibration) Icon(Icons.Default.Check, contentDescription = "Выбрана") },
                    onClick = {
                        menu = false
                        Notifications.changeVibration(ctx, p.id, type, v)
                        v.preview(ctx)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Системные настройки…") },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = {
                    menu = false
                    openNotificationSettings(ctx, Notifications.channelId(p, type))
                },
            )
        }
    }
}

private fun typeColor(type: AlertType): Color = when (type) {
    AlertType.LOW -> LowColor
    AlertType.PREDICT_LOW -> LowColor.copy(alpha = 0.45f)
    AlertType.HIGH -> HighColor
    AlertType.STALE -> Color(0xFF9E9E9E)
    AlertType.RECOVERY -> InRangeColor
    AlertType.RESUMED -> InRangeColor.copy(alpha = 0.45f)
}

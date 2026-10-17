package ru.okornilov.glucose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Общие элементы экрана настроек: группы-карточки и строки «текст слева, управление справа»

/** Группа настроек: заголовок со значком и карточка со строками. */
@Composable
internal fun SettingsGroup(title: String, icon: ImageVector? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

/** Разделитель между строками группы — с отступом под текст. */
@Composable
internal fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 54.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/**
 * Строка настройки. leading — значок или цветная точка слева (место под него резервируется всегда,
 * чтобы строки были выровнены, в том числе вложенные). trailing — переключатель, поле или кнопка справа.
 */
@Composable
internal fun SettingRow(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leading?.invoke() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
internal fun SwitchSetting(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(title, subtitle, leading, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Число в узком поле справа: сохраняется, как только введено корректное значение. */
@Composable
internal fun <T : Any> NumberSetting(
    title: String,
    subtitle: String,
    initial: String,
    parse: (String) -> T?,
    valid: (T) -> Boolean,
    suffix: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onValid: (T) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val ok = parse(text)?.let(valid) ?: false
    SettingRow(title, subtitle, leading) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                parse(input)?.takeIf(valid)?.let(onValid)
            },
            isError = !ok,
            singleLine = true,
            suffix = suffix?.let { { Text(" $it") } },
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.width(if (suffix != null) 112.dp else 84.dp),
        )
    }
}

/** Цветная точка — обозначение типа уведомления или порога. */
@Composable
internal fun Dot(color: Color) {
    Box(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color),
    )
}

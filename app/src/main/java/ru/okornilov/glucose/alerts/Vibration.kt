package ru.okornilov.glucose.alerts

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Режимы вибрации уведомлений. pattern = null у DEFAULT — системная вибрация. */
enum class Vibration(val key: String, val title: String, val pattern: LongArray?) {
    OFF("off", "Без вибрации", null),
    SHORT("short", "Короткая", longArrayOf(0, 250)),
    DEFAULT("default", "Стандартная", null),
    LONG("long", "Длинная", longArrayOf(0, 1000, 500, 1000)),
    PULSE("pulse", "Пульс", longArrayOf(0, 150, 100, 150, 600, 150, 100, 150, 600, 150, 100, 150)),
    ALARM("alarm", "Тревога", longArrayOf(0, 800, 400, 800, 400, 800, 400, 800));

    /** Проиграть образец при выборе режима. */
    fun preview(ctx: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java)
        }
        when {
            this == OFF -> Unit
            pattern == null -> vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            else -> vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    companion object {
        fun of(key: String?): Vibration? = entries.find { it.key == key }
    }
}

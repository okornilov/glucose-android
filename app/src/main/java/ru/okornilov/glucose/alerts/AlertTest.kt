package ru.okornilov.glucose.alerts

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import ru.okornilov.glucose.app

/** Проверка тревог из настроек: тестовое уведомление на настоящих каналах активного профиля и диагностика. */
enum class AlertTest(val title: String, val type: AlertType, val loud: Boolean = false) {
    LOW("Низкий сахар", AlertType.LOW),
    LOW_LOUD("Низкий — громкий сигнал", AlertType.LOW, loud = true),
    HIGH("Высокий сахар", AlertType.HIGH),
    PREDICT("Скоро низкий", AlertType.PREDICT_LOW),
    STALE("Нет данных", AlertType.STALE);

    companion object {
        /**
         * Что может помешать тревоге прозвучать. Пустой список — всё в порядке.
         * Проверяются каналы активного профиля, «Не беспокоить», режим звонка и громкость будильника.
         */
        fun problems(ctx: Context): List<String> {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val audio = ctx.getSystemService(AudioManager::class.java)
            val profile = ctx.app.prefs.active
            val list = mutableListOf<String>()
            if (!nm.areNotificationsEnabled()) list += "Уведомления приложения выключены"
            listOf(AlertType.LOW, AlertType.HIGH, AlertType.STALE).forEach { type ->
                val ch = nm.getNotificationChannel(Notifications.channelId(profile, type)) ?: return@forEach
                when {
                    ch.importance == NotificationManager.IMPORTANCE_NONE -> list += "Канал «${type.title}» выключен в системе"
                    ch.importance < NotificationManager.IMPORTANCE_DEFAULT -> list += "Канал «${type.title}» без звука"
                }
            }
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                val low = nm.getNotificationChannel(Notifications.channelId(profile, AlertType.LOW))
                list += if (low?.canBypassDnd() == true) {
                    "Включён «Не беспокоить» — низкий сахар его обходит, остальные тревоги будут тихими"
                } else {
                    "Включён «Не беспокоить» — тревоги будут без звука; громкий сигнал прозвучит, если в режиме разрешены будильники"
                }
            }
            if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                list += "Телефон в беззвучном режиме или на вибрации — громкий сигнал при низком всё равно прозвучит"
            }
            if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                list += "Громкость будильника на нуле — громкий сигнал будет не слышен"
            }
            return list
        }
    }
}

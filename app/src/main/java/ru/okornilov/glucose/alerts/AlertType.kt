package ru.okornilov.glucose.alerts

import android.app.NotificationManager
import android.media.RingtoneManager

/** Типы уведомлений: у каждого в каждом профиле свой канал, мелодия и вибрация. */
enum class AlertType(
    val key: String,
    val title: String,
    val importance: Int,
    /** Какой системный звук по умолчанию и какой список показывать в выборе мелодии. */
    val ringtoneType: Int,
    val defaultVibration: Vibration,
) {
    LOW("low", "Низкий сахар", NotificationManager.IMPORTANCE_HIGH, RingtoneManager.TYPE_ALARM, Vibration.ALARM),
    HIGH("high", "Высокий сахар", NotificationManager.IMPORTANCE_HIGH, RingtoneManager.TYPE_NOTIFICATION, Vibration.DEFAULT),
    STALE("stale", "Нет данных", NotificationManager.IMPORTANCE_HIGH, RingtoneManager.TYPE_NOTIFICATION, Vibration.DEFAULT),
    RECOVERY("recovery", "Сахар в норме", NotificationManager.IMPORTANCE_DEFAULT, RingtoneManager.TYPE_NOTIFICATION, Vibration.DEFAULT),
    RESUMED("resumed", "Данные снова поступают", NotificationManager.IMPORTANCE_DEFAULT, RingtoneManager.TYPE_NOTIFICATION, Vibration.DEFAULT),
}

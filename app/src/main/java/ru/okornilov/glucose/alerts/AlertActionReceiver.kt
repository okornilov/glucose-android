package ru.okornilov.glucose.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.okornilov.glucose.app

/** Кнопки под уведомлением: «отложить» и «до нормализации»; смахивание — выключить громкий сигнал. */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmPlayer.stop()
        if (intent.action == ACTION_STOP_SOUND) {
            // «Остановить» под тестовой тревогой — убрать и само уведомление
            Notifications.cancelTest(context)
            return
        }
        if (intent.action == ACTION_DISABLE_PREDICT) {
            // Выключить прогнозы в активном профиле; включить снова — в настройках профиля
            val prefs = context.app.prefs
            prefs.updateProfile(prefs.active.id) { it.copy(notifyPredictLow = false) }
            Notifications.cancel(context, AlertEngine.KIND_PREDICT)
            return
        }
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val alerts = context.app.alerts
        when (intent.action) {
            ACTION_SNOOZE -> alerts.snooze(kind, intent.getIntExtra(EXTRA_MINUTES, 30))
            ACTION_SILENCE -> alerts.silence(kind)
        }
        Notifications.cancel(context, kind)
    }

    companion object {
        const val ACTION_SNOOZE = "ru.okornilov.glucose.SNOOZE"
        const val ACTION_SILENCE = "ru.okornilov.glucose.SILENCE"
        const val ACTION_STOP_SOUND = "ru.okornilov.glucose.STOP_SOUND"
        const val ACTION_DISABLE_PREDICT = "ru.okornilov.glucose.DISABLE_PREDICT"
        const val EXTRA_KIND = "kind"
        const val EXTRA_MINUTES = "minutes"
    }
}

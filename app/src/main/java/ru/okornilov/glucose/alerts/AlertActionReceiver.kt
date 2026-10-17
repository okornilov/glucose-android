package ru.okornilov.glucose.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.okornilov.glucose.app

/** Кнопки под уведомлением: «отложить» и «до нормализации». */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
        const val EXTRA_KIND = "kind"
        const val EXTRA_MINUTES = "minutes"
    }
}

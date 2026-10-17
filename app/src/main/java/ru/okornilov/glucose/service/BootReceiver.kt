package ru.okornilov.glucose.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ru.okornilov.glucose.app

/** Возобновляет мониторинг после перезагрузки телефона и обновления приложения. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.app.prefs.monitoring) return
        try {
            MonitorService.start(context)
        } catch (e: IllegalStateException) {
            // Android может запретить старт из фона; сервис поднимется при следующем открытии приложения
            Log.w("BootReceiver", "Не удалось запустить мониторинг: $e")
        }
    }
}

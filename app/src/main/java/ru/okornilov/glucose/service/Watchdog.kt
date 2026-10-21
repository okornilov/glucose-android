package ru.okornilov.glucose.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.okornilov.glucose.alerts.Notifications
import ru.okornilov.glucose.app
import java.util.concurrent.TimeUnit

/**
 * Сторож мониторинга. Независимо от службы (через системный планировщик) раз в 15 минут проверяет,
 * идёт ли опрос. Если опроса давно не было — перезапускает службу и предупреждает, что мониторинг стоял:
 * пока служба остановлена, не работают и тревоги, и «нет данных».
 */
class Watchdog(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.app.prefs
        if (!prefs.monitoring) return Result.success()
        val last = ctx.app.pollLog.lastPollAt
        val now = System.currentTimeMillis()
        if (last != 0L && now - last < STALL_MS) return Result.success()

        val minutes = if (last == 0L) null else (now - last) / 60_000
        val restarted = try {
            MonitorService.start(ctx)
            true
        } catch (e: Exception) {
            // Android может запретить запуск службы из фона — тогда поможет только открыть приложение
            Log.w("Watchdog", "Не удалось перезапустить мониторинг: $e")
            false
        }
        // Только что выключали и включали мониторинг или поставили приложение — без журнала не шумим
        if (minutes != null) Notifications.watchdog(ctx, minutes, restarted)
        return Result.success()
    }

    companion object {
        /** Опроса не было дольше — считаем, что служба остановлена. */
        private const val STALL_MS = 10 * 60_000L
        private const val NAME = "monitor-watchdog"

        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<Watchdog>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}

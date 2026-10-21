package ru.okornilov.glucose.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.okornilov.glucose.alerts.Notifications
import ru.okornilov.glucose.app

/**
 * Фоновый мониторинг. Сервис держит процесс живым (постоянное уведомление), а будит его раз в минуту
 * точный будильник setExactAndAllowWhileIdle — он срабатывает и в Doze, если приложению
 * разрешена работа без оптимизации батареи.
 */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            Notifications.ID_ONGOING,
            Notifications.ongoing(this, app.repo.last.value, app.repo.error.value),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (!app.prefs.monitoring) {
            stopSelf()
            return START_NOT_STICKY
        }
        poll()
        return START_STICKY
    }

    private fun poll() {
        if (job?.isActive == true) return
        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "glucose:poll")
            .apply { acquire(60_000L) }
        app.pollLog.onPoll()
        job = scope.launch {
            try {
                app.repo.refresh()
            } finally {
                Notifications.notify(
                    this@MonitorService,
                    Notifications.ID_ONGOING,
                    Notifications.ongoing(this@MonitorService, app.repo.last.value, app.repo.error.value),
                )
                if (app.prefs.monitoring) scheduleNext(this@MonitorService)
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        cancelAlarm(this)
        super.onDestroy()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 60_000L
        private const val ACTION_POLL = "ru.okornilov.glucose.POLL"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.app.pollLog.reset()
            cancelAlarm(ctx)
            ctx.stopService(Intent(ctx, MonitorService::class.java))
        }

        private fun pollIntent(ctx: Context): PendingIntent = PendingIntent.getForegroundService(
            ctx, 1,
            Intent(ctx, MonitorService::class.java).setAction(ACTION_POLL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun scheduleNext(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val at = SystemClock.elapsedRealtime() + POLL_INTERVAL_MS
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pollIntent(ctx))
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pollIntent(ctx))
            }
        }

        private fun cancelAlarm(ctx: Context) {
            ctx.getSystemService(AlarmManager::class.java).cancel(pollIntent(ctx))
        }
    }
}

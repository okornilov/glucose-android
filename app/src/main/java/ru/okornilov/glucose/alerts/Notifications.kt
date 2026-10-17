package ru.okornilov.glucose.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import ru.okornilov.glucose.R
import ru.okornilov.glucose.app
import ru.okornilov.glucose.data.Freshness
import ru.okornilov.glucose.data.Profile
import ru.okornilov.glucose.data.Reading
import ru.okornilov.glucose.data.ago
import ru.okornilov.glucose.data.describe
import ru.okornilov.glucose.data.fmt
import ru.okornilov.glucose.data.freshness
import ru.okornilov.glucose.profile.ProfileReceiver
import ru.okornilov.glucose.ui.MainActivity

object Notifications {
    private const val CH_MONITOR = "monitor"

    const val ID_ONGOING = 1
    private const val ID_LEVEL = 10
    private const val ID_STALE = 11
    private const val ID_RECOVERY = 12
    private const val ID_RESUMED = 13

    /** Канал типа уведомления в активном профиле. */
    fun channelId(ctx: Context, type: AlertType): String = channelId(ctx.app.prefs.active, type)

    fun channelId(profile: Profile, type: AlertType): String =
        // ch2 — схема после исправления вибрации: каналы ch1 были созданы с выключенной «стандартной» вибрацией
        "ch2_${profile.id}_${type.key}_${profile.channelVersion(type.key)}"

    /**
     * Приводит каналы в соответствие с профилями: у каждого профиля своя группа и по каналу на тип.
     * Существующие каналы не трогает (в них могут быть настройки пользователя), лишние удаляет.
     */
    fun syncChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val profiles = ctx.app.prefs.profiles()
        nm.createNotificationChannelGroups(profiles.map { NotificationChannelGroup(it.id, "Профиль «${it.name}»") })
        val channels = listOf(
            NotificationChannel(CH_MONITOR, "Текущее значение", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        ) + profiles.flatMap { p -> AlertType.entries.map { buildChannel(p, it, copyFrom = null) } }
        nm.createNotificationChannels(channels)
        val actual = channels.map { it.id }.toSet()
        nm.notificationChannels.filter { it.id !in actual }.forEach { nm.deleteNotificationChannel(it.id) }
        val groups = profiles.map { it.id }.toSet()
        nm.notificationChannelGroups.filter { it.id !in groups }.forEach { nm.deleteNotificationChannelGroup(it.id) }
    }

    /** value: "" — по умолчанию, [Profile.SILENT] — без звука, иначе URI мелодии. */
    fun changeSound(ctx: Context, profileId: String, type: AlertType, value: String) =
        changeChannel(ctx, profileId, type) { it.copy(sounds = it.sounds + (type.key to value)) }

    fun changeVibration(ctx: Context, profileId: String, type: AlertType, vibration: Vibration) =
        changeChannel(ctx, profileId, type) { it.copy(vibrations = it.vibrations + (type.key to vibration.key)) }

    /** Звук и вибрацию канала Android менять не даёт — создаём канал новой версии, перенося прочие настройки. */
    private fun changeChannel(ctx: Context, profileId: String, type: AlertType, change: (Profile) -> Profile) {
        val prefs = ctx.app.prefs
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val before = prefs.profile(profileId) ?: return
        val old = nm.getNotificationChannel(channelId(before, type))
        prefs.updateProfile(profileId) {
            val changed = change(it)
            changed.copy(channelVersions = changed.channelVersions + (type.key to before.channelVersion(type.key) + 1))
        }
        val after = prefs.profile(profileId) ?: return
        nm.createNotificationChannel(buildChannel(after, type, copyFrom = old))
        old?.let { nm.deleteNotificationChannel(it.id) }
    }

    fun soundUri(profile: Profile, type: AlertType): Uri? = when (val s = profile.sound(type.key)) {
        "" -> RingtoneManager.getDefaultUri(type.ringtoneType)
        Profile.SILENT -> null
        else -> Uri.parse(s)
    }

    fun soundTitle(ctx: Context, profile: Profile, type: AlertType): String = when (val s = profile.sound(type.key)) {
        "" -> "По умолчанию"
        Profile.SILENT -> "Без звука"
        else -> try {
            RingtoneManager.getRingtone(ctx, Uri.parse(s))?.getTitle(ctx) ?: "Выбранная мелодия"
        } catch (e: Exception) {
            "Выбранная мелодия"
        }
    }

    fun vibration(profile: Profile, type: AlertType): Vibration =
        Vibration.of(profile.vibrations[type.key]) ?: type.defaultVibration

    private fun buildChannel(profile: Profile, type: AlertType, copyFrom: NotificationChannel?): NotificationChannel {
        val attrs = AudioAttributes.Builder()
            // Низкий сахар звучит через поток будильника — громче и слышен при выключенном звуке уведомлений
            .setUsage(if (type == AlertType.LOW) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val vibration = vibration(profile, type)
        return NotificationChannel(channelId(profile, type), type.title, copyFrom?.importance ?: type.importance).apply {
            group = profile.id
            setSound(soundUri(profile, type), attrs)
            // Порядок важен: setVibrationPattern(null) сам выключает вибрацию, поэтому enableVibration — после
            vibrationPattern = vibration.pattern
            enableVibration(vibration != Vibration.OFF)
            // Переносим то, что пользователь мог настроить в системе для прежней версии канала
            if (copyFrom != null) {
                lockscreenVisibility = copyFrom.lockscreenVisibility
                setBypassDnd(copyFrom.canBypassDnd())
                setShowBadge(copyFrom.canShowBadge())
            }
        }
    }

    /** Постоянное уведомление фонового сервиса; в строке состояния — само значение вместо иконки. */
    fun ongoing(ctx: Context, last: Reading?, error: String?): Notification {
        val b = Notification.Builder(ctx, CH_MONITOR)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openApp(ctx))
        val prefs = ctx.app.prefs
        val profile = prefs.active
        if (last != null) {
            b.setSmallIcon(Icon.createWithBitmap(valueBitmap(last.mmol.fmt())))
                .setContentTitle("${last.mmol.fmt()} ммоль/л ${last.arrow}")
                .setContentText(
                    when (freshness(last, error)) {
                        Freshness.NO_SERVER -> "⚠ Нет связи с сервером · замер ${ago(last.ts)}"
                        Freshness.NO_NEW_DATA -> "⚠ Новых данных нет · замер ${ago(last.ts)}"
                        Freshness.FRESH -> "Замер ${ago(last.ts)} · профиль «${profile.name}»"
                    }
                )
        } else {
            b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Нет данных")
                .setContentText(error ?: "Ожидание данных LibreView · профиль «${profile.name}»")
        }
        // Быстрое переключение: кнопки остальных профилей (в уведомлении помещается три)
        prefs.profiles().filter { it.id != profile.id }.take(3).forEachIndexed { i, p ->
            b.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(ctx, R.drawable.ic_stat), p.name, ProfileReceiver.activateIntent(ctx, p.id, i)
                ).build()
            )
        }
        return b.build()
    }

    fun level(ctx: Context, low: Boolean, r: Reading) {
        val n = Notification.Builder(ctx, channelId(ctx, if (low) AlertType.LOW else AlertType.HIGH))
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (low) "🔴 Низкий сахар" else "🟠 Высокий сахар")
            .setContentText(describe(r))
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .addAction(alertAction(ctx, AlertEngine.KIND_LEVEL, 30))
            .addAction(alertAction(ctx, AlertEngine.KIND_LEVEL, 60))
            .addAction(alertAction(ctx, AlertEngine.KIND_LEVEL, 0))
            .build()
        notify(ctx, ID_LEVEL, n)
    }

    fun stale(ctx: Context, minutes: Long, last: Reading?, error: String?) {
        val text = buildString {
            append(last?.let { "Последнее: ${describe(it)}" } ?: "Данных ещё не было")
            error?.let { append("\nОшибка: $it") }
        }
        val n = Notification.Builder(ctx, channelId(ctx, AlertType.STALE))
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("⚠️ Нет новых данных уже $minutes мин")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .addAction(alertAction(ctx, AlertEngine.KIND_STALE, 30))
            .addAction(alertAction(ctx, AlertEngine.KIND_STALE, 60))
            .addAction(alertAction(ctx, AlertEngine.KIND_STALE, 0))
            .build()
        notify(ctx, ID_STALE, n)
    }

    /** «Сахар в норме» (RECOVERY) или «Данные снова поступают» (RESUMED). */
    fun info(ctx: Context, type: AlertType, title: String, text: String) {
        val n = Notification.Builder(ctx, channelId(ctx, type))
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        notify(ctx, if (type == AlertType.RESUMED) ID_RESUMED else ID_RECOVERY, n)
    }

    fun cancel(ctx: Context, kind: String) {
        ctx.getSystemService(NotificationManager::class.java)
            .cancel(if (kind == AlertEngine.KIND_LEVEL) ID_LEVEL else ID_STALE)
    }

    fun notify(ctx: Context, id: Int, n: Notification) {
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (e: SecurityException) {
            // Например, выбранная мелодия больше недоступна
            Log.w("Notifications", "Не удалось показать уведомление: $e")
        }
    }

    /** minutes = 0 — «до нормализации» / «до появления данных», иначе отложить на minutes. */
    private fun alertAction(ctx: Context, kind: String, minutes: Int): Notification.Action {
        val title = when {
            minutes == 30 -> "🔕 30 мин"
            minutes == 60 -> "🔕 1 час"
            kind == AlertEngine.KIND_LEVEL -> "🔇 До нормализации"
            else -> "🔇 До появления данных"
        }
        val intent = Intent(ctx, AlertActionReceiver::class.java)
            .setAction(if (minutes == 0) AlertActionReceiver.ACTION_SILENCE else AlertActionReceiver.ACTION_SNOOZE)
            .putExtra(AlertActionReceiver.EXTRA_KIND, kind)
            .putExtra(AlertActionReceiver.EXTRA_MINUTES, minutes)
        val requestCode = (if (kind == AlertEngine.KIND_LEVEL) 100 else 200) + minutes
        val pi = PendingIntent.getBroadcast(
            ctx, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_stat), title, pi).build()
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun valueBitmap(text: String): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = size * 0.75f
        }
        while (paint.measureText(text) > size && paint.textSize > 10f) paint.textSize -= 2f
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2
        Canvas(bmp).drawText(text, size / 2f, y, paint)
        return bmp
    }
}

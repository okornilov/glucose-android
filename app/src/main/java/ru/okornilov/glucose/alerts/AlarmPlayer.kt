package ru.okornilov.glucose.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/**
 * Громкий сигнал при низком сахаре, на который не отреагировали: мелодия по кругу через поток будильника
 * (его не глушат беззвучный режим и «Не беспокоить» с разрешёнными будильниками) и вибрация.
 * Звучит до реакции, но не дольше минуты — следующий повтор тревоги запустит его снова.
 */
object AlarmPlayer {
    private const val MAX_MS = 60_000L
    private val PATTERN = longArrayOf(0, 800, 400, 800, 400, 800, 1200)

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** sound — мелодия профиля для низкого сахара; null — без звука (в профиле выбрано «без звука»), только вибрация. */
    @Synchronized
    fun start(ctx: Context, sound: Uri?, vibrate: Boolean) {
        stop()
        val app = ctx.applicationContext
        if (sound != null) {
            player = try {
                MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
                    try {
                        setDataSource(app, sound)
                    } catch (e: Exception) {
                        // Выбранная мелодия пропала — играем будильник по умолчанию
                        reset()
                        setAudioAttributes(attrs)
                        setDataSource(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                    }
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.w("AlarmPlayer", "Не удалось воспроизвести сигнал: $e")
                null
            }
        }
        if (vibrate) {
            vibrator = app.getSystemService(Vibrator::class.java)?.also {
                @Suppress("DEPRECATION")
                it.vibrate(VibrationEffect.createWaveform(PATTERN, 0), attrs)
            }
        }
        handler.postDelayed({ stop() }, MAX_MS)
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
    }
}

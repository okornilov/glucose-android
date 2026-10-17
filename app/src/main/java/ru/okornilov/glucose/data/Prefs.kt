package ru.okornilov.glucose.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import java.util.UUID

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _changes = MutableStateFlow(0)

    /** Растёт при каждом изменении настроек — чтобы экраны перерисовывались. */
    val changes: StateFlow<Int> = _changes.asStateFlow()
    private val bump: () -> Unit = { _changes.update { it + 1 } }

    // Общие настройки, не зависящие от профиля

    var email by sp.stringPref("email", "", bump)
    var region by sp.stringPref("region", "ru", bump)
    var password: String
        get() = sp.getString("password", null)?.let { Secret.decrypt(it) }.orEmpty()
        set(value) {
            sp.edit().putString("password", Secret.encrypt(value)).apply()
            bump()
        }

    var muteUntil by sp.longPref("mute_until", 0L, bump)

    /** Когда запущен текущий сенсор (epoch millis), 0 — неизвестно. Обновляется при каждом опросе. */
    var sensorStart by sp.longPref("sensor_start", 0L)

    /** Срок службы сенсора, дней: 14 у Libre 2/3, 15 у моделей Plus. */
    var sensorDays by sp.intPref("sensor_days", 14, bump)

    /** Напоминать о замене сенсора за сутки и за 2 часа. */
    var notifySensor by sp.boolPref("notify_sensor", true, bump)

    /** Утренняя сводка за ночь — включается в настройках. */
    var summaryEnabled by sp.boolPref("summary_enabled", false, bump)

    /** Время утренней сводки, минуты от полуночи; ночь — с 23:00 до этого времени. */
    var summaryMinute by sp.intPref("summary_minute", 7 * 60, bump)

    /** Постоянное уведомление со значением сахара; выключено — тихое «мониторинг работает» без значка. */
    var showOngoing by sp.boolPref("show_ongoing", true, bump)

    /** Live Update (Android 16+): сахар в «капсуле» строки состояния и на экране блокировки. */
    var liveUpdate by sp.boolPref("live_update", true, bump)
    var monitoring by sp.boolPref("monitoring", false, bump)

    /** Непрозрачность фона виджета, %: 0 — полностью прозрачный. */
    var widgetOpacity by sp.intPref("widget_opacity", 90, bump)

    fun muted(now: Long = System.currentTimeMillis()) = now < muteUntil

    // Профили уведомлений

    var activeProfileId by sp.stringPref("active_profile", Profile.DEFAULT_ID, bump)

    @Volatile
    private var cache: List<Profile>? = null

    @Synchronized
    fun profiles(): List<Profile> {
        cache?.let { return it }
        val raw = sp.getString("profiles", null)
        var list = if (raw.isNullOrEmpty()) {
            listOf(legacyProfile()).also { write(it) }
        } else {
            val array = JSONArray(raw)
            (0 until array.length()).map { Profile.fromJson(array.getJSONObject(it)) }
        }
        // Прогноз сначала был включён по умолчанию; его никто не включал сам — один раз выключаем во всех профилях
        if (!sp.getBoolean(PREDICT_OFF_MIGRATED, false)) {
            list = list.map { it.copy(notifyPredictLow = false) }
            write(list)
            sp.edit().putBoolean(PREDICT_OFF_MIGRATED, true).apply()
        }
        cache = list
        return list
    }

    val active: Profile
        get() = profiles().let { list -> list.find { it.id == activeProfileId } ?: list.first() }

    fun profile(id: String): Profile? = profiles().find { it.id == id }

    fun updateProfile(id: String, change: (Profile) -> Profile) {
        synchronized(this) { write(profiles().map { if (it.id == id) change(it) else it }) }
        bump()
    }

    fun addProfile(name: String, base: Profile): Profile {
        val profile = base.copy(id = "p" + UUID.randomUUID().toString().take(8), name = name, channelVersions = emptyMap())
        synchronized(this) { write(profiles() + profile) }
        bump()
        return profile
    }

    /** Последний профиль удалить нельзя. */
    fun deleteProfile(id: String) {
        val rest = profiles().filter { it.id != id }
        if (rest.isEmpty()) return
        synchronized(this) { write(rest) }
        if (activeProfileId == id) activeProfileId = rest.first().id
        bump()
    }

    // Настройки активного профиля — их читают правила уведомлений, главный экран и виджет

    val low: Double get() = active.low
    val high: Double get() = active.high
    val notifyLow: Boolean get() = active.notifyLow
    val notifyHigh: Boolean get() = active.notifyHigh
    val notifyStale: Boolean get() = active.notifyStale
    val notifyRecovery: Boolean get() = active.notifyRecovery
    val repeatMinutes: Int get() = active.repeatMinutes
    val staleMinutes: Int get() = active.staleMinutes
    val notifyPredictLow: Boolean get() = active.notifyPredictLow
    val predictMinutes: Int get() = active.predictMinutes
    val lowRepeatMinutes: Int get() = active.lowRepeatMinutes
    val lowEscalate: Boolean get() = active.lowEscalate

    private fun write(list: List<Profile>) {
        cache = list
        sp.edit().putString("profiles", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    /** Первый профиль — из настроек версии без профилей, если они были. */
    private fun legacyProfile(): Profile {
        val d = Profile(id = Profile.DEFAULT_ID, name = "Обычный")
        return d.copy(
            low = sp.getString("low", null)?.toDoubleOrNull() ?: d.low,
            high = sp.getString("high", null)?.toDoubleOrNull() ?: d.high,
            notifyLow = sp.getBoolean("notify_low", d.notifyLow),
            notifyHigh = sp.getBoolean("notify_high", d.notifyHigh),
            notifyStale = sp.getBoolean("notify_stale", d.notifyStale),
            notifyRecovery = sp.getBoolean("notify_recovery", d.notifyRecovery),
            repeatMinutes = sp.getInt("repeat_minutes", d.repeatMinutes),
            staleMinutes = sp.getInt("stale_minutes", d.staleMinutes),
            sounds = LEGACY_TYPES.mapNotNull { t -> sp.getString("sound_$t", null)?.takeIf { it.isNotEmpty() }?.let { t to it } }.toMap(),
        )
    }

    companion object {
        private const val PREDICT_OFF_MIGRATED = "predict_off_migrated"
        const val STALE_REPEAT_MINUTES = 60
        private val LEGACY_TYPES = listOf("low", "high", "stale", "recovery", "resumed")
    }
}

package ru.okornilov.glucose.data

import org.json.JSONObject

/** Профиль уведомлений: пороги, какие уведомления слать, мелодии и вибрация по типам. */
data class Profile(
    val id: String,
    val name: String,
    /** Низкий сахар — значение <= low, высокий — значение > high. */
    val low: Double = 3.8,
    val high: Double = 12.0,
    val notifyLow: Boolean = true,
    val notifyHigh: Boolean = true,
    val notifyStale: Boolean = true,
    val notifyRecovery: Boolean = true,
    /** Как часто повторять уведомление, пока сахар вне диапазона. */
    val repeatMinutes: Int = 20,
    /** Через сколько минут без новых данных предупреждать. */
    val staleMinutes: Int = 30,
    /** Тип уведомления (AlertType.key) → "" — мелодия по умолчанию, [SILENT] — без звука, иначе URI. */
    val sounds: Map<String, String> = emptyMap(),
    /** Тип уведомления → Vibration.key; нет записи — вибрация по умолчанию для типа. */
    val vibrations: Map<String, String> = emptyMap(),
    /** Звук и вибрация канала после создания не меняются, поэтому при их смене канал пересоздаётся с новой версией. */
    val channelVersions: Map<String, Int> = emptyMap(),
) {
    fun sound(type: String): String = sounds[type].orEmpty()

    fun channelVersion(type: String): Int = channelVersions[type] ?: 1

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("low", low)
        .put("high", high)
        .put("notifyLow", notifyLow)
        .put("notifyHigh", notifyHigh)
        .put("notifyStale", notifyStale)
        .put("notifyRecovery", notifyRecovery)
        .put("repeatMinutes", repeatMinutes)
        .put("staleMinutes", staleMinutes)
        .put("sounds", JSONObject(sounds))
        .put("vibrations", JSONObject(vibrations))
        .put("channelVersions", JSONObject(channelVersions))

    companion object {
        const val SILENT = "silent"
        const val DEFAULT_ID = "default"

        fun fromJson(o: JSONObject): Profile {
            val d = Profile(id = o.getString("id"), name = o.getString("name"))
            return d.copy(
                low = o.optDouble("low", d.low),
                high = o.optDouble("high", d.high),
                notifyLow = o.optBoolean("notifyLow", d.notifyLow),
                notifyHigh = o.optBoolean("notifyHigh", d.notifyHigh),
                notifyStale = o.optBoolean("notifyStale", d.notifyStale),
                notifyRecovery = o.optBoolean("notifyRecovery", d.notifyRecovery),
                repeatMinutes = o.optInt("repeatMinutes", d.repeatMinutes),
                staleMinutes = o.optInt("staleMinutes", d.staleMinutes),
                sounds = o.optJSONObject("sounds").toMap { getString(it) },
                vibrations = o.optJSONObject("vibrations").toMap { getString(it) },
                channelVersions = o.optJSONObject("channelVersions").toMap { getInt(it) },
            )
        }

        private fun <T> JSONObject?.toMap(value: JSONObject.(String) -> T): Map<String, T> =
            this?.keys()?.asSequence()?.associateWith { value(it) }.orEmpty()
    }
}

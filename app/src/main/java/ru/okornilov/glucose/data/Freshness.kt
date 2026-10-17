package ru.okornilov.glucose.data

/** Значение считается устаревшим, если запрос к серверу не удался или замеру больше стольких минут. */
const val FRESH_MINUTES = 5

/** Почему текущее значение может быть не на сейчас. */
enum class Freshness {
    FRESH,

    /** Запрос к LibreView не удался: нет сети, сервер недоступен или не пускает. */
    NO_SERVER,

    /** Сервер ответил, но нового замера нет: сенсор или телефон с LibreLink не передают данные. */
    NO_NEW_DATA,
}

fun freshness(last: Reading?, error: String?, now: Long = System.currentTimeMillis()): Freshness = when {
    error != null -> Freshness.NO_SERVER
    last == null || (now - last.ts) / 60_000 >= FRESH_MINUTES -> Freshness.NO_NEW_DATA
    else -> Freshness.FRESH
}

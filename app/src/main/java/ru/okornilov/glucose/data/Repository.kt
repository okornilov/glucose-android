package ru.okornilov.glucose.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.okornilov.glucose.alerts.AlertEngine

class Repository(
    private val prefs: Prefs,
    private val db: ReadingsDb,
    private val alerts: AlertEngine,
    /** После каждого опроса — например, обновить виджет. */
    private val onRefreshed: () -> Unit = {},
) {
    private val _last = MutableStateFlow(db.latest())
    val last: StateFlow<Reading?> = _last.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Растёт после каждой записи в базу — чтобы график перечитал данные. */
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    private val mutex = Mutex()
    private val startedAt = System.currentTimeMillis()
    private var client: LibreClient? = null
    private var clientKey: String? = null

    private fun client(): LibreClient {
        val key = listOf(prefs.email, prefs.password, prefs.region).joinToString("\u0000")
        if (key != clientKey || client == null) {
            client = LibreClient(prefs.email, prefs.password, prefs.region)
            clientKey = key
        }
        return client!!
    }

    suspend fun refresh(): Reading? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (prefs.email.isBlank() || prefs.password.isBlank()) {
                _error.value = "Укажите логин и пароль LibreLinkUp в настройках"
            } else {
                try {
                    val (current, history, sensorStart) = client().fetch()
                    _error.value = null
                    if (sensorStart != null) prefs.sensorStart = sensorStart
                    db.insert(history + listOfNotNull(current))
                    db.deleteBefore(now - KEEP_DAYS * 24 * 3600_000L)
                    _dataVersion.update { it + 1 }
                    val prev = _last.value
                    if (current != null && (prev == null || current.ts > prev.ts)) {
                        _last.value = current
                        if (prefs.monitoring) alerts.onReading(current, now, db.between(now - RECENT_MS, now + 60_000))
                    }
                } catch (e: Exception) {
                    _error.value = e.message ?: e.javaClass.simpleName
                }
            }
            if (prefs.monitoring) alerts.checkStale(_last.value, startedAt, _error.value, now)
            onRefreshed()
            _last.value
        }
    }

    /** Выход из аккаунта: забыть замеры и сенсор, чтобы данные другого аккаунта не смешались с этими. */
    suspend fun reset() = mutex.withLock {
        withContext(Dispatchers.IO) {
            db.deleteBefore(Long.MAX_VALUE)
            prefs.sensorStart = 0L
            client = null
            clientKey = null
            _last.value = null
            _error.value = null
            _dataVersion.update { it + 1 }
            onRefreshed()
        }
    }

    companion object {
        private const val KEEP_DAYS = 90
        private const val RECENT_MS = 20 * 60_000L
    }
}

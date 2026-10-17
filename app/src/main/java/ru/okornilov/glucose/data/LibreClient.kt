package ru.okornilov.glucose.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class LibreException(message: String) : Exception(message)

/** Клиент неофициального API LibreLinkUp (облако LibreView). Блокирующий — вызывать с Dispatchers.IO. */
class LibreClient(
    private val email: String,
    private val password: String,
    region: String?,
    private val version: String = "4.16.0",
) {
    private var base = baseUrl(region)
    private var token: String? = null
    private var accountId: String? = null
    private var patientId: String? = null
    private var loginError: String? = null
    private var loginRetryAt = 0L

    @Synchronized
    fun fetch(): Pair<Reading?, List<Reading>> {
        val pid = patientId ?: run {
            val connections = get("/llu/connections") as JSONArray
            if (connections.length() == 0) {
                throw LibreException("Нет пациентов, поделившихся данными: примите приглашение в LibreLinkUp")
            }
            connections.getJSONObject(0).getString("patientId").also { patientId = it }
        }
        val data = get("/llu/connections/$pid/graph") as JSONObject
        val current = parse(data.optJSONObject("connection")?.optJSONObject("glucoseMeasurement"))
        val graph = data.optJSONArray("graphData") ?: JSONArray()
        val history = (0 until graph.length()).mapNotNull { parse(graph.optJSONObject(it)) }
        return current to history
    }

    private fun login() {
        // После отказа по логину/паролю не долбим LibreView каждую минуту — учётку могут заблокировать
        loginError?.let { if (System.currentTimeMillis() < loginRetryAt) throw LibreException(it) }
        try {
            doLogin()
            loginError = null
        } catch (e: LibreException) {
            val message = "${e.message} (следующая попытка входа через 15 мин)"
            loginError = message
            loginRetryAt = System.currentTimeMillis() + LOGIN_RETRY_MS
            throw LibreException(message)
        }
    }

    private fun doLogin() {
        repeat(3) {
            val credentials = JSONObject().put("email", email).put("password", password)
            val (code, body) = request("POST", "/llu/auth/login", credentials, auth = false)
            if (code !in 200..299) throw LibreException("LibreView: HTTP $code при входе")
            val data = body.optJSONObject("data")
            if (data != null && data.optBoolean("redirect")) {
                base = baseUrl(data.getString("region"))
                return@repeat
            }
            when (val status = body.optInt("status", -1)) {
                0 -> if (data != null && data.has("authTicket")) {
                    token = data.getJSONObject("authTicket").getString("token")
                    accountId = sha256(data.getJSONObject("user").getString("id"))
                    return
                }
                2 -> throw LibreException("LibreView: неверный логин или пароль (для России укажите регион ru)")
                4 -> throw LibreException("LibreView просит принять условия использования: войдите в приложение LibreLinkUp")
                else -> throw LibreException("Не удалось войти в LibreView: status=$status")
            }
            throw LibreException("Не удалось войти в LibreView: нет токена в ответе")
        }
        throw LibreException("LibreView: слишком много перенаправлений при входе")
    }

    private fun get(path: String): Any {
        for (attempt in 0..1) {
            if (token == null) login()
            val (code, body) = request("GET", path)
            if (code == 401 && attempt == 0) {
                token = null
                continue
            }
            if (code !in 200..299) throw LibreException("LibreView: HTTP $code")
            val status = body.optInt("status", -1)
            if (status != 0) throw LibreException("LibreView: status=$status")
            return body.get("data")
        }
        throw LibreException("LibreView: не удалось авторизоваться")
    }

    private fun request(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): Pair<Int, JSONObject> {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("product", "llu.android")
            conn.setRequestProperty("version", version)
            conn.setRequestProperty("accept", "application/json")
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("cache-control", "no-cache")
            if (auth) {
                conn.setRequestProperty("authorization", "Bearer $token")
                conn.setRequestProperty("account-id", accountId)
            }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                JSONObject()
            }
            return code to json
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val LOGIN_RETRY_MS = 15 * 60_000L

        // FactoryTimestamp приходит в UTC в формате "10/17/2026 1:23:45 PM"
        private val TS_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US)

        fun baseUrl(region: String?): String = when {
            region.isNullOrBlank() -> "https://api.libreview.io"
            // Российские учётные записи живут на отдельном сервере, глобальный на него не перенаправляет
            region.equals("ru", ignoreCase = true) -> "https://api.libreview.ru"
            else -> "https://api-${region.lowercase()}.libreview.io"
        }

        fun parse(m: JSONObject?): Reading? {
            if (m == null || !m.has("ValueInMgPerDl") || !m.has("FactoryTimestamp")) return null
            val ts = LocalDateTime.parse(m.getString("FactoryTimestamp"), TS_FORMAT)
                .toInstant(ZoneOffset.UTC).toEpochMilli()
            val trend = if (m.has("TrendArrow") && !m.isNull("TrendArrow")) m.getInt("TrendArrow") else null
            return Reading(ts, Math.round(m.getDouble("ValueInMgPerDl")).toInt(), trend)
        }

        private fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

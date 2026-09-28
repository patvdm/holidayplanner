package com.pat.app.holidayplanner.data.remote

import android.util.Base64
import android.util.Log
import com.pat.app.holidayplanner.BuildConfig
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

private const val TAG = "SheetsApi"
private const val TIMEOUT_MS = 30_000L

/** A picture is far more to send than a row, over whatever connection the phone happens to have. */
private const val UPLOAD_TIMEOUT_MS = 120_000L
private const val DRIVE_PICTURE_PATH = "googleusercontent.com/d/"

private const val BODY_PREVIEW_CHARS = 120

/** Once more is enough: a one-shot address that has already been spent answers on the next ask. */
private const val ATTEMPTS = 2

/** The backend the app talks to: BuildConfig values unless overridden in Settings → Advanced. */
data class BackendConfig(val url: String, val secret: String, val overridden: Boolean)

/** [worthRetrying] is set for the answers that are not really answers, such as a sign-in page. */
class SheetsException(
    message: String,
    cause: Throwable? = null,
    val worthRetrying: Boolean = false,
) : Exception(message, cause)

/**
 * HTTP client for the Apps Script web app. Every call is a JSON POST to the /exec URL.
 *
 * Apps Script answers a POST with a 302 to script.googleusercontent.com that must be fetched with
 * GET. OkHttp does exactly that when it follows redirects itself, so redirects are left to the
 * engine; Ktor's own redirect plugin would repeat the POST instead. That address is single use and
 * only answers to the cookies the redirect set, which is why the engine keeps a cookie jar;
 * without one it sometimes serves a Drive error page instead of the answer. Responses are always
 * HTTP 200, so the `ok` field in the body is what decides success.
 */
class SheetsApi(
    prefs: AppPreferences,
    private val client: HttpClient = defaultClient(),
) : SheetsBackend, ImageStore {

    val config: Flow<BackendConfig> = prefs.backendOverride.map { override ->
        BackendConfig(
            url = override.url.ifBlank { BuildConfig.SHEETS_URL },
            secret = override.secret.ifBlank { BuildConfig.SHEETS_SECRET },
            overridden = override.url.isNotBlank() || override.secret.isNotBlank(),
        )
    }

    override suspend fun ping(): Result<Long> = call("ping").map { it.long("serverTime") }

    override suspend fun pull(since: Long): Result<PullResult> =
        call("pull") { put("since", since) }.map { body ->
            PullResult(serverTime = body.long("serverTime"), changes = body.readChanges())
        }

    override suspend fun push(rows: SheetRows): Result<PushResult> =
        call("push") { putJsonObject("rows") { putRows(rows) } }.map { body ->
            PushResult(
                serverTime = body.long("serverTime"),
                accepted = body.long("accepted").toInt(),
                skipped = (body["skipped"] as? JsonArray).orEmpty().map { it.text() },
                errors = (body["errors"] as? JsonArray)?.size ?: 0,
            )
        }

    override suspend fun upload(bytes: ByteArray, mimeType: String, name: String): Result<StoredImage> =
        call("image", timeoutMs = UPLOAD_TIMEOUT_MS) {
            // Base64 because the body is JSON; the phone has already made the picture small enough.
            put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
            put("mimeType", mimeType)
            put("name", name)
        }.map { body ->
            StoredImage(url = body.str("url"), fileId = body.str("fileId"))
        }

    override suspend fun remove(url: String): Result<Unit> {
        val fileId = url.substringAfter(DRIVE_PICTURE_PATH, "").substringBefore('?')
        if (fileId.isEmpty()) return Result.success(Unit)
        return call("deleteImage") { put("fileId", fileId) }.map { }
    }

    /** Drive pictures are served from lh3.googleusercontent.com/d/<file id>. */
    override fun owns(url: String): Boolean = url.contains(DRIVE_PICTURE_PATH)

    private suspend fun call(
        action: String,
        timeoutMs: Long = TIMEOUT_MS,
        extra: JsonObjectBuilder.() -> Unit = {},
    ): Result<JsonObject> {
        val cfg = config.first()
        if (cfg.url.isBlank()) return Result.failure(SheetsException("Backend URL is not configured"))
        val payload = buildJsonObject {
            put("secret", cfg.secret)
            put("action", action)
            extra()
        }
        return runCatchingNetwork(action) { send(cfg.url, payload.toString(), timeoutMs) }
    }

    /** Sends the call, and asks again when what came back was not an answer at all. */
    private suspend fun send(url: String, body: String, timeoutMs: Long): JsonObject {
        var last: SheetsException? = null
        repeat(ATTEMPTS) {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
                timeout { requestTimeoutMillis = timeoutMs }
            }
            try {
                return parse(response.bodyAsText())
            } catch (e: SheetsException) {
                if (!e.worthRetrying) throw e
                Log.w(TAG, "asking again: ${e.message}")
                last = e
            }
        }
        throw last ?: SheetsException("No answer from the backend")
    }

    private inline fun runCatchingNetwork(action: String, block: () -> JsonObject): Result<JsonObject> = try {
        Result.success(block())
    } catch (e: IOException) {
        failure(action, e)
    } catch (e: SheetsException) {
        failure(action, e)
    } catch (e: IllegalStateException) {
        failure(action, e)
    } catch (e: IllegalArgumentException) {
        failure(action, e)
    }

    private fun failure(action: String, e: Exception): Result<JsonObject> {
        Log.w(TAG, "$action failed: ${e.message}")
        return Result.failure(e)
    }

    private fun parse(body: String): JsonObject {
        val element = try {
            json.parseToJsonElement(body)
        } catch (e: SerializationException) {
            // A sign-in or error page where JSON was expected: the address was spent, so ask again.
            throw SheetsException("Non-JSON response: ${body.take(BODY_PREVIEW_CHARS)}", e, worthRetrying = true)
        }
        val parsed = element as? JsonObject
        if (parsed == null || parsed["ok"]?.text() != "true") {
            val reason = parsed?.get("error")?.text() ?: body.take(BODY_PREVIEW_CHARS)
            throw SheetsException("Backend error: $reason")
        }
        return parsed
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Keeps the cookies of one redirect so the hop after it is allowed to read the answer. Apps Script
 * sets them on the 302 and the address they unlock is good for a single fetch, so there is nothing
 * here worth persisting between runs.
 */
private class HopCookieJar : CookieJar {
    private val jar = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        jar.removeAll { old -> cookies.any { it.name == old.name && it.domain == old.domain } }
        jar += cookies
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = jar.filter { it.matches(url) }
}

private fun defaultClient(): HttpClient = HttpClient(OkHttp) {
    followRedirects = false
    expectSuccess = false
    engine {
        config {
            followRedirects(true)
            followSslRedirects(true)
            cookieJar(HopCookieJar())
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = TIMEOUT_MS
        connectTimeoutMillis = TIMEOUT_MS
        socketTimeoutMillis = TIMEOUT_MS
    }
}

private fun JsonObjectBuilder.putRows(rows: SheetRows) {
    rows.forEach { (table, tableRows) ->
        put(table, JsonArray(tableRows.map { row -> JsonObject(row.mapValues { JsonPrimitive(it.value) }) }))
    }
}

/** Cells are read as text whatever JSON type they arrive as, so a stray number is not a failure. */
private fun JsonObject.readChanges(): SheetRows {
    val changes = this["changes"] as? JsonObject ?: return emptyMap()
    return changes.mapNotNull { (table, element) ->
        val rows = (element as? JsonArray) ?: return@mapNotNull null
        table to rows.mapNotNull { row ->
            (row as? JsonObject)?.mapValues { (_, cell) -> cell.text() }
        }
    }.toMap()
}

private fun JsonObject.str(key: String): String = this[key]?.text().orEmpty()

private fun JsonObject.long(key: String): Long = this[key]?.text()?.toLongOrNull() ?: 0L

private fun JsonElement.text(): String = (this as? JsonPrimitive)?.content.orEmpty()

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

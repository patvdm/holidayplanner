package com.pat.app.holidayplanner.data.remote

import com.pat.app.holidayplanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val CALL_TIMEOUT_S = 60L

/** How much of an unexpected answer to keep in the error, enough to see what it was. */
private const val ERROR_PREVIEW_CHARS = 200

/**
 * Photos kept in Cloudflare R2 behind the same Worker as the Claude connector (tools/mcp). Much
 * quicker than Drive through Apps Script: no cold start, no redirect, and the picture goes up as
 * plain bytes rather than as text inside JSON.
 *
 * Set up by PHOTOS_URL and PHOTOS_TOKEN in local.properties; with either missing, [isConfigured] is
 * false and photos keep going to Drive.
 */
class CloudflarePhotos : ImageStore {

    private val baseUrl = BuildConfig.PHOTOS_URL.trim().trimEnd('/')
    private val token = BuildConfig.PHOTOS_TOKEN.trim()

    // No pooled connections: a phone that froze the app in the background kills them, and the
    // next upload would fail on a dead socket (the same lesson as HttpLinkResolver).
    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
        .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    override val isConfigured: Boolean get() = baseUrl.isNotEmpty() && token.isNotEmpty()

    override suspend fun upload(bytes: ByteArray, mimeType: String, name: String): Result<StoredImage> =
        send(
            Request.Builder()
                .url("$baseUrl/photos")
                .header("Authorization", "Bearer $token")
                .put(bytes.toRequestBody(mimeType.toMediaType())),
        ).mapCatching { body ->
            val json = Json.parseToJsonElement(body).jsonObject
            val url = json["url"]?.jsonPrimitive?.content.orEmpty()
            if (url.isEmpty()) throw IOException("No url in answer: ${body.take(ERROR_PREVIEW_CHARS)}")
            StoredImage(url = url, fileId = json["fileId"]?.jsonPrimitive?.content.orEmpty())
        }

    override suspend fun remove(url: String): Result<Unit> {
        if (!owns(url)) return Result.success(Unit)
        return send(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .delete(),
        ).map { }
    }

    override fun owns(url: String): Boolean = baseUrl.isNotEmpty() && url.startsWith("$baseUrl/photos/")

    private suspend fun send(request: Request.Builder): Result<String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request.header("User-Agent", APP_USER_AGENT).build()).execute().use { response ->
                val body = response.body.string()
                if (response.isSuccessful) {
                    Result.success(body)
                } else {
                    Result.failure(IOException("HTTP ${response.code}: ${body.take(ERROR_PREVIEW_CHARS)}"))
                }
            }
        } catch (error: IOException) {
            Result.failure(error)
        }
    }
}

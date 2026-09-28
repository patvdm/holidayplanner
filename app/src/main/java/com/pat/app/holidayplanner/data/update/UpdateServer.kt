package com.pat.app.holidayplanner.data.update

import com.pat.app.holidayplanner.BuildConfig
import com.pat.app.holidayplanner.data.remote.APP_USER_AGENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val DOWNLOAD_TIMEOUT_MIN = 15L
private const val HTTP_NOT_FOUND = 404

/** One published version of the app, as the Worker describes it. */
data class Release(
    val versionCode: Long,
    val versionName: String,
    val sha256: String,
    val size: Long,
    /** Path of the APK on the Worker, starting with a slash. */
    val apkPath: String,
)

/**
 * The app's update channel: the same Cloudflare Worker that stores photos (tools/mcp), reached
 * with the same token, since only someone who already has the app should be able to fetch it.
 */
class UpdateServer {

    private val baseUrl = BuildConfig.PHOTOS_URL.trim().trimEnd('/')
    private val token = BuildConfig.PHOTOS_TOKEN.trim()

    // Never pooled, for the same frozen-socket reason as the photo uploads.
    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
        .callTimeout(DOWNLOAD_TIMEOUT_MIN, TimeUnit.MINUTES)
        .build()

    val isConfigured: Boolean get() = baseUrl.isNotEmpty() && token.isNotEmpty()

    /** The newest published version, or null when nothing has been published yet. */
    suspend fun latest(): Result<Release?> = call("$baseUrl/app/latest") { response ->
        if (response.code == HTTP_NOT_FOUND) return@call null
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        val json = Json.parseToJsonElement(response.body.string()).jsonObject
        fun field(name: String) = json[name]?.jsonPrimitive ?: throw IOException("No $name in the answer")
        Release(
            versionCode = field("versionCode").long,
            versionName = json["versionName"]?.jsonPrimitive?.content.orEmpty(),
            sha256 = field("sha256").content,
            size = json["size"]?.jsonPrimitive?.long ?: 0,
            apkPath = field("apk").content,
        )
    }

    /** Streams the APK into [target], so a large file never sits in memory. */
    suspend fun download(release: Release, target: File): Result<Unit> =
        call("$baseUrl${release.apkPath}") { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            target.outputStream().use { out -> response.body.byteStream().use { it.copyTo(out) } }
        }

    private suspend fun <T> call(url: String, read: (Response) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", APP_USER_AGENT)
                .build()
            Result.success(client.newCall(request).execute().use(read))
        } catch (error: IOException) {
            Result.failure(error)
        } catch (error: IllegalArgumentException) {
            // A malformed answer from the Worker, or a bad address in local.properties.
            Result.failure(error)
        }
    }
}

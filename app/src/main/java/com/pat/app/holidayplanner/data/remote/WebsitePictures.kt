package com.pat.app.holidayplanner.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import java.io.IOException
import java.net.URI

private const val TAG = "WebsitePictures"
private const val TIMEOUT_MS = 10_000L

/** Enough of a page to reach its head, where the share picture is named; the rest is not read. */
private const val MAX_HEAD_CHARS = 200_000

/** The tags that name a page's share picture, most deliberate first. */
private val SHARE_TAGS = listOf("og:image:secure_url", "og:image", "twitter:image", "twitter:image:src")

/** Pictures that are a badge rather than a photo of the place. */
private val NOT_A_PHOTO = Regex("""(logo|icon|favicon|sprite|placeholder|default)""", RegexOption.IGNORE_CASE)

/**
 * The picture a place's own website shows when it is shared: what a restaurant or a bar chose to
 * be seen by, and often the only good photo of a place nobody has written about. Logos and SVGs
 * are skipped, since those are badges rather than pictures of the place.
 */
class WebsitePictures(
    private val clientFactory: () -> HttpClient = ::defaultClient,
) {

    /** The share picture of [pageUrl], or null when it names none worth showing. */
    suspend fun sharePicture(pageUrl: String): String? = try {
        val html = clientFactory().use { it.get(pageUrl).bodyAsText() }.take(MAX_HEAD_CHARS)
        sharePictureIn(html, pageUrl)
    } catch (e: IOException) {
        Log.w(TAG, "$pageUrl: ${e.message}")
        null
    } catch (e: IllegalStateException) {
        Log.w(TAG, "$pageUrl: ${e.message}")
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "$pageUrl: ${e.message}")
        null
    }
}

/** The share picture named in [html], made absolute against [pageUrl]. */
internal fun sharePictureIn(html: String, pageUrl: String): String? {
    val metas = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).map { it.value }.toList()
    val named = SHARE_TAGS.firstNotNullOfOrNull { tag ->
        metas.firstOrNull { attribute(it, "property") == tag || attribute(it, "name") == tag }
            ?.let { attribute(it, "content") }
    } ?: return null
    val absolute = runCatching { URI(pageUrl).resolve(named.trim()).toString() }.getOrNull() ?: return null
    val file = absolute.substringBefore('?').substringAfterLast('/')
    return absolute.takeIf {
        it.startsWith("http") && !file.endsWith(".svg", ignoreCase = true) && !NOT_A_PHOTO.containsMatchIn(file)
    }
}

private fun attribute(tag: String, name: String): String? =
    Regex("""\b$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
        ?.replace("&amp;", "&")

private fun defaultClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = true
    install(HttpTimeout) {
        requestTimeoutMillis = TIMEOUT_MS
        connectTimeoutMillis = TIMEOUT_MS
        socketTimeoutMillis = TIMEOUT_MS
    }
    defaultRequest {
        header(HttpHeaders.UserAgent, APP_USER_AGENT)
        header(HttpHeaders.Accept, "text/html")
    }
}

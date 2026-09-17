package com.pat.app.holidayplanner.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.ConnectionPool

private const val TAG = "LinkResolver"
private const val TIMEOUT_MS = 10_000L

/** Enough for any short link, and a hard stop if a site redirects to itself forever. */
private const val MAX_HOPS = 5

/** Attempts at the whole walk. Google answers a perfectly good short link with 404 now and then. */
private const val MAX_ATTEMPTS = 3
private const val RETRY_DELAY_MS = 400L

/**
 * Google's link service is friendlier to something that looks like the browser a person would have
 * opened the link in. The default `okhttp/x.y` identifies us as a robot for no benefit.
 */
private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

/** Where a link ended up, with enough detail to explain a disappointing answer. */
data class ResolvedLink(val url: String, val status: Int, val hops: Int) {
    fun movedFrom(start: String): Boolean = url != start
}

/** Follows a shortened link to whatever it really points at. */
interface LinkResolver {
    suspend fun resolve(url: String): Result<ResolvedLink>
}

/**
 * Resolves a short link by walking the redirects one at a time.
 *
 * The hops are followed here rather than by the HTTP engine, because an engine that follows them
 * internally only ever reports the address it was given, which is the one thing we already know.
 * Only headers are fetched, so the page itself is never downloaded.
 *
 * Connections are never pooled. Every Ktor OkHttp client in a process is built from one shared
 * prototype with `newBuilder()`, which copies the connection pool along with it, so even a brand
 * new client reuses old sockets. Samsung freezes background apps, those sockets die while frozen,
 * and the next request over one fails in ways that looked like the link being unresolvable.
 */
class HttpLinkResolver(
    private val clientFactory: () -> HttpClient = ::defaultClient,
) : LinkResolver {

    override suspend fun resolve(url: String): Result<ResolvedLink> {
        val client = clientFactory()
        return try {
            attempt(client, url)
        } finally {
            client.close()
        }
    }

    /**
     * Tries the whole walk a few times.
     *
     * Two different things go wrong intermittently and both recover on a second ask: a connection
     * that died while the app was frozen in the background, and Google answering a valid short link
     * with 404. Neither is worth showing someone who just wants to save a place.
     */
    private suspend fun attempt(client: HttpClient, url: String): Result<ResolvedLink> {
        var last = runCatching(client, url)
        repeat(MAX_ATTEMPTS - 1) { retry ->
            if (!worthRetrying(last)) return last
            Log.w(TAG, "attempt ${retry + 1} unsatisfying, trying again")
            delay(RETRY_DELAY_MS)
            last = runCatching(client, url)
        }
        return last
    }

    /** A failure, or a first request that was simply refused, rather than a real answer. */
    private fun worthRetrying(result: Result<ResolvedLink>): Boolean {
        val resolved = result.getOrNull() ?: return true
        return resolved.hops == 0 && resolved.status >= HttpStatusCode.BadRequest.value
    }

    private suspend fun runCatching(client: HttpClient, url: String): Result<ResolvedLink> = try {
        val resolved = follow(client, url)
        Log.d(TAG, "resolved $url to ${resolved.url} (HTTP ${resolved.status}, ${resolved.hops} hops)")
        Result.success(resolved)
    } catch (e: IOException) {
        Log.w(TAG, "network failure resolving $url: ${e.message}")
        Result.failure(e)
    } catch (e: IllegalStateException) {
        Log.w(TAG, "client failure resolving $url: ${e.message}")
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "not a usable address: ${e.message}")
        Result.failure(e)
    }

    private suspend fun follow(client: HttpClient, start: String): ResolvedLink {
        var current = start
        var status = 0
        repeat(MAX_HOPS) { hop ->
            val response = request(client, current)
            status = response.status.value
            val location = response.headers[HttpHeaders.Location]
            Log.d(TAG, "hop $hop: HTTP $status from $current")
            if (!response.status.isRedirect() || location.isNullOrBlank()) {
                return ResolvedLink(current, status, hop)
            }
            current = absolute(current, location)
        }
        Log.w(TAG, "gave up after $MAX_HOPS hops from $start")
        return ResolvedLink(current, status, MAX_HOPS)
    }

    /**
     * HEAD first, because it avoids the body. A server that refuses it, or that answers it without
     * the redirect it gives a GET, is asked again properly.
     */
    private suspend fun request(client: HttpClient, url: String): HttpResponse {
        val head = client.head(url)
        val refused = head.status == HttpStatusCode.MethodNotAllowed ||
            head.status == HttpStatusCode.NotImplemented
        val unhelpful = !head.status.isRedirect() && head.headers[HttpHeaders.Location].isNullOrBlank()
        return if (refused || unhelpful) client.get(url) else head
    }

    /** A Location header may be relative, in which case it is read against the address it came from. */
    private fun absolute(from: String, location: String): String = try {
        URI(from).resolve(location).toString()
    } catch (e: URISyntaxException) {
        location
    }
}

private fun HttpStatusCode.isRedirect(): Boolean = value in HttpStatusCode.MultipleChoices.value..
    HttpStatusCode.PermanentRedirect.value

private fun defaultClient(): HttpClient = HttpClient(OkHttp) {
    // Both layers leave redirects alone; follow() walks them so the final address is known.
    followRedirects = false
    expectSuccess = false
    engine {
        config {
            followRedirects(false)
            followSslRedirects(false)
            retryOnConnectionFailure(true)
            // No idle connections kept, so a socket frozen in the background is never reused.
            connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = TIMEOUT_MS
        connectTimeoutMillis = TIMEOUT_MS
        socketTimeoutMillis = TIMEOUT_MS
    }
    defaultRequest {
        header(HttpHeaders.UserAgent, USER_AGENT)
        header(HttpHeaders.Accept, "text/html,application/xhtml+xml,*/*")
        header(HttpHeaders.AcceptLanguage, "en-GB,en;q=0.9")
    }
}

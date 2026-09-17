package com.pat.app.holidayplanner.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The redirect walk, with the network faked.
 *
 * This exists because the first version handed redirect following to the HTTP engine, which meant
 * the client only ever reported the address it was given. Every Maps link resolved to itself, and
 * nothing caught it until it was tried by hand.
 */
class HttpLinkResolverTest {

    private val shortLink = "https://maps.app.goo.gl/AbCdEfGh"
    private val fullLink = "https://www.google.com/maps/place/Mauritshuis/@52.08,4.31,17z"

    @Test
    fun `a single redirect resolves to where it points`() = runTest {
        val resolver = resolverWith(shortLink to fullLink)

        assertEquals(fullLink, resolver.resolve(shortLink).getOrNull()?.url)
    }

    @Test
    fun `a chain of redirects is followed to the end`() = runTest {
        val middle = "https://www.google.com/maps?link=1"
        val resolver = resolverWith(shortLink to middle, middle to fullLink)

        assertEquals(fullLink, resolver.resolve(shortLink).getOrNull()?.url)
    }

    @Test
    fun `an address that does not redirect is returned unchanged`() = runTest {
        val resolver = resolverWith()

        assertEquals(fullLink, resolver.resolve(fullLink).getOrNull()?.url)
    }

    @Test
    fun `a relative location is read against the address it came from`() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/short") {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/maps/place/Somewhere"))
            } else {
                respond("", HttpStatusCode.OK)
            }
        }

        val resolved = HttpLinkResolver { client(engine) }.resolve("https://example.com/short").getOrNull()?.url

        assertEquals("https://example.com/maps/place/Somewhere", resolved)
    }

    @Test
    fun `a permanent redirect counts too`() = runTest {
        val engine = MockEngine { request ->
            if (request.url.host == "maps.app.goo.gl") {
                respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, fullLink))
            } else {
                respond("", HttpStatusCode.OK)
            }
        }

        assertEquals(fullLink, HttpLinkResolver { client(engine) }.resolve(shortLink).getOrNull()?.url)
    }

    @Test
    fun `a server that refuses HEAD is asked again with GET`() = runTest {
        var headSeen = false
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Head -> {
                    headSeen = true
                    respond("", HttpStatusCode.MethodNotAllowed)
                }
                else -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, fullLink))
            }
        }

        val resolved = HttpLinkResolver { client(engine) }.resolve(shortLink).getOrNull()?.url

        assertTrue("HEAD should be tried first", headSeen)
        assertEquals(fullLink, resolved)
    }

    @Test
    fun `a redirect that never ends stops rather than looping forever`() = runTest {
        val engine = MockEngine {
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://example.com/again"))
        }

        val result = HttpLinkResolver { client(engine) }.resolve(shortLink)

        assertTrue(result.isSuccess)
        assertEquals("https://example.com/again", result.getOrNull()?.url)
    }

    @Test
    fun `a redirect with no location header ends the walk`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Found) }

        assertEquals(shortLink, HttpLinkResolver { client(engine) }.resolve(shortLink).getOrNull()?.url)
    }

    @Test
    fun `a network failure is reported rather than thrown`() = runTest {
        val engine = MockEngine { throw java.io.IOException("offline") }

        val result = HttpLinkResolver { client(engine) }.resolve(shortLink)

        assertTrue(result.isFailure)
    }

    @Test
    fun `a connection that fails once is retried`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            // A pooled connection that went stale while the app was backgrounded fails like this.
            if (calls == 1) throw java.io.IOException("stale connection")
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, fullLink))
        }

        val resolved = HttpLinkResolver { client(engine) }.resolve(shortLink).getOrNull()?.url

        assertEquals(fullLink, resolved)
    }

    @Test
    fun `a HEAD that hides the redirect is retried as a GET`() = runTest {
        val engine = MockEngine { request ->
            // Some servers answer HEAD plainly but redirect a real GET.
            if (request.method == HttpMethod.Head) {
                respond("", HttpStatusCode.OK)
            } else {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, fullLink))
            }
        }

        assertEquals(fullLink, HttpLinkResolver { client(engine) }.resolve(shortLink).getOrNull()?.url)
    }

    @Test
    fun `the status and hop count come back for diagnosis`() = runTest {
        val resolver = resolverWith(shortLink to fullLink)

        val resolved = resolver.resolve(shortLink).getOrNull()

        assertEquals(fullLink, resolved?.url)
        assertEquals(1, resolved?.hops)
        assertTrue(resolved?.movedFrom(shortLink) ?: false)
    }

    @Test
    fun `a refused first request is tried again`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            // Google answers a perfectly valid short link with 404 every so often.
            if (calls <= 2) {
                respondError(HttpStatusCode.NotFound)
            } else {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, fullLink))
            }
        }

        assertEquals(fullLink, HttpLinkResolver { client(engine) }.resolve(shortLink).getOrNull()?.url)
    }

    @Test
    fun `a link that is really gone reports the refusal rather than retrying forever`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respondError(HttpStatusCode.NotFound)
        }

        val resolved = HttpLinkResolver { client(engine) }.resolve(shortLink).getOrNull()

        assertEquals(shortLink, resolved?.url)
        assertEquals(HttpStatusCode.NotFound.value, resolved?.status)
        assertTrue("should stop after a few tries, not loop", calls in 1..8)
    }

    /** Redirects the given from-to pairs once each, and answers anything else with 200. */
    private fun resolverWith(vararg hops: Pair<String, String>): HttpLinkResolver {
        val byUrl = hops.toMap()
        val engine = MockEngine { request ->
            val target = byUrl[request.url.toString()]
            if (target == null) {
                respond("", HttpStatusCode.OK)
            } else {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, target))
            }
        }
        return HttpLinkResolver { client(engine) }
    }

    private fun client(engine: MockEngine) = HttpClient(engine) {
        followRedirects = false
        expectSuccess = false
    }
}

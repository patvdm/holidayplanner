package com.pat.app.holidayplanner.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikimediaPicturesTest {

    private val banner = "https://upload.wikimedia.org/thumb/Bruges_banner.jpg/1200px-Bruges_banner.jpg"
    private val peacePalace = "https://upload.wikimedia.org/thumb/Vredespaleis.jpg/1200px-Vredespaleis.jpg"

    /** What each Wikimedia API would answer, keyed by what is asked of it. */
    private fun answer(request: HttpRequestData): String {
        val host = request.url.host
        val titles = request.url.parameters["titles"]
        val search = request.url.parameters["gsrsearch"]
        return when {
            host == "en.wikivoyage.org" && titles == "Bruges" -> pages("""{"title":"Bruges"}""")
            host == "en.wikivoyage.org" && titles == "File:Bruges banner.jpg" ->
                pages("""{"title":"File:Bruges banner.jpg","imageinfo":[{"thumburl":"$banner"}]}""")
            // English Wikipedia only knows the palace under its English name, which the search misses.
            host == "en.wikipedia.org" && search != null -> pages("""{"title":"Den Haag","index":1}""")
            host == "nl.wikipedia.org" && search?.startsWith("Vredespaleis") == true ->
                pages("""{"title":"Vredespaleis","index":1,"thumbnail":{"source":"$peacePalace?utm_source=x"}}""")
            else -> pages()
        }
    }

    private fun pages(vararg page: String) = """{"query":{"pages":[${page.joinToString(",")}]}}"""

    private val pictures = WikimediaPictures(
        clientFactory = {
            HttpClient(
                MockEngine { request -> respondJson(answer(request)) },
            )
        },
    )

    private fun MockRequestHandleScope.respondJson(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun `a spot named after its own town gets the town's picture`() = runTest {
        val found = pictures.forSpot("Bruges", cityName = "Bruges", countryName = "Belgium", isoCode = "BE")

        assertEquals(banner, found.getOrNull())
    }

    @Test
    fun `a spot English Wikipedia misses is found in the country's own language`() = runTest {
        val found = pictures.forSpot("Vredespaleis", cityName = "Den Haag", countryName = "Netherlands", isoCode = "NL")

        assertEquals("$peacePalace?utm_source=x", found.getOrNull())
    }

    @Test
    fun `a page about the town is not taken for a spot in it`() = runTest {
        val found = pictures.forSpot("Funfabriek", cityName = "Den Haag", countryName = "Netherlands", isoCode = "GB")

        assertNull(found.getOrNull())
    }
}

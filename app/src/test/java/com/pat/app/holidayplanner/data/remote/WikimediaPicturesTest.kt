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
    private val paramaribo = "https://upload.wikimedia.org/thumb/Paramaribo_Waag.jpg/1200px-Paramaribo_Waag.jpg"
    private val amsterdamWaag = "https://upload.wikimedia.org/thumb/Waag_Nieuwmarkt.jpg/1200px-Waag_Nieuwmarkt.jpg"
    private val street = "https://upload.wikimedia.org/thumb/Nieuwmarkt_street.jpg/1200px-Nieuwmarkt_street.jpg"

    /** Amsterdam's Waag, on the Nieuwmarkt. */
    private val waagLat = 52.3727
    private val waagLng = 4.9003

    /** What each Wikimedia API would answer, keyed by what is asked of it. */
    private fun answer(request: HttpRequestData): String {
        val host = request.url.host
        val titles = request.url.parameters["titles"]
        val search = request.url.parameters["gsrsearch"]
        val near = request.url.parameters["ggscoord"]
        return when {
            host == "en.wikivoyage.org" && titles == "Bruges" -> pages("""{"title":"Bruges"}""")
            host == "en.wikivoyage.org" && titles == "File:Bruges banner.jpg" ->
                pages("""{"title":"File:Bruges banner.jpg","imageinfo":[{"thumburl":"$banner"}]}""")
            // The English search for the Waag finds the one in Paramaribo, 7,000 km away.
            host == "en.wikipedia.org" && search?.startsWith("WAAG") == true ->
                pages(article("De Waag", paramaribo, lat = 5.82, lon = -55.15))
            // English Wikipedia only knows the palace under its English name, which the search misses.
            host == "en.wikipedia.org" && search != null -> pages("""{"title":"Den Haag","index":1}""")
            host == "nl.wikipedia.org" && search?.startsWith("Vredespaleis") == true ->
                pages("""{"title":"Vredespaleis","index":1,"thumbnail":{"source":"$peacePalace?utm_source=x"}}""")
            // Around the Nieuwmarkt, Commons has a photo of the Waag itself and one of the street.
            host == "commons.wikimedia.org" && near != null -> pages(
                file("File:Nieuwmarkt street.jpg", street, lat = 52.3726, lon = 4.9001),
                file("File:Waag Nieuwmarkt.jpg", amsterdamWaag, lat = waagLat, lon = waagLng),
            )
            else -> pages()
        }
    }

    private fun pages(vararg page: String) = """{"query":{"pages":[${page.joinToString(",")}]}}"""

    private fun where(lat: Double, lon: Double) = """"coordinates":[{"lat":$lat,"lon":$lon}]"""

    private fun article(title: String, picture: String, lat: Double, lon: Double) =
        """{"title":"$title","index":1,"thumbnail":{"source":"$picture"},${where(lat, lon)}}"""

    private fun file(title: String, picture: String, lat: Double, lon: Double) =
        """{"title":"$title","imageinfo":[{"thumburl":"$picture"}],${where(lat, lon)}}"""

    private val pictures = WikimediaPictures(
        clientFactory = { HttpClient(MockEngine { request -> respondJson(answer(request)) }) },
        website = WebsitePictures(clientFactory = { HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }) }),
    )

    private fun MockRequestHandleScope.respondJson(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun spot(name: String, city: String, country: String, iso: String, near: Boolean = false) =
        SpotPictureQuery(
            name = name,
            cityName = city,
            countryName = country,
            isoCode = iso,
            lat = waagLat.takeIf { near },
            lng = waagLng.takeIf { near },
        )

    @Test
    fun `a spot named after its own town gets the town's picture`() = runTest {
        val found = pictures.forSpot(spot("Bruges", "Bruges", "Belgium", "BE"))

        assertEquals(banner, found.getOrNull())
    }

    @Test
    fun `a spot English Wikipedia misses is found in the country's own language`() = runTest {
        val found = pictures.forSpot(spot("Vredespaleis", "Den Haag", "Netherlands", "NL"))

        assertEquals("$peacePalace?utm_source=x", found.getOrNull())
    }

    @Test
    fun `a page about the town is not taken for a spot in it`() = runTest {
        val found = pictures.forSpot(spot("Funfabriek", "Den Haag", "Netherlands", "GB"))

        assertNull(found.getOrNull())
    }

    @Test
    fun `a namesake far away is turned down, and a photo of the place nearby taken instead`() = runTest {
        val found = pictures.forSpot(spot("WAAG", "Amsterdam", "Netherlands", "GB", near = true))

        assertEquals(amsterdamWaag, found.getOrNull())
    }

    @Test
    fun `the chooser offers what is nearby, closest first, with how far`() = runTest {
        val offered = pictures.candidatesForSpot(spot("WAAG", "Amsterdam", "Netherlands", "GB", near = true))
            .getOrThrow()

        assertEquals(listOf(amsterdamWaag, street), offered.map { it.url })
        assertEquals(PictureFrom.COMMONS, offered.first().from)
        assertEquals(0, offered.first().metres)
    }
}

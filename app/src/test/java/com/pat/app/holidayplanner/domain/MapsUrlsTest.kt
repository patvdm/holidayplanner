package com.pat.app.holidayplanner.domain

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapsUrlsTest {

    private val defaultLocale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `coordinates and a place id give an exact link`() {
        val url = MapsUrls.spot(lat = 52.0801, lng = 4.3145, googlePlaceId = "ChIJabc")

        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=52.0801%2C4.3145&query_place_id=ChIJabc",
            url,
        )
    }

    @Test
    fun `without a place id the link is just the point`() {
        val url = MapsUrls.spot(lat = 52.0801, lng = 4.3145)

        assertEquals("https://www.google.com/maps/search/?api=1&query=52.0801%2C4.3145", url)
        assertFalse(url.contains("query_place_id"))
    }

    @Test
    fun `without coordinates the link searches for the name and where it is`() {
        val url = MapsUrls.spot(
            lat = null,
            lng = null,
            name = "Mauritshuis",
            cityName = "Den Haag",
            countryName = "Netherlands",
        )

        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Mauritshuis%2C%20Den%20Haag%2C%20Netherlands",
            url,
        )
    }

    @Test
    fun `a half known location still produces a usable query`() {
        val url = MapsUrls.spot(lat = 52.0801, lng = null, name = "Mauritshuis", cityName = "Den Haag")

        assertTrue(url.contains("query=Mauritshuis%2C%20Den%20Haag"))
    }

    @Test
    fun `a Dutch device still writes coordinates with a dot`() {
        Locale.setDefault(Locale.forLanguageTag("nl-NL"))

        val url = MapsUrls.spot(lat = 52.0801, lng = 4.3145)

        assertTrue("coordinates must not use a comma decimal separator", url.contains("52.0801%2C4.3145"))
    }

    @Test
    fun `spaces and symbols in a name are encoded`() {
        val url = MapsUrls.spot(lat = null, lng = null, name = "Bar & Café", cityName = "Lisbon")

        assertTrue(url.contains("Bar%20%26%20Caf%C3%A9"))
        assertFalse("plus signs read as literal plus in Maps", url.contains("+"))
    }

    @Test
    fun `a spot with nothing known still returns a well formed url`() {
        val url = MapsUrls.spot(lat = null, lng = null)

        assertEquals("https://www.google.com/maps/search/?api=1&query=", url)
    }

    @Test
    fun `a route runs from the first stop to the last through the rest`() {
        val url = MapsUrls.route(
            listOf(
                RoutePoint("Mauritshuis", 52.0802, 4.3146),
                RoutePoint("Binnenhof", 52.0799, 4.3131),
                RoutePoint("Peace Palace", 52.0866, 4.2955),
            ),
        )

        assertEquals(
            "https://www.google.com/maps/dir/?api=1" +
                "&origin=52.0802%2C4.3146" +
                "&destination=52.0866%2C4.2955" +
                "&waypoints=52.0799%2C4.3131" +
                "&travelmode=walking",
            url,
        )
    }

    @Test
    fun `two stops make a route with no waypoints at all`() {
        val url = MapsUrls.route(
            listOf(RoutePoint("A", 1.0, 2.0), RoutePoint("B", 3.0, 4.0)),
        )

        assertEquals(
            "https://www.google.com/maps/dir/?api=1&origin=1.0%2C2.0&destination=3.0%2C4.0&travelmode=walking",
            url,
        )
    }

    @Test
    fun `a single stop is not a route`() {
        assertNull(MapsUrls.route(listOf(RoutePoint("Alone", 1.0, 2.0))))
    }

    @Test
    fun `no stops is not a route either`() {
        assertNull(MapsUrls.route(emptyList()))
    }

    @Test
    fun `the travel mode is sent in the lower case Maps expects`() {
        val url = MapsUrls.route(
            listOf(RoutePoint("A", 1.0, 2.0), RoutePoint("B", 3.0, 4.0)),
            travelMode = TravelMode.BICYCLING,
        )

        assertTrue(url!!.endsWith("&travelmode=bicycling"))
    }

    @Test
    fun `a Turkish device still writes the travel mode Maps expects`() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))

        val url = MapsUrls.route(
            listOf(RoutePoint("A", 1.0, 2.0), RoutePoint("B", 3.0, 4.0)),
            travelMode = TravelMode.TRANSIT,
        )

        assertTrue("dotless i would break the parameter", url!!.endsWith("&travelmode=transit"))
    }

    @Test
    fun `place ids pin each end of the route`() {
        val url = MapsUrls.route(
            listOf(
                RoutePoint("A", 1.0, 2.0, googlePlaceId = "ChIJstart"),
                RoutePoint("B", 3.0, 4.0, googlePlaceId = "ChIJend"),
            ),
        )

        assertTrue(url!!.contains("&origin_place_id=ChIJstart"))
        assertTrue(url.contains("&destination_place_id=ChIJend"))
    }

    @Test
    fun `waypoint place ids are sent only when every waypoint has one`() {
        val known = MapsUrls.route(
            listOf(
                RoutePoint("A", 1.0, 2.0),
                RoutePoint("B", 3.0, 4.0, googlePlaceId = "ChIJb"),
                RoutePoint("C", 5.0, 6.0, googlePlaceId = "ChIJc"),
                RoutePoint("D", 7.0, 8.0),
            ),
        )

        assertTrue(known!!.contains("&waypoint_place_ids=ChIJb|ChIJc"))
    }

    @Test
    fun `one waypoint without a place id drops the whole list rather than misaligning it`() {
        val url = MapsUrls.route(
            listOf(
                RoutePoint("A", 1.0, 2.0),
                RoutePoint("B", 3.0, 4.0, googlePlaceId = "ChIJb"),
                RoutePoint("C", 5.0, 6.0),
                RoutePoint("D", 7.0, 8.0),
            ),
        )

        assertFalse(url!!.contains("waypoint_place_ids"))
    }

    @Test
    fun `a stop with no coordinates is sent as something to search for`() {
        val url = MapsUrls.route(
            listOf(RoutePoint("Bar & Cafe, Lisbon"), RoutePoint("B", 3.0, 4.0)),
        )

        assertTrue(url!!.contains("&origin=Bar%20%26%20Cafe%2C%20Lisbon"))
    }

    @Test
    fun `waypoints are separated by pipes in the order given`() {
        val url = MapsUrls.route(
            listOf(
                RoutePoint("A", 1.0, 2.0),
                RoutePoint("B", 3.0, 4.0),
                RoutePoint("C", 5.0, 6.0),
                RoutePoint("D", 7.0, 8.0),
            ),
        )

        assertTrue(url!!.contains("&waypoints=3.0%2C4.0|5.0%2C6.0"))
    }

    @Test
    fun `eleven stops still go out as a single route`() {
        val parts = MapsUrls.splitForMaps((1..11).toList())

        assertEquals(1, parts.size)
        assertEquals((1..11).toList(), parts.single())
    }

    @Test
    fun `a twelfth stop splits the route, with part one ending where part two begins`() {
        val parts = MapsUrls.splitForMaps((1..12).toList())

        assertEquals(2, parts.size)
        assertEquals((1..11).toList(), parts[0])
        assertEquals(listOf(11, 12), parts[1])
    }

    @Test
    fun `a very long day splits into as many routes as it needs`() {
        val parts = MapsUrls.splitForMaps((1..25).toList())

        assertEquals(3, parts.size)
        assertTrue("no part may exceed the Maps limit", parts.all { it.size <= MapsUrls.MAX_ROUTE_STOPS })
        assertEquals(25, parts.last().last())
    }

    @Test
    fun `a split route covers every stop with no gaps between the parts`() {
        val parts = MapsUrls.splitForMaps((1..25).toList())

        val walked = parts.flatMapIndexed { index, part -> if (index == 0) part else part.drop(1) }
        assertEquals((1..25).toList(), walked)
    }

    @Test
    fun `there is nothing to split when there is no route`() {
        assertEquals(emptyList<List<Int>>(), MapsUrls.splitForMaps(listOf(1)))
        assertEquals(emptyList<List<Int>>(), MapsUrls.splitForMaps(emptyList<Int>()))
    }

    @Test
    fun `directions to one stop leave the origin to the phone`() {
        val url = MapsUrls.directionsTo(RoutePoint(label = "Vredespaleis", lat = 52.0864, lng = 4.2956))

        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1"))
        assertTrue(url.contains("destination=52.0864%2C4.2956"))
        assertFalse(url.contains("origin="))
        assertTrue(url.endsWith("travelmode=walking"))
    }

    @Test
    fun `a stop with no coordinates is searched for by name`() {
        val url = MapsUrls.directionsTo(RoutePoint(label = "Cafe de Klos, Kerkstraat 41"))

        assertTrue(url.contains("destination=Cafe%20de%20Klos%2C%20Kerkstraat%2041"))
    }

    @Test
    fun `a place id pins the exact business to walk to`() {
        val url = MapsUrls.directionsTo(RoutePoint(label = "Klos", googlePlaceId = "abc123"))

        assertTrue(url.contains("destination_place_id=abc123"))
    }
}

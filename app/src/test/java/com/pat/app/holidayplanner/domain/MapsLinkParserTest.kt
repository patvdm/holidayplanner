package com.pat.app.holidayplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shapes Google Maps actually produces. Which one you get depends on how the link was made, so the
 * parser has to cope with all of them rather than the tidy documented form.
 */
class MapsLinkParserTest {

    @Test
    fun `a place link gives the name and the pin`() {
        val place = MapsLinkParser.parse(
            "https://www.google.com/maps/place/Mauritshuis/@52.0801,4.3145,17z/" +
                "data=!3m1!4b1!4m6!3m5!1s0x47c5b7!8m2!3d52.0802!4d4.3146",
        )

        assertEquals("Mauritshuis", place.name)
        assertEquals(52.0802, place.lat!!, 0.0001)
        assertEquals(4.3146, place.lng!!, 0.0001)
    }

    @Test
    fun `the pin wins over the camera position`() {
        val place = MapsLinkParser.parse(
            "https://www.google.com/maps/place/Somewhere/@10.0,20.0,17z/data=!8m2!3d30.0!4d40.0",
        )

        assertEquals(30.0, place.lat!!, 0.0001)
        assertEquals(40.0, place.lng!!, 0.0001)
    }

    @Test
    fun `a place link without a data segment still gives the camera position`() {
        val place = MapsLinkParser.parse("https://www.google.com/maps/place/Mauritshuis/@52.0801,4.3145,17z")

        assertEquals("Mauritshuis", place.name)
        assertEquals(52.0801, place.lat!!, 0.0001)
    }

    @Test
    fun `an encoded name is readable again`() {
        val place = MapsLinkParser.parse("https://www.google.com/maps/place/Caf%C3%A9+de+Paris/@48.87,2.33,17z")

        assertEquals("Café de Paris", place.name)
    }

    @Test
    fun `a plus in a name becomes a space`() {
        assertEquals("Den Haag Centraal", MapsLinkParser.parse("https://google.com/maps/place/Den+Haag+Centraal").name)
    }

    @Test
    fun `the api search form gives coordinates`() {
        val place = MapsLinkParser.parse("https://www.google.com/maps/search/?api=1&query=52.0801%2C4.3145")

        assertEquals(52.0801, place.lat!!, 0.0001)
        assertEquals(4.3145, place.lng!!, 0.0001)
        assertEquals("", place.name)
    }

    @Test
    fun `a text query is treated as the name rather than coordinates`() {
        val place = MapsLinkParser.parse("https://www.google.com/maps/search/?api=1&query=Mauritshuis%2C%20Den%20Haag")

        assertEquals("Mauritshuis, Den Haag", place.name)
        assertNull(place.lat)
    }

    @Test
    fun `the old q parameter works too`() {
        val place = MapsLinkParser.parse("https://maps.google.com/?q=52.08,4.31")

        assertEquals(52.08, place.lat!!, 0.0001)
        assertEquals(4.31, place.lng!!, 0.0001)
    }

    @Test
    fun `the loc prefix is stripped`() {
        val place = MapsLinkParser.parse("https://maps.google.com/?q=loc:52.08,4.31")

        assertEquals(52.08, place.lat!!, 0.0001)
    }

    @Test
    fun `southern and western coordinates keep their sign`() {
        val place = MapsLinkParser.parse("https://www.google.com/maps/place/Somewhere/@-33.8688,-151.2093,17z")

        assertEquals(-33.8688, place.lat!!, 0.0001)
        assertEquals(-151.2093, place.lng!!, 0.0001)
    }

    @Test
    fun `a link with nothing usable reports itself empty`() {
        val place = MapsLinkParser.parse("https://www.google.com/maps")

        assertTrue(place.isEmpty)
        assertFalse(place.hasCoordinates)
    }

    @Test
    fun `an unresolved short link yields nothing, which is why it must be followed first`() {
        assertTrue(MapsLinkParser.parse("https://maps.app.goo.gl/AbCdEfGh").isEmpty)
    }

    @Test
    fun `a name without coordinates is still usable`() {
        val place = MapsLinkParser.parse("https://www.google.com/maps/place/Mauritshuis")

        assertEquals("Mauritshuis", place.name)
        assertFalse(place.hasCoordinates)
        assertFalse(place.isEmpty)
    }
}

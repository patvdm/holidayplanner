package com.pat.app.holidayplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Address shapes taken from real Google responses. The point of these is that the components vary
 * by country, so the fallback order is the only thing keeping a spot out of the wrong place.
 */
class AddressComponentsTest {

    @Test
    fun `a Dutch address uses the locality`() {
        val resolved = AddressComponents.resolve(mauritshuis)

        assertEquals("NL", resolved?.isoCode)
        assertEquals("Netherlands", resolved?.countryName)
        assertEquals("Den Haag", resolved?.cityName)
    }

    @Test
    fun `a British address falls back to the postal town`() {
        val parts = listOf(
            part("route", "Great Russell Street"),
            part("postal_town", "London"),
            part("administrative_area_level_2", "Greater London"),
            part("country", "United Kingdom", "GB"),
        )

        val resolved = AddressComponents.resolve(parts)

        assertEquals("GB", resolved?.isoCode)
        assertEquals("London", resolved?.cityName)
    }

    @Test
    fun `a rural address falls back to the county`() {
        val parts = listOf(
            part("administrative_area_level_2", "Alentejo Litoral"),
            part("administrative_area_level_1", "Setúbal"),
            part("country", "Portugal", "PT"),
        )

        assertEquals("Alentejo Litoral", AddressComponents.resolve(parts)?.cityName)
    }

    @Test
    fun `an address with only a province uses it`() {
        val parts = listOf(
            part("administrative_area_level_1", "Tyrol"),
            part("country", "Austria", "AT"),
        )

        assertEquals("Tyrol", AddressComponents.resolve(parts)?.cityName)
    }

    @Test
    fun `locality wins over the coarser levels`() {
        val parts = listOf(
            part("administrative_area_level_1", "South Holland"),
            part("administrative_area_level_2", "Haaglanden"),
            part("postal_town", "The Hague"),
            part("locality", "Den Haag"),
            part("country", "Netherlands", "NL"),
        )

        assertEquals("Den Haag", AddressComponents.resolve(parts)?.cityName)
    }

    @Test
    fun `a component carrying several types is still recognised`() {
        val parts = listOf(
            AddressPart(types = listOf("locality", "political"), name = "Lisbon", shortName = "Lisbon"),
            AddressPart(types = listOf("country", "political"), name = "Portugal", shortName = "PT"),
        )

        assertEquals("Lisbon", AddressComponents.resolve(parts)?.cityName)
        assertEquals("PT", AddressComponents.resolve(parts)?.isoCode)
    }

    @Test
    fun `an address with no city leaves the place name empty rather than guessing`() {
        val parts = listOf(part("country", "Japan", "JP"))

        val resolved = AddressComponents.resolve(parts)

        assertEquals("JP", resolved?.isoCode)
        assertEquals("", resolved?.cityName)
    }

    @Test
    fun `no country means the hierarchy cannot be decided`() {
        assertNull(AddressComponents.resolve(listOf(part("locality", "Nowhere"))))
        assertNull(AddressComponents.resolve(emptyList()))
    }

    @Test
    fun `a blank country code is treated as no country`() {
        assertNull(AddressComponents.resolve(listOf(part("country", "Atlantis", "  "))))
    }

    @Test
    fun `a lower case country code is normalised`() {
        val parts = listOf(part("country", "Netherlands", "nl"))

        assertEquals("NL", AddressComponents.resolve(parts)?.isoCode)
    }

    @Test
    fun `a country with no long name falls back to its code`() {
        val parts = listOf(part("country", "", "NL"))

        assertEquals("NL", AddressComponents.resolve(parts)?.countryName)
    }

    @Test
    fun `whitespace around names is trimmed`() {
        val parts = listOf(
            part("locality", "  Den Haag  "),
            part("country", "  Netherlands  ", " NL "),
        )

        val resolved = AddressComponents.resolve(parts)

        assertEquals("Den Haag", resolved?.cityName)
        assertEquals("Netherlands", resolved?.countryName)
        assertEquals("NL", resolved?.isoCode)
    }

    @Test
    fun `an empty locality falls through to the next level`() {
        val parts = listOf(
            part("locality", "   "),
            part("administrative_area_level_2", "Haaglanden"),
            part("country", "Netherlands", "NL"),
        )

        assertEquals("Haaglanden", AddressComponents.resolve(parts)?.cityName)
    }

    @Test
    fun `the place name alone can be read without a country`() {
        assertEquals("Den Haag", AddressComponents.cityName(mauritshuis))
        assertNull(AddressComponents.cityName(listOf(part("country", "Netherlands", "NL"))))
    }

    private val mauritshuis = listOf(
        part("street_number", "29"),
        part("route", "Plein"),
        part("locality", "Den Haag"),
        part("administrative_area_level_2", "Haaglanden"),
        part("administrative_area_level_1", "South Holland"),
        part("country", "Netherlands", "NL"),
        part("postal_code", "2511 CS"),
    )

    private fun part(type: String, name: String, shortName: String = name) =
        AddressPart(types = listOf(type), name = name, shortName = shortName)
}

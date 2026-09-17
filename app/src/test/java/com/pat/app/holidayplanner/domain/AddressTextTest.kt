package com.pat.app.holidayplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Addresses as Google writes them when sharing a place. None of this is exact, and the answer only
 * ever prefills a field, but getting the common shapes right saves typing every single time.
 */
class AddressTextTest {

    @Test
    fun `a Dutch address gives the city without its postcode`() {
        assertEquals("Den Haag", AddressText.cityFrom("Plein 29, 2511 CS Den Haag"))
    }

    @Test
    fun `a trailing country name is dropped`() {
        assertEquals("Den Haag", AddressText.cityFrom("Plein 29, 2511 CS Den Haag, Netherlands"))
    }

    @Test
    fun `a Portuguese postcode is stripped`() {
        assertEquals("Lisboa", AddressText.cityFrom("R. Garrett 1, 1200-204 Lisboa"))
    }

    @Test
    fun `a French postcode is stripped`() {
        assertEquals("Paris", AddressText.cityFrom("10 Rue de Rivoli, 75001 Paris, France"))
    }

    @Test
    fun `a British postcode after the town is stripped`() {
        assertEquals("London", AddressText.cityFrom("Great Russell St, London WC1B 3DG, UK"))
    }

    @Test
    fun `an American address falls back past the state code`() {
        assertEquals(
            "Mountain View",
            AddressText.cityFrom("1600 Amphitheatre Pkwy, Mountain View, CA 94043, USA"),
        )
    }

    @Test
    fun `a bare city is returned as it is`() {
        assertEquals("Den Haag", AddressText.cityFrom("Den Haag"))
    }

    @Test
    fun `a two word city survives`() {
        assertEquals("Den Bosch", AddressText.cityFrom("Markt 1, 5211 JX Den Bosch"))
    }

    @Test
    fun `nothing in gives nothing out`() {
        assertEquals("", AddressText.cityFrom(""))
        assertEquals("", AddressText.cityFrom("  ,  "))
    }

    @Test
    fun `a name is cut out of an address that has no place name`() {
        assertEquals("Plein 29", AddressText.nameFrom("Plein 29, 2511 CS Den Haag"))
    }

    @Test
    fun `the country and city are both left out of the name`() {
        assertEquals("Plein 29", AddressText.nameFrom("Plein 29, 2511 CS Den Haag, Netherlands"))
        assertEquals("Great Russell St", AddressText.nameFrom("Great Russell St, London WC1B 3DG, UK"))
    }

    @Test
    fun `the name stops before the city, not before the state`() {
        assertEquals(
            "1600 Amphitheatre Pkwy",
            AddressText.nameFrom("1600 Amphitheatre Pkwy, Mountain View, CA 94043, USA"),
        )
    }

    @Test
    fun `only the head of the address becomes the name`() {
        assertEquals("Plein 29", AddressText.nameFrom("Plein 29, 2nd floor, 2511 CS Den Haag"))
    }

    @Test
    fun `a place name in front of its own address wins`() {
        assertEquals("Mauritshuis", AddressText.nameFrom("Mauritshuis, Plein 29, 2511 CS Den Haag"))
        assertEquals(
            "Rijksmuseum",
            AddressText.nameFrom("Rijksmuseum, Museumstraat 1, 1071 XX Amsterdam, Netherlands"),
        )
    }

    @Test
    fun `an address that is only a city yields no name rather than repeating the city`() {
        assertEquals("", AddressText.nameFrom("Den Haag"))
        assertEquals("", AddressText.nameFrom("2511 CS Den Haag, Netherlands"))
        assertEquals("", AddressText.nameFrom(""))
    }

    @Test
    fun `a country at the end of an address is recognised`() {
        assertEquals("NL", AddressText.countryFrom("Plein 29, 2511 CS Den Haag, Netherlands"))
        assertEquals("PT", AddressText.countryFrom("R. Garrett 1, 1200-204 Lisboa, Portugal"))
    }

    @Test
    fun `common country shorthands map to their ISO codes`() {
        assertEquals("GB", AddressText.countryFrom("Great Russell St, London WC1B 3DG, UK"))
        assertEquals("US", AddressText.countryFrom("1600 Amphitheatre Pkwy, Mountain View, CA 94043, USA"))
    }

    @Test
    fun `an address with no country names none`() {
        assertEquals("", AddressText.countryFrom("Rooseveltstraat 49c, 2321 BL Leiden"))
        assertEquals("", AddressText.countryFrom(""))
    }

    @Test
    fun `a street address reads as an address`() {
        assertTrue(AddressText.looksLikeAddress("Plein 29, 2511 CS Den Haag"))
        assertTrue(AddressText.looksLikeAddress("1600 Amphitheatre Pkwy, Mountain View, CA 94043"))
        assertTrue(AddressText.looksLikeAddress("Great Russell St, London, UK"))
    }

    @Test
    fun `a place name does not read as an address`() {
        assertFalse(AddressText.looksLikeAddress("Mauritshuis"))
        assertFalse(AddressText.looksLikeAddress("Café de Paris"))
        assertFalse(AddressText.looksLikeAddress("Rijksmuseum Amsterdam"))
        assertFalse(AddressText.looksLikeAddress(""))
    }

    @Test
    fun `a name with one comma and no number is still a name`() {
        assertFalse(AddressText.looksLikeAddress("Bar Alto, Lisbon"))
    }
}

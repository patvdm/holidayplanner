package com.pat.app.holidayplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryCatalogTest {

    @Test
    fun `the Netherlands has the expected flag`() {
        assertEquals("🇳🇱", CountryCatalog.flag("NL"))
    }

    @Test
    fun `a lower case code still gets a flag`() {
        assertEquals(CountryCatalog.flag("NL"), CountryCatalog.flag("nl"))
    }

    @Test
    fun `nonsense codes get no flag rather than broken glyphs`() {
        assertEquals("", CountryCatalog.flag(""))
        assertEquals("", CountryCatalog.flag("N"))
        assertEquals("", CountryCatalog.flag("NLD"))
        assertEquals("", CountryCatalog.flag("N1"))
    }

    @Test
    fun `the catalog covers the countries we care about`() {
        val codes = CountryCatalog.all().map { it.isoCode }

        assertTrue(codes.containsAll(listOf("NL", "PT", "ES", "IT", "JP")))
    }

    @Test
    fun `names are English and sorted`() {
        val names = CountryCatalog.all().map { it.name }

        assertTrue(names.contains("Netherlands"))
        assertEquals(names.sortedBy { it.lowercase() }, names)
    }

    @Test
    fun `every entry has a flag`() {
        assertTrue(CountryCatalog.all().all { it.flag.isNotEmpty() })
    }
}

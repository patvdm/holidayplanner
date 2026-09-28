package com.pat.app.holidayplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikiLanguagesTest {

    @Test
    fun `a country is searched in its own language`() {
        assertEquals("nl", WikiLanguages.forCountry("NL"))
        assertEquals("de", WikiLanguages.forCountry("DE"))
        assertEquals("sl", WikiLanguages.forCountry("SI"))
        assertEquals("hr", WikiLanguages.forCountry("hr"))
    }

    @Test
    fun `countries with more than one language get the one most of their spots are in`() {
        assertEquals("nl", WikiLanguages.forCountry("BE"))
        assertEquals("de", WikiLanguages.forCountry("CH"))
        assertEquals("es", WikiLanguages.forCountry("ES"))
    }

    @Test
    fun `an English speaking or unknown country has no second search`() {
        assertNull(WikiLanguages.forCountry("GB"))
        assertNull(WikiLanguages.forCountry("US"))
        assertNull(WikiLanguages.forCountry(""))
        assertNull(WikiLanguages.forCountry("ZZ"))
    }
}

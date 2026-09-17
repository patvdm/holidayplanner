package com.pat.app.holidayplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {

    @Test
    fun `an ordinary word is wrapped so it matches anywhere in a field`() {
        assertEquals("%Lisbon%", SearchQuery.like("Lisbon"))
    }

    @Test
    fun `surrounding whitespace is dropped`() {
        assertEquals("%Lisbon%", SearchQuery.like("  Lisbon  "))
    }

    @Test
    fun `spaces inside the search are kept, because titles have them`() {
        assertEquals("%Peace Palace%", SearchQuery.like("Peace Palace"))
    }

    @Test
    fun `a percent sign is escaped rather than matching everything`() {
        assertEquals("%\\%%", SearchQuery.like("%"))
    }

    @Test
    fun `an underscore is escaped rather than matching any character`() {
        assertEquals("%a\\_b%", SearchQuery.like("a_b"))
    }

    @Test
    fun `a backslash is escaped first, so it cannot escape the escaping`() {
        assertEquals("%\\\\%", SearchQuery.like("\\"))
    }

    @Test
    fun `a backslash before a percent stays literal`() {
        assertEquals("%\\\\\\%%", SearchQuery.like("\\%"))
    }

    @Test
    fun `a booking reference searches literally`() {
        assertEquals("%AB_123%".replace("_", "\\_"), SearchQuery.like("AB_123"))
    }

    @Test
    fun `blank input is not a search`() {
        assertFalse(SearchQuery.isSearchable(""))
        assertFalse(SearchQuery.isSearchable("   "))
    }

    @Test
    fun `any real text is a search`() {
        assertTrue(SearchQuery.isSearchable("a"))
        assertTrue(SearchQuery.isSearchable(" x "))
    }

    @Test
    fun `every hit type round-trips from its stored name`() {
        SearchHitType.entries.forEach { type ->
            assertEquals(type, SearchHitType.fromRow(type.name))
        }
    }

    @Test
    fun `an unknown type is dropped rather than guessed at`() {
        assertNull(SearchHitType.fromRow("PLANET"))
        assertNull(SearchHitType.fromRow(""))
        assertNull(SearchHitType.fromRow("spot"))
    }
}

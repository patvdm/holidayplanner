package com.pat.app.holidayplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private data class Entry(val type: String, val place: String, val at: Long)

class FeedGroupingTest {

    private fun group(vararg entries: Entry): List<FeedGroup> = FeedGrouping.group(
        items = entries.toList(),
        typeOf = { FeedEntryType.fromRow(it.type) },
        placeOf = { it.place },
        createdAtOf = { it.at },
    )

    @Test
    fun `the same kind in the same place collapses into one line`() {
        val groups = group(
            Entry("LINK", "Lisbon", 10),
            Entry("LINK", "Lisbon", 20),
            Entry("LINK", "Lisbon", 30),
        )

        assertEquals(1, groups.size)
        assertEquals(3, groups.single().count)
        assertEquals(FeedEntryType.LINK, groups.single().type)
        assertEquals("Lisbon", groups.single().place)
    }

    @Test
    fun `the same kind in different places stays apart`() {
        val groups = group(Entry("LINK", "Lisbon", 10), Entry("LINK", "Den Haag", 20))

        assertEquals(2, groups.size)
        assertTrue(groups.all { it.count == 1 })
    }

    @Test
    fun `different kinds in one place stay apart`() {
        val groups = group(Entry("LINK", "Lisbon", 10), Entry("SPOT", "Lisbon", 20))

        assertEquals(2, groups.size)
        assertEquals(setOf(FeedEntryType.LINK, FeedEntryType.SPOT), groups.map { it.type }.toSet())
    }

    @Test
    fun `the newest activity leads the strip`() {
        val groups = group(
            Entry("LINK", "Lisbon", 10),
            Entry("SPOT", "Den Haag", 99),
            Entry("TRIP", "", 50),
        )

        assertEquals(listOf("Den Haag", "", "Lisbon"), groups.map { it.place })
    }

    @Test
    fun `a group is dated by its newest member, not its first`() {
        val groups = group(Entry("LINK", "Lisbon", 5), Entry("LINK", "Lisbon", 500))

        assertEquals(500, groups.single().newestAt)
    }

    @Test
    fun `things with no place group together under none`() {
        val groups = group(Entry("TRIP", "", 10), Entry("TRIP", "", 20))

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().count)
        assertEquals("", groups.single().place)
    }

    @Test
    fun `surrounding whitespace does not split a place in two`() {
        val groups = group(Entry("SPOT", "Lisbon", 10), Entry("SPOT", " Lisbon ", 20))

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().count)
    }

    @Test
    fun `an unrecognised kind is dropped rather than counted`() {
        val groups = group(Entry("MOON_BASE", "Lisbon", 10), Entry("SPOT", "Lisbon", 20))

        assertEquals(1, groups.size)
        assertEquals(FeedEntryType.SPOT, groups.single().type)
    }

    @Test
    fun `nothing this week is an empty strip`() {
        assertEquals(emptyList<FeedGroup>(), group())
    }

    @Test
    fun `the window reaches back exactly seven days`() {
        val now = 1_000_000_000_000L
        val sevenDays = 7L * 24 * 60 * 60 * 1000

        assertEquals(now - sevenDays, FeedGrouping.since(now))
    }

    @Test
    fun `every entry type round-trips from its stored name`() {
        FeedEntryType.entries.forEach { assertEquals(it, FeedEntryType.fromRow(it.name)) }
    }

    @Test
    fun `an unknown entry type reads as nothing`() {
        assertNull(FeedEntryType.fromRow("SOMETHING_NEW"))
    }
}

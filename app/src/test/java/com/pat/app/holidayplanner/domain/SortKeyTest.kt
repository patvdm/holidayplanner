package com.pat.app.holidayplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SortKeyTest {

    @Test
    fun `first key sits in the middle of the alphabet`() {
        val key = SortKey.between(null, null)
        assertTrue(key.isNotEmpty())
        assertTrue(SortKey.isValid(key))
    }

    @Test
    fun `key after an item is greater than it`() {
        val first = SortKey.between(null, null)
        val second = SortKey.after(first)
        assertTrue("$second should be after $first", second > first)
    }

    @Test
    fun `key before an item is smaller than it`() {
        val first = SortKey.between(null, null)
        val before = SortKey.between(null, first)
        assertTrue("$before should be before $first", before < first)
    }

    @Test
    fun `key between two neighbours lands between them`() {
        val left = SortKey.between(null, null)
        val right = SortKey.after(left)
        val middle = SortKey.between(left, right)
        assertTrue("$left < $middle < $right", left < middle && middle < right)
    }

    @Test
    fun `adjacent keys still take a key between them`() {
        val left = "V"
        val right = "W"
        val middle = SortKey.between(left, right)
        assertTrue("$left < $middle < $right", left < middle && middle < right)
        assertTrue(SortKey.isValid(middle))
    }

    @Test
    fun `every generated key is valid`() {
        var key = SortKey.between(null, null)
        repeat(100) {
            key = SortKey.after(key)
            assertTrue("$key should be a valid key", SortKey.isValid(key))
        }
    }

    @Test
    fun `appending a thousand items keeps them in order`() {
        val keys = mutableListOf<String>()
        repeat(1_000) { keys += SortKey.after(keys.lastOrNull()) }
        assertEquals(keys.sorted(), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `a thousand inserts at the same position stay strictly ordered`() {
        val left = SortKey.between(null, null)
        val right = SortKey.after(left)
        var previous = right
        val inserted = mutableListOf<String>()
        repeat(1_000) {
            previous = SortKey.between(left, previous)
            inserted += previous
            assertTrue("$left < $previous < $right", previous > left && previous < right)
        }
        assertEquals(inserted.size, inserted.toSet().size)
        assertEquals(inserted.sortedDescending(), inserted)
    }

    @Test
    fun `a thousand inserts at the head stay strictly ordered`() {
        var head = SortKey.between(null, null)
        repeat(1_000) {
            val next = SortKey.between(null, head)
            assertTrue("$next should be before $head", next < head)
            assertTrue(SortKey.isValid(next))
            head = next
        }
    }

    @Test
    fun `keys ending in the lowest digit are rejected`() {
        assertTrue(SortKey.isValid("V"))
        assertTrue(!SortKey.isValid("V0"))
        assertTrue(!SortKey.isValid(""))
        assertTrue(!SortKey.isValid("V-"))
    }

    @Test
    fun `neighbours in the wrong order are ignored rather than throwing`() {
        val key = SortKey.between("W", "V")
        assertTrue("$key should be after W", key > "W")
    }

    @Test
    fun `a neighbour edited by hand into a bad key is ignored`() {
        val key = SortKey.between("not a key!", null)
        assertTrue(SortKey.isValid(key))
        assertNotEquals("", key)
    }

    @Test
    fun `sorting by key matches insertion order for a shuffled rebuild`() {
        val a = SortKey.between(null, null)
        val b = SortKey.after(a)
        val c = SortKey.between(a, b)
        val d = SortKey.between(null, a)
        assertEquals(listOf(d, a, c, b), listOf(a, b, c, d).sorted())
    }
}

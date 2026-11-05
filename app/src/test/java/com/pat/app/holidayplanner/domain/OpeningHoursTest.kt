package com.pat.app.holidayplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate
import java.time.LocalTime

class OpeningHoursTest {

    private val day = LocalDate.of(2026, 11, 5)
    private fun at(hour: Int, minute: Int = 0) = LocalTime.of(hour, minute)

    /** Like Cabron: dinner only, from six, and late on Friday and Saturday. */
    private val dinner = OpeningHours(
        checked = day,
        always = false,
        periods = listOf(
            OpenPeriod(FRIDAY, at(18), SATURDAY, at(1)),
            OpenPeriod(SATURDAY, at(18), SUNDAY, at(1)),
            OpenPeriod(SUNDAY, at(18), SUNDAY, at(22)),
        ),
    )

    @Test
    fun `open in the evening, not at lunch`() {
        assertEquals(true, dinner.isOpenAt(SATURDAY, at(19)))
        assertEquals(false, dinner.isOpenAt(SATURDAY, at(12, 30)))
    }

    @Test
    fun `a late night counts on the morning after`() {
        assertEquals(true, dinner.isOpenAt(SATURDAY, at(0, 30)))
        assertEquals(false, dinner.isOpenAt(SATURDAY, at(1)))
    }

    @Test
    fun `saturday night into sunday wraps round the week`() {
        val weekend = OpeningHours(day, false, listOf(OpenPeriod(SUNDAY, at(22), MONDAY, at(2))))

        assertEquals(true, weekend.isOpenAt(MONDAY, at(1)))
        assertEquals(true, weekend.isOpenAt(SUNDAY, at(23)))
        assertEquals(false, weekend.isOpenAt(MONDAY, at(3)))
    }

    @Test
    fun `a day with nothing opening is closed all day, even after a late night before it`() {
        assertTrue(dinner.closedAllDay(MONDAY))
        assertFalse(dinner.closedAllDay(SATURDAY))
    }

    @Test
    fun `unknown hours claim nothing`() {
        val unknown = OpeningHours.unknown(day)

        assertNull(unknown.isOpenAt(MONDAY, at(12)))
        assertFalse(unknown.closedAllDay(MONDAY))
    }

    @Test
    fun `always open is open at any hour`() {
        val always = OpeningHours(day, always = true, periods = emptyList())

        assertEquals(true, always.isOpenAt(MONDAY, at(4)))
        assertFalse(always.closedAllDay(MONDAY))
    }

    @Test
    fun `hours survive being written and read back`() {
        assertEquals(dinner, OpeningHours.parse(dinner.encode()))
        assertEquals("2026-11-05|51800-60100 61800-70100 71800-72200", dinner.encode())
        assertEquals(OpeningHours.unknown(day), OpeningHours.parse("2026-11-05|none"))
        assertEquals(true, OpeningHours.parse("2026-11-05|always")?.always)
    }

    @Test
    fun `a blank or garbled column means nobody has asked yet`() {
        assertNull(OpeningHours.parse(""))
        assertNull(OpeningHours.parse("soon|none"))
    }
}

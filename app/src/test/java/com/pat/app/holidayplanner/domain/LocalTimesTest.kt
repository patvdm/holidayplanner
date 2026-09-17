package com.pat.app.holidayplanner.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The flight from your plan's acceptance test: Amsterdam to Lisbon, where both ends are true at the
 * same moment and neither may be rewritten into the other's clock.
 */
class LocalTimesTest {

    private val defaultZone = TimeZone.getDefault()

    @After
    fun restoreZone() {
        TimeZone.setDefault(defaultZone)
    }

    @Test
    fun `a departure is shown in its own clock whatever the device is set to`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

        assertEquals("10:15", LocalTimes.timeOfDay("2026-04-01T10:15"))
    }

    @Test
    fun `an arrival is shown in its own clock too`() {
        assertEquals("12:40", LocalTimes.timeOfDay("2026-04-01T12:40"))
    }

    @Test
    fun `a flight west gains an hour back, so it takes longer than the clocks suggest`() {
        val elapsed = LocalTimes.elapsed(
            startLocal = "2026-04-01T10:15",
            startZone = "Europe/Amsterdam",
            endLocal = "2026-04-01T12:40",
            endZone = "Europe/Lisbon",
        )

        assertEquals("3h 25m", LocalTimes.formatDuration(elapsed!!))
    }

    @Test
    fun `a flight east loses an hour`() {
        val elapsed = LocalTimes.elapsed(
            startLocal = "2026-04-08T14:00",
            startZone = "Europe/Lisbon",
            endLocal = "2026-04-08T18:25",
            endZone = "Europe/Amsterdam",
        )

        assertEquals("3h 25m", LocalTimes.formatDuration(elapsed!!))
    }

    @Test
    fun `within one zone the duration is simply the difference`() {
        val elapsed = LocalTimes.elapsed(
            "2026-04-02T09:00", "Europe/Lisbon",
            "2026-04-02T09:45", "Europe/Lisbon",
        )

        assertEquals("45m", LocalTimes.formatDuration(elapsed!!))
    }

    @Test
    fun `a flight over midnight still measures correctly`() {
        val elapsed = LocalTimes.elapsed(
            "2026-04-01T23:30", "Europe/Amsterdam",
            "2026-04-02T01:00", "Europe/Amsterdam",
        )

        assertEquals("1h 30m", LocalTimes.formatDuration(elapsed!!))
    }

    @Test
    fun `an unparseable or missing time measures nothing rather than guessing`() {
        assertNull(LocalTimes.elapsed("", "Europe/Amsterdam", "2026-04-01T12:40", "Europe/Lisbon"))
        assertNull(LocalTimes.elapsed("nonsense", "Europe/Amsterdam", "2026-04-01T12:40", "Europe/Lisbon"))
        assertNull(LocalTimes.parse("2026-04-01"))
        assertNull(LocalTimes.parse(""))
    }

    @Test
    fun `an unknown zone falls back rather than failing`() {
        assertNull(LocalTimes.zoneOf("Middle/Earth"))
        assertNull(LocalTimes.elapsed("2026-04-01T10:15", "Middle/Earth", "2026-04-01T12:40", "Europe/Lisbon"))
    }

    @Test
    fun `times round trip through writing and reading`() {
        val moment = LocalDateTime.of(2026, 4, 1, 10, 15)

        assertEquals("2026-04-01T10:15", LocalTimes.write(moment))
        assertEquals(moment, LocalTimes.parse("2026-04-01T10:15"))
    }

    @Test
    fun `dates round trip too`() {
        val date = LocalDate.of(2026, 4, 1)

        assertEquals("2026-04-01", LocalTimes.writeDate(date))
        assertEquals(date, LocalTimes.parseDate("2026-04-01"))
        assertEquals(date, LocalTimes.dateOf("2026-04-01T10:15"))
    }

    @Test
    fun `a zone reads as the city people know it by`() {
        assertEquals("Amsterdam", LocalTimes.zoneLabel("Europe/Amsterdam"))
        assertEquals("Lisbon", LocalTimes.zoneLabel("Europe/Lisbon"))
        assertEquals("New York", LocalTimes.zoneLabel("America/New_York"))
        assertEquals("UTC", LocalTimes.zoneLabel("UTC"))
    }

    @Test
    fun `two zones are only worth pointing out when they differ`() {
        assertTrue(LocalTimes.differentZones("Europe/Amsterdam", "Europe/Lisbon"))
        assertFalse(LocalTimes.differentZones("Europe/Amsterdam", "Europe/Amsterdam"))
        assertFalse(LocalTimes.differentZones("", "Europe/Lisbon"))
    }

    @Test
    fun `a day heading reads plainly`() {
        assertEquals("Wed 1 Apr 2026", LocalTimes.formatDay(LocalDate.of(2026, 4, 1), Locale.UK))
    }
}

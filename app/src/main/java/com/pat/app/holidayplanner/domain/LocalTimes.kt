package com.pat.app.holidayplanner.domain

import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

/** How trip item times are written: local wall clock, with the zone kept in its own column. */
private val LOCAL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TIME_OF_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Local times, kept local.
 *
 * A flight leaves at one wall clock and lands at another, in a different zone, and both are true.
 * Converting either to a single timeline would show a Lisbon landing an hour out, so times are
 * stored and shown exactly as written and the zone travels alongside. Zones are only ever brought
 * together to work out how long something took.
 */
object LocalTimes {

    fun parse(local: String): LocalDateTime? = try {
        LocalDateTime.parse(local.trim(), LOCAL_FORMAT)
    } catch (e: DateTimeParseException) {
        null
    }

    fun parseDate(date: String): LocalDate? = try {
        LocalDate.parse(date.trim(), DATE_FORMAT)
    } catch (e: DateTimeParseException) {
        null
    }

    fun write(moment: LocalDateTime): String = moment.format(LOCAL_FORMAT)

    fun writeDate(date: LocalDate): String = date.format(DATE_FORMAT)

    /**
     * The day an item belongs to. A bare date counts: a spot put on a day has no time of its own,
     * and asking for one would be asking for something nobody knows.
     */
    fun dateOf(local: String): LocalDate? = parse(local)?.toLocalDate() ?: parseDate(local)

    /** "14:05", or empty when nothing was written. */
    fun timeOfDay(local: String): String = parse(local)?.format(TIME_OF_DAY).orEmpty()

    /** The short name of a zone: "Europe/Amsterdam" reads as "Amsterdam". */
    fun zoneLabel(zoneId: String): String =
        zoneId.substringAfterLast('/').replace('_', ' ').trim()

    /** True when two items are written against different clocks, which is worth showing. */
    fun differentZones(first: String, second: String): Boolean =
        first.isNotBlank() && second.isNotBlank() && first != second

    /**
     * How long something actually took, which is the one place zones are combined.
     *
     * Amsterdam 10:15 to Lisbon 12:40 is three hours twenty five, not two twenty five, because
     * Lisbon's clock runs an hour behind.
     */
    fun elapsed(
        startLocal: String,
        startZone: String,
        endLocal: String,
        endZone: String,
    ): Duration? {
        val start = parse(startLocal)
        val end = parse(endLocal)
        val from = zoneOf(startZone)
        if (start == null || end == null || from == null) return null
        val to = zoneOf(endZone) ?: from
        return Duration.between(start.atZone(from), end.atZone(to))
    }

    /** "3h 25m", or "45m" when there are no whole hours. */
    fun formatDuration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % MINUTES_PER_HOUR
        return if (hours == 0L) "${minutes}m" else "${hours}h ${minutes}m"
    }

    /** A readable day heading: "Wed 1 Apr 2026". */
    fun formatDay(date: LocalDate, locale: Locale = Locale.getDefault()): String {
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        val month = date.month.getDisplayName(TextStyle.SHORT, locale)
        return "$weekday ${date.dayOfMonth} $month ${date.year}"
    }

    fun zoneOf(zoneId: String): ZoneId? = try {
        ZoneId.of(zoneId.trim())
    } catch (e: DateTimeException) {
        null
    }

    /** The zone a new item starts with when nothing better is known. */
    fun deviceZone(): String = ZoneId.systemDefault().id

    private const val MINUTES_PER_HOUR = 60L
}

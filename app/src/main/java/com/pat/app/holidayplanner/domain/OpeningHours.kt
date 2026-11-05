package com.pat.app.holidayplanner.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** One stretch of being open, from a day and time to a day and time; it may run past midnight. */
data class OpenPeriod(val openDay: DayOfWeek, val open: LocalTime, val closeDay: DayOfWeek, val close: LocalTime) {

    private val start: Int get() = minuteOfWeek(openDay, open)

    /** Wraps round the week: Saturday night to Sunday morning ends after it starts. */
    private val end: Int
        get() {
            val raw = minuteOfWeek(closeDay, close)
            return if (raw <= start) raw + MINUTES_PER_WEEK else raw
        }

    fun covers(day: DayOfWeek, time: LocalTime): Boolean {
        val at = minuteOfWeek(day, time)
        return (at in start until end) || (at + MINUTES_PER_WEEK in start until end)
    }

    internal fun encode(): String = "${openDay.value}${hhmm(open)}-${closeDay.value}${hhmm(close)}"

    internal companion object {
        private val PATTERN = Regex("""([1-7])(\d{2})(\d{2})-([1-7])(\d{2})(\d{2})""")

        fun parse(text: String): OpenPeriod? {
            val numbers = PATTERN.matchEntire(text)?.groupValues?.drop(1)?.map(String::toInt) ?: return null
            val (opens, closes) = numbers.chunked(FIELDS_PER_END)
            val (openDay, openHour, openMinute) = opens
            val (closeDay, closeHour, closeMinute) = closes
            return runCatching {
                OpenPeriod(
                    openDay = DayOfWeek.of(openDay),
                    open = LocalTime.of(openHour, openMinute),
                    closeDay = DayOfWeek.of(closeDay),
                    close = LocalTime.of(closeHour % HOURS_PER_DAY, closeMinute),
                )
            }.getOrNull()
        }

        private fun hhmm(time: LocalTime) = "%02d%02d".format(time.hour, time.minute)
    }
}

/**
 * When a spot is open, as Google last said, and when it was asked. Stored in one text column as
 * `yyyy-MM-dd|what`, where what is [NONE] (Google has no hours for it, or no such place was found),
 * [ALWAYS], or the week's periods. A blank column means nobody has asked yet.
 */
data class OpeningHours(val checked: LocalDate, val always: Boolean, val periods: List<OpenPeriod>) {

    /** Google had nothing to say: the hours are unknown, which is not the same as closed. */
    val unknown: Boolean get() = !always && periods.isEmpty()

    /** Null when the hours are unknown, so nothing is claimed either way. */
    fun isOpenAt(day: DayOfWeek, time: LocalTime): Boolean? = when {
        always -> true
        unknown -> null
        else -> periods.any { it.covers(day, time) }
    }

    /** The stretches that open on [day], in order; empty on a day it does not open at all. */
    fun on(day: DayOfWeek): List<OpenPeriod> = periods.filter { it.openDay == day }.sortedBy { it.open }

    /** Known hours with nothing opening on [day]. A late night from the day before does not count. */
    fun closedAllDay(day: DayOfWeek): Boolean = !always && !unknown && on(day).isEmpty()

    fun encode(): String {
        val what = when {
            always -> ALWAYS
            periods.isEmpty() -> NONE
            else -> periods.joinToString(" ") { it.encode() }
        }
        return "$checked|$what"
    }

    companion object {
        const val NONE = "none"
        const val ALWAYS = "always"

        fun unknown(checked: LocalDate) = OpeningHours(checked, always = false, periods = emptyList())

        /** Null for a blank or unreadable column, which means the hours still have to be asked for. */
        fun parse(value: String): OpeningHours? {
            val checked = runCatching { LocalDate.parse(value.substringBefore('|')) }.getOrNull() ?: return null
            val what = value.substringAfter('|', missingDelimiterValue = "").trim()
            return when (what) {
                ALWAYS -> OpeningHours(checked, always = true, periods = emptyList())
                NONE, "" -> unknown(checked)
                else -> OpeningHours(checked, always = false, periods = what.split(' ').mapNotNull(OpenPeriod::parse))
            }
        }
    }
}

/** Day, hour and minute, at each end of a written period. */
private const val FIELDS_PER_END = 3
private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_DAY = MINUTES_PER_HOUR * HOURS_PER_DAY
private const val MINUTES_PER_WEEK = MINUTES_PER_DAY * 7

private fun minuteOfWeek(day: DayOfWeek, time: LocalTime): Int =
    (day.value - 1) * MINUTES_PER_DAY + time.hour * MINUTES_PER_HOUR + time.minute

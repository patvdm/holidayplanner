package com.pat.app.holidayplanner.ui.trips

import com.pat.app.holidayplanner.data.local.TripItemRow
import com.pat.app.holidayplanner.domain.LocalTimes
import java.time.LocalDate
import java.util.TreeSet

/** Longer than any holiday; a stray end date a year out does not become four hundred headers. */
private const val MAX_PLANNED_DAYS = 120

/** One day of a trip, with everything on it in the order it happens. */
data class TripDay(val date: LocalDate, val entries: List<TripItemRow>)

/** The days of a trip, and the items that have not been given a day. */
data class TripPlan(val days: List<TripDay>, val undated: List<TripItemRow>)

/**
 * Lays a trip's items out by day.
 *
 * Every day between the trip's dates is present whether or not anything is on it yet, because the
 * point of the screen is to fill the empty ones. An item dated outside that range still gets its
 * day, so nothing planned is ever hidden; an item with no date at all waits at the bottom.
 */
fun List<TripItemRow>.toPlan(start: LocalDate?, end: LocalDate?): TripPlan {
    val byDay = mutableMapOf<LocalDate, MutableList<TripItemRow>>()
    val undated = mutableListOf<TripItemRow>()
    forEach { row ->
        val date = LocalTimes.dateOf(row.item.startLocal) ?: LocalTimes.dateOf(row.item.endLocal)
        if (date == null) undated += row else byDay.getOrPut(date) { mutableListOf() } += row
    }

    val dates = TreeSet(byDay.keys)
    dates += datesBetween(start, end)

    return TripPlan(
        days = dates.map { date -> TripDay(date, byDay[date].orEmpty().sortedWith(withinDay)) },
        undated = undated.sortedBy { it.title.lowercase() },
    )
}

private fun datesBetween(start: LocalDate?, end: LocalDate?): List<LocalDate> {
    val first = start ?: end ?: return emptyList()
    val last = end ?: first
    if (last.isBefore(first)) return listOf(first)
    return generateSequence(first) { it.plusDays(1) }
        .takeWhile { !it.isAfter(last) }
        .take(MAX_PLANNED_DAYS)
        .toList()
}

/**
 * Within a day: anything with a time of day first, in time order, then the rest in the order they
 * were added, which is the order they were planned in.
 */
private val withinDay = compareBy<TripItemRow>(
    { LocalTimes.timeOfDay(it.item.startLocal).isEmpty() },
    { LocalTimes.timeOfDay(it.item.startLocal) },
    { it.item.meta.createdAt },
)

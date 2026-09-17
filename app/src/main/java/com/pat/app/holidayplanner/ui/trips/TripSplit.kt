package com.pat.app.holidayplanner.ui.trips

import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.domain.LocalTimes
import java.time.LocalDate

/** Trips split the way you think about them: what is coming, and what already happened. */
data class TripSplit(val upcoming: List<TripEntity>, val past: List<TripEntity>)

/**
 * A trip counts as upcoming until the day it ends has passed, so you are not looking at "past
 * trips" while still on one. A trip with no end date has not happened yet by definition.
 */
fun List<TripEntity>.splitAround(today: LocalDate): TripSplit {
    val (past, upcoming) = partition { trip ->
        val end = LocalTimes.parseDate(trip.endDate) ?: return@partition false
        end.isBefore(today)
    }
    return TripSplit(
        upcoming = upcoming.sortedWith(byStartDate),
        // Most recent first: last year's trip is more interesting than one from a decade ago.
        past = past.sortedWith(byStartDate).reversed(),
    )
}

/** Undated trips lead, because they are the ones still being planned. */
private val byStartDate = compareBy<TripEntity>(
    { LocalTimes.parseDate(it.startDate) ?: LocalDate.MIN },
    { it.name.lowercase() },
)

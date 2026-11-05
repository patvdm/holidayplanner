package com.pat.app.holidayplanner.ui.map

import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.domain.LocalTimes
import com.pat.app.holidayplanner.domain.TripItemType
import java.time.LocalDate

/** What one trip day puts on the map: the spots planned on it, and the routes walked on it. */
data class DayPlan(val spotIds: Set<String>, val routeIds: Set<String>)

/**
 * The spots and routes on [day], the same day the trip screen files each item under: its start,
 * or failing that its end. Flights have no spot to show and are left out.
 */
fun List<TripItemEntity>.dayPlan(day: LocalDate): DayPlan {
    val onDay = filter { (LocalTimes.dateOf(it.startLocal) ?: LocalTimes.dateOf(it.endLocal)) == day }
    return DayPlan(
        spotIds = onDay.filter { it.type == TripItemType.SPOT && it.spotId.isNotEmpty() }.map { it.spotId }.toSet(),
        routeIds = onDay.filter { it.type == TripItemType.ROUTE && it.routeId.isNotEmpty() }.map { it.routeId }.toSet(),
    )
}

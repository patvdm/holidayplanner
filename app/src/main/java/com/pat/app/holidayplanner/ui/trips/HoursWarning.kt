package com.pat.app.holidayplanner.ui.trips

import com.pat.app.holidayplanner.data.local.RouteStopHours
import com.pat.app.holidayplanner.data.local.TripItemRow
import com.pat.app.holidayplanner.domain.LocalTimes
import com.pat.app.holidayplanner.domain.OpenPeriod
import com.pat.app.holidayplanner.domain.OpeningHours
import com.pat.app.holidayplanner.domain.TripItemType
import java.time.DayOfWeek

/** Something planned for when the place is shut, as far as Google's hours say. */
sealed interface HoursWarning {

    /** A spot planned at a time it is closed, with what it does open that day, if anything. */
    data class ClosedAt(val time: String, val thatDay: List<OpenPeriod>) : HoursWarning

    /** A spot planned for a day it does not open at all. */
    data class ClosedThatDay(val day: DayOfWeek) : HoursWarning

    /** Stops on a route that do not open on the day it is walked. */
    data class StopsClosed(val day: DayOfWeek, val names: List<String>) : HoursWarning
}

/**
 * Whether [row] is planned for when its place is shut. Only a check against known hours: a spot
 * Google has no hours for, or one not looked up yet, never warns. A route's stops have no times of
 * their own, so a route only warns about a stop that is shut all that day.
 */
fun hoursWarning(row: TripItemRow, stopHours: Map<String, List<RouteStopHours>>): HoursWarning? {
    val item = row.item
    val date = LocalTimes.dateOf(item.startLocal) ?: return null
    return when (item.type) {
        TripItemType.SPOT ->
            spotWarning(OpeningHours.parse(row.spotOpeningHours.orEmpty()), item.startLocal, date.dayOfWeek)
        TripItemType.ROUTE -> stopHours[item.routeId].orEmpty()
            .filter { OpeningHours.parse(it.openingHours)?.closedAllDay(date.dayOfWeek) == true }
            .map { it.spotName }
            .takeIf { it.isNotEmpty() }
            ?.let { HoursWarning.StopsClosed(date.dayOfWeek, it) }
        TripItemType.FLIGHT -> null
    }
}

private fun spotWarning(hours: OpeningHours?, startLocal: String, day: DayOfWeek): HoursWarning? {
    if (hours == null) return null
    val at = LocalTimes.parse(startLocal)?.toLocalTime()
    return when {
        hours.closedAllDay(day) -> HoursWarning.ClosedThatDay(day)
        at != null && hours.isOpenAt(day, at) == false ->
            HoursWarning.ClosedAt(LocalTimes.timeOfDay(startLocal), hours.on(day))
        else -> null
    }
}

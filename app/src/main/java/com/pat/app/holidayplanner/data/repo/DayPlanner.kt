package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.TripEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Turns a handful of spots into a dated day route on a trip.
 *
 * It exists so the screens that offer "Plan a day" depend on one thing rather than on both the trip
 * and the route repositories, neither of which they otherwise need.
 */
class DayPlanner(
    private val trips: TripRepository,
    private val routes: RouteRepository,
) {

    /** The trips a day could belong to. */
    fun observeTrips(): Flow<List<TripEntity>> = trips.observeTrips()

    /** Creates the route and puts [spotIds] on it in order, returning the new trip item. */
    suspend fun planDay(
        tripId: String,
        title: String,
        cityId: String,
        date: LocalDate?,
        spotIds: List<String>,
    ): String {
        val itemId = trips.addDayRoute(tripId, title, cityId, date)
        routes.addStops(itemId, spotIds)
        return itemId
    }
}

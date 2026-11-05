package com.pat.app.holidayplanner.ui.map

import com.pat.app.holidayplanner.data.repo.LinkRepository
import com.pat.app.holidayplanner.data.repo.RouteRepository
import com.pat.app.holidayplanner.data.repo.SpotPinRepository
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.data.repo.TripRepository

/** Everything the map reads from, handed over as one. */
class MapSources(
    val pins: SpotPinRepository,
    val spots: SpotRepository,
    val links: LinkRepository,
    val routes: RouteRepository,
    val trips: TripRepository,
)

package com.pat.app.holidayplanner.data.repo

import android.util.Log
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.dao.SpotDao
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.remote.PlacesSearch
import com.pat.app.holidayplanner.domain.OpeningHours
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private const val TAG = "OpeningHoursBackfill"

/**
 * At most this many hours lookups a day from one phone. Google bills them at its dearest tier, with
 * 1,000 a month free; this keeps a first fill of a few hundred spots spread over days and far below
 * any daily quota, and leaves the month's allowance with room to spare.
 */
private const val DAILY_CAP = 60

/** A breath between requests, so a first fill is never a burst. */
private const val PAUSE_MS = 1_500L

/**
 * Asks Google when each spot is open, while the app is open: spots never asked first, then those
 * asked more than a season ago. A spot saved without a Google place id is looked up by name near
 * its position first, which Google does not charge for. Every answer is written, an unknown one
 * too, so nothing is asked twice; a failure is left for the next start.
 */
class OpeningHoursBackfill(
    private val spotDao: SpotDao,
    private val spots: SpotRepository,
    private val places: PlacesSearch,
    private val prefs: AppPreferences,
) {

    /** Ids tried since the process started, so an offline phone does not loop on the same rows. */
    private val tried = mutableSetOf<String>()

    /** Never returns while the app lives. Call it from a scope that outlives every screen. */
    suspend fun watch() {
        if (!places.isConfigured) return
        spotDao.observeHoursCandidates().conflate().collect { candidates ->
            val today = LocalDate.now()
            candidates
                .filter { it.meta.id !in tried && it.needsHours(today) }
                .sortedBy { it.openingHours.isNotBlank() }
                .forEach { spot ->
                    if (!spend(today)) return@collect
                    tried += spot.meta.id
                    lookUp(spot, today)
                    delay(PAUSE_MS)
                }
        }
    }

    private suspend fun lookUp(spot: SpotEntity, today: LocalDate) {
        placeIdOf(spot)
            .mapCatching { placeId ->
                // Nothing by that name here: Google has no hours to give, and that is an answer.
                if (placeId == null) OpeningHours.unknown(today) else places.openingHours(placeId).getOrThrow()
            }
            .onSuccess { spots.setOpeningHours(spot.meta.id, it.encode()) }
            .onFailure { Log.w(TAG, "${spot.name}: left for next time (${it.message})") }
    }

    /** The spot's own Google id, or the one found by its name where it stands. */
    private suspend fun placeIdOf(spot: SpotEntity): Result<String?> {
        if (spot.googlePlaceId.isNotBlank()) return Result.success(spot.googlePlaceId)
        val lat = spot.lat
        val lng = spot.lng
        if (lat == null || lng == null) return Result.success(null)
        return places.findPlaceId(spot.name, lat, lng)
    }

    /** Counts one lookup against today's cap; false once it is spent. */
    private suspend fun spend(today: LocalDate): Boolean {
        val saved = prefs.hoursLookups.first().split(' ')
        val count = if (saved.firstOrNull() == today.toString()) saved.getOrNull(1)?.toIntOrNull() ?: 0 else 0
        if (count >= DAILY_CAP) return false
        prefs.setHoursLookups("$today ${count + 1}")
        return true
    }
}

private fun SpotEntity.needsHours(today: LocalDate): Boolean =
    OpeningHours.parse(openingHours)?.isStale(today) ?: true

package com.pat.app.holidayplanner.data.repo

import android.util.Log
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.dao.SpotDao
import com.pat.app.holidayplanner.data.remote.PlacesSearch
import com.pat.app.holidayplanner.domain.OpeningHours
import com.pat.app.holidayplanner.domain.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val TAG = "OpeningHoursLookup"

/** A run over many spots skips those checked this recently, so asking twice in a row costs nothing. */
const val HOURS_BULK_SKIP_DAYS = 7L

/** A breath between requests in a run, so a city of fifty spots is never a burst. */
private const val PAUSE_MS = 1_000L

/**
 * One run over the spots of a trip or a city: how far it has got, how many came back with hours,
 * and how many Google could not be reached for.
 */
data class HoursRun(
    val runId: String,
    val name: String,
    val total: Int,
    val done: Int = 0,
    val found: Int = 0,
    val unreached: Int = 0,
) {
    val finished: Boolean get() = done >= total
}

/**
 * Asks Google when spots are open, only ever because somebody asked: one spot from its screen, or
 * every spot of a trip or a city. Hours are billed at Google's dearest tier (1,000 a month free), so
 * nothing runs in the background. A spot saved without a Google place id is found by name near its
 * position first, which Google does not charge for.
 *
 * Lookups run in a scope of their own rather than a screen's, so leaving the screen does not cut a
 * run short. Every answer is written, an unknown one too.
 */
class OpeningHoursLookup(
    private val spotDao: SpotDao,
    private val spots: SpotRepository,
    private val places: PlacesSearch,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val busyIds = MutableStateFlow<Set<String>>(emptySet())
    private val failedIds = MutableStateFlow<Set<String>>(emptySet())
    private val runState = MutableStateFlow<HoursRun?>(null)

    /** False without a Places key, in which case there is nothing to offer. */
    val isAvailable: Boolean get() = places.isConfigured

    /** Spots being looked up one at a time right now. */
    val busy: StateFlow<Set<String>> = busyIds.asStateFlow()

    /** Spots whose last lookup could not reach Google, until they are tried again. */
    val failed: StateFlow<Set<String>> = failedIds.asStateFlow()

    /** The run over a trip or a city, while it goes and once it is done, until dismissed. */
    val run: StateFlow<HoursRun?> = runState.asStateFlow()

    /** One spot, now, whatever it had before: hours found earlier, none, or not asked yet. */
    fun lookUp(spotId: String) {
        if (spotId in busyIds.value) return
        busyIds.update { it + spotId }
        failedIds.update { it - spotId }
        scope.launch {
            if (spotDao.find(spotId)?.let { fetch(it) } == null) failedIds.update { it + spotId }
            busyIds.update { it - spotId }
        }
    }

    /** The spots of a trip, on a day or as a stop on one of its routes, that a run would ask about. */
    suspend fun dueOnTrip(tripId: String): List<SpotEntity> = spotDao.onTrip(tripId).dueForLookup(LocalDate.now())

    /** The spots of a city that a run would ask about. */
    suspend fun dueInCity(cityId: String): List<SpotEntity> = spotDao.inCity(cityId).dueForLookup(LocalDate.now())

    /** Looks up [due] one by one. Ignored while another run is still going: one at a time. */
    fun lookUpAll(runId: String, name: String, due: List<SpotEntity>) {
        if (due.isEmpty() || runState.value?.finished == false) return
        runState.value = HoursRun(runId, name, total = due.size)
        scope.launch {
            due.forEach { spot ->
                val hours = fetch(spot)
                runState.update { run ->
                    run?.copy(
                        done = run.done + 1,
                        found = run.found + if (hours != null && !hours.unknown) 1 else 0,
                        unreached = run.unreached + if (hours == null) 1 else 0,
                    )
                }
                delay(PAUSE_MS)
            }
        }
    }

    /** Clears a finished run's summary. */
    fun dismissRun() {
        if (runState.value?.finished == true) runState.value = null
    }

    /** What Google answered, unknown hours included; null when it could not be reached. */
    private suspend fun fetch(spot: SpotEntity): OpeningHours? {
        val today = LocalDate.now()
        return placeIdOf(spot)
            .mapCatching { placeId ->
                // Nothing by that name here: Google has no hours to give, and that is an answer.
                if (placeId == null) OpeningHours.unknown(today) else places.openingHours(placeId).getOrThrow()
            }
            .onSuccess { spots.setOpeningHours(spot.meta.id, it.encode()) }
            .onFailure { Log.w(TAG, "${spot.name}: not reached (${it.message})") }
            .getOrNull()
    }

    /** The spot's own Google id, or the one found by its name where it stands. */
    private suspend fun placeIdOf(spot: SpotEntity): Result<String?> {
        if (spot.googlePlaceId.isNotBlank()) return Result.success(spot.googlePlaceId)
        val lat = spot.lat
        val lng = spot.lng
        if (lat == null || lng == null) return Result.success(null)
        return places.findPlaceId(spot.name, lat, lng)
    }
}

/** Whether Google can be asked about this spot at all: it needs an id, or a position to search near. */
val SpotEntity.canLookUpHours: Boolean get() = googlePlaceId.isNotBlank() || (lat != null && lng != null)

/**
 * The spots a run asks about, high priority first: those Google can be asked about, less the ones
 * checked in the last [HOURS_BULK_SKIP_DAYS] days.
 */
fun List<SpotEntity>.dueForLookup(today: LocalDate): List<SpotEntity> = this
    .filter { it.canLookUpHours }
    .filterNot { spot ->
        OpeningHours.parse(spot.openingHours)?.checked?.isAfter(today.minusDays(HOURS_BULK_SKIP_DAYS)) == true
    }
    .sortedBy { it.priority != Priority.HIGH }

package com.pat.app.holidayplanner.data.repo

import kotlinx.coroutines.flow.first
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import android.util.Log
import com.pat.app.holidayplanner.data.remote.PictureSource
import com.pat.app.holidayplanner.domain.NO_PICTURE
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate

private const val TAG = "PictureBackfill"

/** A breath between requests: Wikimedia answers a burst with "too many requests" and nothing else. */
private const val PAUSE_MS = 1_000L

/** The one-time second search asks two Wikipedias per spot, so it goes more gently still. */
private const val RETRY_PAUSE_MS = 3_000L

/** Rows handled per emission; the rest arrive on the next one, which the writes trigger. */
private const val MAX_PER_ROUND = 25

/** One row that has no picture yet and what to ask for. */
private data class Wanted(
    val id: String,
    val lookup: suspend () -> Result<String?>,
    val store: suspend (String) -> Unit,
)

/**
 * Finds a picture for every country, city and spot that has none, as long as the app is open.
 *
 * It watches the tables rather than running once, so a spot shared five minutes from now gets its
 * picture as soon as it is saved, and rows pulled from the other phone are covered too. Every row
 * ends up with either a picture or [NO_PICTURE], so nothing is searched for twice; a network
 * failure leaves the row blank and is not retried until the next start.
 */
class PictureBackfill(
    private val countries: CountryRepository,
    private val cities: CityRepository,
    private val spots: SpotRepository,
    private val source: PictureSource,
    private val prefs: AppPreferences,
) {

    /** Ids tried since the process started, so an offline phone does not loop on the same rows. */
    private val tried = mutableSetOf<String>()

    private val wanted: Flow<List<Wanted>> = combine(
        countries.observeWithoutPicture(),
        cities.observeWithoutPicture(),
        spots.observeWithImageUrl(""),
    ) { noCountry, noCity, noSpot ->
        // Countries and cities first: they are few, and they head the screens people open first.
        noCountry.map { country ->
            Wanted(
                id = country.meta.id,
                lookup = { source.forPlace(country.name, within = "") },
                store = { countries.setImageUrl(country.meta.id, it) },
            )
        } + noCity.map { city ->
            Wanted(
                id = city.cityId,
                lookup = { source.forPlace(city.cityName, within = city.countryName) },
                store = { cities.setImageUrl(city.cityId, it) },
            )
        } + noSpot.map { row ->
            Wanted(
                id = row.spot.meta.id,
                lookup = { source.forSpot(row.spot.name, row.cityName, row.countryName, row.isoCode) },
                store = { spots.setFoundImageUrl(row.spot.meta.id, it) },
            )
        }
    }

    /** Never returns while the app lives. Call it from a scope that outlives every screen. */
    suspend fun watch() {
        retryEmptyHandedSpotsOnce()
        wanted.conflate().collect { batch ->
            batch.filter { it.id !in tried }.take(MAX_PER_ROUND).forEach { row ->
                tried += row.id
                fetch(row)
                delay(PAUSE_MS)
            }
        }
    }

    /**
     * Spots searched before the search learned to try the country's own language are searched once
     * more. Slowly, since Wikipedia refuses bursts; if it refuses anyway, the rest wait for the next
     * start rather than being written off. A spot still not found keeps NO_PICTURE, so nothing is
     * written for it and nothing syncs.
     */
    private suspend fun retryEmptyHandedSpotsOnce() {
        if (prefs.spotPictureRetryDone.first()) return
        var complete = true
        spots.observeWithImageUrl(NO_PICTURE).first().forEach { row ->
            source.forSpot(row.spot.name, row.cityName, row.countryName, row.isoCode)
                .onSuccess { url -> if (url != null) spots.setFoundImageUrl(row.spot.meta.id, url) }
                .onFailure { complete = false }
            delay(RETRY_PAUSE_MS)
        }
        if (complete) prefs.markSpotPictureRetryDone()
        Log.d(TAG, "second search for spots without a picture ${if (complete) "done" else "cut short"}")
    }

    private suspend fun fetch(row: Wanted) {
        row.lookup().onSuccess { url ->
            row.store(url ?: NO_PICTURE)
            Log.d(TAG, "${row.id}: ${url ?: "nothing found"}")
        }.onFailure {
            Log.w(TAG, "${row.id}: left for next time (${it.message})")
        }
    }
}

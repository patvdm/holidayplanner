package com.pat.app.holidayplanner.data.repo

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
) {

    /** Ids tried since the process started, so an offline phone does not loop on the same rows. */
    private val tried = mutableSetOf<String>()

    private val wanted: Flow<List<Wanted>> = combine(
        countries.observeWithoutPicture(),
        cities.observeWithoutPicture(),
        spots.observeWithoutPicture(),
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
                lookup = { source.forSpot(row.spot.name, row.cityName, row.countryName) },
                store = { spots.setImageUrl(row.spot.meta.id, it) },
            )
        }
    }

    /** Never returns while the app lives. Call it from a scope that outlives every screen. */
    suspend fun watch() {
        wanted.conflate().collect { batch ->
            batch.filter { it.id !in tried }.take(MAX_PER_ROUND).forEach { row ->
                tried += row.id
                fetch(row)
                delay(PAUSE_MS)
            }
        }
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

package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.domain.PictureOrigin
import com.pat.app.holidayplanner.data.remote.SpotPictureQuery
import com.pat.app.holidayplanner.data.local.CityOption
import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.SpotListRow
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

/** The one-time re-check asks several sources per spot, so it goes more gently still. */
private const val RECHECK_PAUSE_MS = 3_000L

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
    private val links: LinkRepository,
) {

    /** Ids tried since the process started, so an offline phone does not loop on the same rows. */
    private val tried = mutableSetOf<String>()

    private val wanted: Flow<List<Wanted>> = combine(
        countries.observeWithoutPicture(),
        cities.observeWithoutPicture(),
        spots.observeWithImageUrl(""),
    ) { noCountry, noCity, noSpot ->
        // Countries and cities first: they are few, and they head the screens people open first.
        noCountry.map(::wantedCountry) + noCity.map(::wantedCity) + noSpot.map { row ->
            Wanted(
                id = row.spot.meta.id,
                lookup = { source.forSpot(queryFor(row)) },
                store = { spots.setPicture(row.spot.meta.id, it, PictureOrigin.AUTO) },
            )
        }
    }

    private fun wantedCountry(country: CountryEntity) = Wanted(
        id = country.meta.id,
        lookup = { source.forPlace(country.name, within = "") },
        store = { countries.setPicture(country.meta.id, it, PictureOrigin.AUTO) },
    )

    private fun wantedCity(city: CityOption) = Wanted(
        id = city.cityId,
        lookup = { source.forPlace(city.cityName, within = city.countryName) },
        store = { cities.setPicture(city.cityId, it, PictureOrigin.AUTO) },
    )

    /** Never returns while the app lives. Call it from a scope that outlives every screen. */
    suspend fun watch() {
        retryPlacesWithoutPictureOnce()
        recheckAutomaticSpotPicturesOnce()
        wanted.conflate().collect { batch ->
            batch.filter { it.id !in tried }.take(MAX_PER_ROUND).forEach { row ->
                tried += row.id
                fetch(row)
                delay(PAUSE_MS)
            }
        }
    }

    /**
     * Countries and cities the search came back empty for are looked for once more, now that it
     * reads the banner each Wikivoyage page names and steps past pages that only list the places
     * sharing a name (Georgia). Only a find is written: one still without a picture keeps its flag.
     */
    private suspend fun retryPlacesWithoutPictureOnce() {
        if (prefs.placePictureRetryDone.first()) return
        var complete = true
        var found = 0
        val missing = countries.observeNoneFound().first().map(::wantedCountry) +
            cities.observeNoneFound().first().map(::wantedCity)
        missing.forEach { row ->
            row.lookup()
                .onSuccess { url ->
                    if (url != null) {
                        row.store(url)
                        found++
                    }
                }
                .onFailure { complete = false }
            delay(RECHECK_PAUSE_MS)
        }
        if (complete) prefs.markPlacePictureRetryDone()
        Log.d(TAG, "retry of places without a picture ${if (complete) "done" else "cut short"}, $found found")
    }

    /**
     * Every spot whose picture the app chose itself, or could not find, is searched once more with
     * the search as it now is: by position as well as name, and on the spot's own website. A
     * wrong match from before (a Waag in Paramaribo for the one in Amsterdam) is replaced or
     * dropped, and gaps get another chance. Pictures somebody chose are never touched. Slowly,
     * since Wikimedia refuses bursts; if it refuses anyway, the rest wait for the next start.
     */
    private suspend fun recheckAutomaticSpotPicturesOnce() {
        if (prefs.spotPictureRecheckDone.first()) return
        var complete = true
        var changed = 0
        spots.observeAllRows().first()
            .filter { PictureOrigin.of(it.spot.imageUrl, it.spot.imageSource) == PictureOrigin.AUTO }
            .forEach { row ->
                source.forSpot(queryFor(row))
                    .onSuccess { url ->
                        val found = url ?: NO_PICTURE
                        if (found != row.spot.imageUrl) {
                            spots.setPicture(row.spot.meta.id, found, PictureOrigin.AUTO)
                            changed++
                        }
                    }
                    .onFailure { complete = false }
                delay(RECHECK_PAUSE_MS)
            }
        if (complete) prefs.markSpotPictureRecheckDone()
        Log.d(TAG, "re-check of automatic spot pictures ${if (complete) "done" else "cut short"}, $changed changed")
    }

    private suspend fun queryFor(row: SpotListRow) = SpotPictureQuery(
        name = row.spot.name,
        cityName = row.cityName,
        countryName = row.countryName,
        isoCode = row.isoCode,
        lat = row.spot.lat,
        lng = row.spot.lng,
        websites = links.webUrlsOf(row.spot.meta.id),
    )

    private suspend fun fetch(row: Wanted) {
        row.lookup().onSuccess { url ->
            row.store(url ?: NO_PICTURE)
            Log.d(TAG, "${row.id}: ${url ?: "nothing found"}")
        }.onFailure {
            Log.w(TAG, "${row.id}: left for next time (${it.message})")
        }
    }
}

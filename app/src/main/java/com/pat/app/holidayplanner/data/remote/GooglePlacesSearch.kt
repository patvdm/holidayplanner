package com.pat.app.holidayplanner.data.remote

import java.time.LocalTime
import java.time.LocalDate
import java.time.DayOfWeek
import com.pat.app.holidayplanner.domain.OpenPeriod
import com.pat.app.holidayplanner.domain.OpeningHours
import com.google.android.libraries.places.api.net.SearchByTextRequest
import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.pat.app.holidayplanner.BuildConfig
import com.pat.app.holidayplanner.domain.AddressPart
import java.io.IOException
import kotlinx.coroutines.tasks.await

private const val TAG = "GooglePlacesSearch"

/** How close to a spot's saved position its Google place is looked for, in metres. */
private const val FIND_RADIUS_M = 250.0

/** How far around the current place suggestions are preferred, in metres. */
private const val BIAS_RADIUS_M = 30_000.0

/**
 * The smallest field list that answers "where is this and what is it called". Every extra field can
 * move the request into a more expensive billing tier, so nothing is added here without a reason.
 */
private val PLACE_FIELDS = listOf(
    Place.Field.ID,
    Place.Field.LOCATION,
    Place.Field.FORMATTED_ADDRESS,
    Place.Field.ADDRESS_COMPONENTS,
)

/** Autocomplete collection that returns cities rather than businesses. */

private class GooglePlacesSession(val token: AutocompleteSessionToken) : PlacesSession

class GooglePlacesSearch(private val context: Context) : PlacesSearch {

    override val isConfigured: Boolean = BuildConfig.MAPS_API_KEY.isNotBlank()

    /** Created on first use, so an app that never opens the search never initialises the SDK. */
    private val client: PlacesClient? by lazy { createClient() }

    override fun newSession(): PlacesSession = GooglePlacesSession(AutocompleteSessionToken.newInstance())

    override suspend fun predictions(
        query: String,
        session: PlacesSession,
        biasLat: Double?,
        biasLng: Double?,
        countryIso: String,
    ): Result<List<PlacePrediction>> {
        val places = client ?: return notConfigured()
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken((session as? GooglePlacesSession)?.token)
            .apply {
                // Searching inside a country one is already looking at: a bar in Split, not in Seattle.
                if (countryIso.isNotBlank()) setCountries(listOf(countryIso))
                if (biasLat != null && biasLng != null) {
                    setLocationBias(CircularBounds.newInstance(LatLng(biasLat, biasLng), BIAS_RADIUS_M))
                }
            }
            .build()

        return runCatchingPlaces("predictions") {
            places.findAutocompletePredictions(request).await().autocompletePredictions.map { prediction ->
                PlacePrediction(
                    googlePlaceId = prediction.placeId,
                    primaryText = prediction.getPrimaryText(null).toString(),
                    secondaryText = prediction.getSecondaryText(null).toString(),
                )
            }
        }
    }

    override suspend fun details(googlePlaceId: String, session: PlacesSession): Result<PlaceDetails> {
        val places = client ?: return notConfigured()
        // The same session token as the typing that led here, so Google bills one session.
        val request = FetchPlaceRequest.builder(googlePlaceId, PLACE_FIELDS)
            .setSessionToken((session as? GooglePlacesSession)?.token)
            .build()

        return runCatchingPlaces("details") {
            places.fetchPlace(request).await().place.toDetails(googlePlaceId)
        }
    }

    override suspend fun openingHours(googlePlaceId: String): Result<OpeningHours> {
        val places = client ?: return notConfigured()
        val request = FetchPlaceRequest.builder(googlePlaceId, listOf(Place.Field.OPENING_HOURS)).build()
        return runCatchingPlaces("hours") {
            places.fetchPlace(request).await().place.openingHours.toOpeningHours(LocalDate.now())
        }
    }

    override suspend fun findPlaceId(name: String, lat: Double, lng: Double): Result<String?> {
        val places = client ?: return notConfigured()
        val request = SearchByTextRequest.builder(name, listOf(Place.Field.ID))
            .setLocationBias(CircularBounds.newInstance(LatLng(lat, lng), FIND_RADIUS_M))
            .setMaxResultCount(1)
            .build()
        return runCatchingPlaces("find") {
            places.searchByText(request).await().places.firstOrNull()?.id
        }
    }

    private fun createClient(): PlacesClient? {
        if (!isConfigured) return null
        return try {
            if (!Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(context.applicationContext, BuildConfig.MAPS_API_KEY)
            }
            Places.createClient(context.applicationContext)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "could not start the Places SDK: ${e.message}")
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "could not start the Places SDK: ${e.message}")
            null
        }
    }

    /**
     * Quota, key and network failures all arrive here and are all handled the same way: the search
     * is unavailable, so the caller offers manual entry instead.
     */
    private inline fun <T> runCatchingPlaces(action: String, block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: ApiException) {
        Log.w(TAG, "$action refused (status ${e.statusCode}): ${e.message}")
        Result.failure(e)
    } catch (e: IOException) {
        Log.w(TAG, "$action failed: ${e.message}")
        Result.failure(e)
    } catch (e: IllegalStateException) {
        Log.w(TAG, "$action failed: ${e.message}")
        Result.failure(e)
    }

    private fun <T> notConfigured(): Result<T> =
        Result.failure(IllegalStateException("No Places API key is configured"))
}

private fun Place.toDetails(requestedId: String) = PlaceDetails(
    googlePlaceId = id ?: requestedId,
    address = formattedAddress.orEmpty(),
    lat = location?.latitude,
    lng = location?.longitude,
    components = addressComponents?.asList().orEmpty().map { component ->
        AddressPart(
            types = component.types.orEmpty(),
            name = component.name.orEmpty(),
            shortName = component.shortName.orEmpty(),
        )
    },
)

/**
 * Google's periods in our own form. Always open comes as one period from Sunday midnight with no
 * close; a period missing either end otherwise is dropped rather than guessed at.
 */
private fun com.google.android.libraries.places.api.model.OpeningHours?.toOpeningHours(today: LocalDate): OpeningHours {
    val periods = this?.periods.orEmpty()
    if (periods.size == 1 && periods.first().close == null) {
        return OpeningHours(today, always = true, periods = emptyList())
    }
    return OpeningHours(
        checked = today,
        always = false,
        periods = periods.mapNotNull { period ->
            val open = period.open ?: return@mapNotNull null
            val close = period.close ?: return@mapNotNull null
            OpenPeriod(
                openDay = open.day.toJava(),
                open = LocalTime.of(open.time.hours, open.time.minutes),
                closeDay = close.day.toJava(),
                close = LocalTime.of(close.time.hours % HOURS_PER_DAY, close.time.minutes),
            )
        },
    )
}

private const val HOURS_PER_DAY = 24

private fun com.google.android.libraries.places.api.model.DayOfWeek.toJava(): DayOfWeek = when (this) {
    com.google.android.libraries.places.api.model.DayOfWeek.MONDAY -> DayOfWeek.MONDAY
    com.google.android.libraries.places.api.model.DayOfWeek.TUESDAY -> DayOfWeek.TUESDAY
    com.google.android.libraries.places.api.model.DayOfWeek.WEDNESDAY -> DayOfWeek.WEDNESDAY
    com.google.android.libraries.places.api.model.DayOfWeek.THURSDAY -> DayOfWeek.THURSDAY
    com.google.android.libraries.places.api.model.DayOfWeek.FRIDAY -> DayOfWeek.FRIDAY
    com.google.android.libraries.places.api.model.DayOfWeek.SATURDAY -> DayOfWeek.SATURDAY
    com.google.android.libraries.places.api.model.DayOfWeek.SUNDAY -> DayOfWeek.SUNDAY
}

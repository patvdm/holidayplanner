package com.pat.app.holidayplanner.data.remote

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
private const val CITIES_FILTER = "(cities)"

private class GooglePlacesSession(val token: AutocompleteSessionToken) : PlacesSession

class GooglePlacesSearch(private val context: Context) : PlacesSearch {

    override val isConfigured: Boolean = BuildConfig.MAPS_API_KEY.isNotBlank()

    /** Created on first use, so an app that never opens the search never initialises the SDK. */
    private val client: PlacesClient? by lazy { createClient() }

    override fun newSession(): PlacesSession = GooglePlacesSession(AutocompleteSessionToken.newInstance())

    override suspend fun predictions(
        query: String,
        mode: PlaceSearchMode,
        session: PlacesSession,
        biasLat: Double?,
        biasLng: Double?,
    ): Result<List<PlacePrediction>> {
        val places = client ?: return notConfigured()
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken((session as? GooglePlacesSession)?.token)
            .apply {
                if (mode == PlaceSearchMode.CITY) setTypesFilter(listOf(CITIES_FILTER))
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

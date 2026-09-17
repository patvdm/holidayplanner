package com.pat.app.holidayplanner.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.pat.app.holidayplanner.domain.GeoPoint
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val TAG = "ReverseGeocoder"

/** What a set of coordinates turns out to be. Either field may be empty. */
data class GeoPlace(val isoCode: String, val cityName: String)

/** Turns coordinates into a country and a town, and an address back into coordinates. */
interface ReverseGeocoder {
    suspend fun lookup(lat: Double, lng: Double): GeoPlace?

    /**
     * Coordinates for a written address, or null when it cannot be placed.
     *
     * Some Google Maps links carry a full address but no position at all, encoding a place hash
     * instead, so the address is the only thing left to work from.
     */
    suspend fun locate(query: String): GeoPoint?
}

/**
 * Uses the platform geocoder, which costs nothing and needs no API key, unlike the Places calls.
 *
 * A shared Maps link carries coordinates but rarely names its country, and reading the country off
 * the end of an address only works when Google bothered to include one. Coordinates always know.
 */
class AndroidReverseGeocoder(private val context: Context) : ReverseGeocoder {

    override suspend fun lookup(lat: Double, lng: Double): GeoPlace? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) {
            Log.d(TAG, "no geocoder on this device")
            return@withContext null
        }
        val geocoder = Geocoder(context, Locale.ENGLISH)
        try {
            addressAt(geocoder, lat, lng)?.toGeoPlace()
        } catch (e: IOException) {
            Log.w(TAG, "lookup failed: ${e.message}")
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "not usable coordinates: ${e.message}")
            null
        }
    }

    override suspend fun locate(query: String): GeoPoint? = withContext(Dispatchers.IO) {
        if (query.isBlank() || !Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.ENGLISH)
        try {
            addressFor(geocoder, query)?.let { GeoPoint(it.latitude, it.longitude) }
        } catch (e: IOException) {
            Log.w(TAG, "locate failed: ${e.message}")
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "not a usable address: ${e.message}")
            null
        }
    }

    private suspend fun addressFor(geocoder: Geocoder, query: String): Address? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(query, 1) { results ->
                    continuation.resume(results.firstOrNull())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(query, 1)?.firstOrNull()
        }

    private suspend fun addressAt(geocoder: Geocoder, lat: Double, lng: Double): Address? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(lat, lng, 1) { results ->
                    continuation.resume(results.firstOrNull())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
        }
}

/** The locality is the town; the sub-admin area covers villages that have no locality of their own. */
private fun Address.toGeoPlace() = GeoPlace(
    isoCode = countryCode.orEmpty().uppercase(Locale.ENGLISH),
    cityName = locality ?: subAdminArea ?: adminArea.orEmpty(),
)

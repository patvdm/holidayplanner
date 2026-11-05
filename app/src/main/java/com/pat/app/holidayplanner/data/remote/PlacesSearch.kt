package com.pat.app.holidayplanner.data.remote

import com.pat.app.holidayplanner.domain.OpeningHours
import com.pat.app.holidayplanner.domain.AddressPart

/** One suggestion while typing. [secondaryText] is the "Den Haag, Netherlands" line under the name. */
data class PlacePrediction(
    val googlePlaceId: String,
    val primaryText: String,
    val secondaryText: String,
)

/**
 * The details fetched once a suggestion is picked. There is no name here on purpose: the display
 * name is a separately billed field, and the prediction's own text says the same thing for free.
 */
data class PlaceDetails(
    val googlePlaceId: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val components: List<AddressPart>,
)

/** Opaque handle tying one round of typing to the pick that follows, which is how Google bills it. */
interface PlacesSession

/**
 * Autocomplete against Google Places. The second of only two things in the app that touch the
 * network, and the only one that costs money per call.
 *
 * Every call returns a [Result] because a refusal is a normal state here: the key may be missing,
 * the daily quota may be spent, or the phone may be offline. The sheet then falls back to letting
 * the person type a name, so a spot can always be added.
 */
interface PlacesSearch {

    /** False when no API key is configured, in which case the sheet opens straight into manual mode. */
    val isConfigured: Boolean

    /**
     * Starts a session. Autocomplete calls and the fetch that follows must share one, or Google
     * bills each keystroke separately.
     */
    fun newSession(): PlacesSession

    suspend fun predictions(
        query: String,
        session: PlacesSession,
        biasLat: Double? = null,
        biasLng: Double? = null,
        /** ISO country code to hold the results to, for a search started from a country or a city. */
        countryIso: String = "",
    ): Result<List<PlacePrediction>>

    /** Fetches the picked place with the smallest field list that answers where it is. */
    suspend fun details(googlePlaceId: String, session: PlacesSession): Result<PlaceDetails>

    /**
     * The place's weekly hours, with nothing else, since hours are billed at Google's dearest tier:
     * one request on its own keeps every other lookup in the cheap one. Unknown hours when Google
     * has none.
     */
    suspend fun openingHours(googlePlaceId: String): Result<OpeningHours>

    /**
     * Google's id for a spot saved without one, by name close to where it is. Asks for the id alone,
     * which Google does not charge for. Null when nothing matches.
     */
    suspend fun findPlaceId(name: String, lat: Double, lng: Double): Result<String?>
}

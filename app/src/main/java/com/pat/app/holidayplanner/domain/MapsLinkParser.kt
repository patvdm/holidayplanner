package com.pat.app.holidayplanner.domain

import java.net.URLDecoder
import java.net.URI
import java.net.URISyntaxException

/** A place read out of a Google Maps address. Either half may be missing. */
data class MapsPlace(
    val name: String,
    val lat: Double?,
    val lng: Double?,
) {
    val hasCoordinates: Boolean get() = lat != null && lng != null
    val isEmpty: Boolean get() = name.isBlank() && !hasCoordinates
}

/** /maps/place/<name>/... carries the name Google shows for the pin. */
private val PLACE_NAME = Regex("""/maps/place/([^/@?]+)""")

/** The !3d/!4d pair in the data segment is the pin itself, not the viewport. */
private val PIN_COORDS = Regex("""!3d(-?\d+\.?\d*)!4d(-?\d+\.?\d*)""")

/** @lat,lng,zoom is where the camera sits, which is close enough when there is nothing better. */
private val CAMERA_COORDS = Regex("""@(-?\d+\.?\d*),(-?\d+\.?\d*)""")

/** A bare "lat,lng" pair, as used by the q= and query= parameters. */
private val COORD_PAIR = Regex("""^\s*(-?\d+\.?\d*)\s*,\s*(-?\d+\.?\d*)\s*$""")

private const val LOC_PREFIX = "loc:"

/**
 * Reads a place out of a Google Maps URL.
 *
 * Maps writes several shapes depending on how the link was made, and a shared link is usually a
 * short one that has to be followed first. Nothing here fetches anything: it only reads an address
 * that has already been resolved.
 */
object MapsLinkParser {

    fun parse(url: String): MapsPlace {
        val decodedPath = decode(url)
        return MapsPlace(
            name = nameOf(decodedPath),
            lat = coordinates(url)?.first,
            lng = coordinates(url)?.second,
        )
    }

    private fun nameOf(url: String): String {
        val fromPath = PLACE_NAME.find(url)?.groupValues?.get(1)
        if (fromPath != null) return fromPath.replace('+', ' ').trim()

        // A q= that is not coordinates is a search term, which is the next best name.
        val query = queryValue(url, "q") ?: queryValue(url, "query") ?: return ""
        val cleaned = query.removePrefix(LOC_PREFIX)
        return if (COORD_PAIR.matches(cleaned)) "" else cleaned.replace('+', ' ').trim()
    }

    private fun coordinates(url: String): Pair<Double, Double>? =
        PIN_COORDS.find(url)?.toPair()
            ?: queryCoordinates(url)
            ?: CAMERA_COORDS.find(url)?.toPair()

    private fun queryCoordinates(url: String): Pair<Double, Double>? {
        val raw = queryValue(url, "q") ?: queryValue(url, "query") ?: return null
        val match = COORD_PAIR.find(raw.removePrefix(LOC_PREFIX)) ?: return null
        return match.toPair()
    }

    private fun MatchResult.toPair(): Pair<Double, Double>? {
        val lat = groupValues[1].toDoubleOrNull() ?: return null
        val lng = groupValues[2].toDoubleOrNull() ?: return null
        return lat to lng
    }

    private fun queryValue(url: String, name: String): String? {
        val query = try {
            URI(url).rawQuery
        } catch (e: URISyntaxException) {
            url.substringAfter('?', "")
        } ?: return null
        return query.split('&')
            .firstOrNull { it.substringBefore('=').equals(name, ignoreCase = true) }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotEmpty() }
            ?.let(::decode)
    }

    /**
     * A malformed percent escape means the text simply was not encoded, so the original is the
     * right answer and there is nothing to report. Suppressed on this function alone rather than
     * whitelisting IllegalArgumentException everywhere, which would hide real mistakes.
     */
    @Suppress("SwallowedException")
    private fun decode(value: String): String = try {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    } catch (e: IllegalArgumentException) {
        value
    }
}

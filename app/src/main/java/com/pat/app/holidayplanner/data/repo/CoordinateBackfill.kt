package com.pat.app.holidayplanner.data.repo

import android.util.Log
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.dao.SpotDao
import com.pat.app.holidayplanner.data.location.ReverseGeocoder
import com.pat.app.holidayplanner.domain.GeoPoint
import com.pat.app.holidayplanner.domain.MapsLinkParser

private const val TAG = "CoordinateBackfill"

/**
 * Gives a position to spots that have a Google Maps link but no coordinates.
 *
 * Without one a spot is invisible on the map, absent from route ordering and outside every
 * distance, even though the app clearly knows where it is. Two things can go wrong, and each has
 * its own answer: a link that was never followed still has its coordinates buried in it, and a link
 * that Google wrote as a place hash has none at all but does carry a full address, which the
 * platform geocoder will place for free.
 */
class CoordinateBackfill(
    private val dao: SpotDao,
    private val geocoder: ReverseGeocoder,
    private val writer: LocalWriter,
) {

    /** Returns how many spots were given a position. Normally zero. */
    suspend fun run(): Int {
        val broken = dao.withLinkButNoCoordinates()
        if (broken.isEmpty()) return 0

        val stamp = writer.stamp()
        val repaired = broken.mapNotNull { spot ->
            positionOf(spot)?.let { point ->
                spot.copy(
                    lat = point.lat,
                    lng = point.lng,
                    meta = spot.meta.touched(stamp.now, stamp.userId),
                )
            }
        }
        if (repaired.isEmpty()) {
            Log.i(TAG, "${broken.size} spot(s) have no position and nothing to derive one from")
            return 0
        }

        dao.upsert(repaired)
        writer.requestSync()
        Log.i(TAG, "gave a position to ${repaired.size} of ${broken.size} spot(s)")
        return repaired.size
    }

    private suspend fun positionOf(spot: SpotEntity): GeoPoint? {
        val place = MapsLinkParser.parse(spot.mapsUrl)
        if (place.hasCoordinates) return GeoPoint(place.lat!!, place.lng!!)

        // The link named somewhere without saying where it is, so place the words instead. The
        // name inside the link is the fullest, since Google writes the whole address into it.
        val query = listOf(place.name, spot.address, spot.name)
            .firstOrNull { it.isNotBlank() }
            ?: return null
        return geocoder.locate(query)
    }
}

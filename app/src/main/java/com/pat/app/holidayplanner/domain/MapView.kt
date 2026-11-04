package com.pat.app.holidayplanner.domain

/**
 * Where the map was looking: its centre and zoom. Kept on the phone so that opening the map with
 * nothing in particular to show carries on where it was left, rather than zooming out to the world.
 */
data class MapView(val lat: Double, val lng: Double, val zoom: Float) {

    /** "lat,lng,zoom", the form it is stored in. */
    fun format(): String = "$lat,$lng,$zoom"

    companion object {
        private const val PARTS = 3
        private const val MAX_LAT = 90.0
        private const val MAX_LNG = 180.0

        /** Null for anything that is not three numbers in range, so a bad value means "no memory". */
        fun parse(value: String?): MapView? {
            val parts = value?.split(',')?.takeIf { it.size == PARTS } ?: return null
            val lat = parts[0].toDoubleOrNull()
            val lng = parts[1].toDoubleOrNull()
            val zoom = parts[2].toFloatOrNull()
            val valid = lat != null && lng != null && zoom != null &&
                lat in -MAX_LAT..MAX_LAT && lng in -MAX_LNG..MAX_LNG && zoom > 0f
            return if (valid) MapView(lat, lng, zoom) else null
        }
    }
}

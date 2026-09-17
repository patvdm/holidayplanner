package com.pat.app.holidayplanner.domain

/**
 * Enums are stored in the sheet as uppercase strings. Anything unrecognised maps to the safe
 * default instead of failing, so a newer app version can add values without breaking older ones.
 */

enum class Priority {
    MUST, LIKE, MAYBE;

    companion object {
        fun fromSheet(value: String?): Priority = parseEnum(value, MAYBE)
    }
}

enum class SpotStatus {
    UNVISITED, VISITED;

    companion object {
        /**
         * Anything that is not plainly "visited" counts as not yet visited, which also quietly
         * carries over the older WANT and PLANNED values.
         */
        fun fromSheet(value: String?): SpotStatus =
            if (value?.trim().equals(VISITED.name, ignoreCase = true)) VISITED else UNVISITED
    }
}

/**
 * What kind of place a spot is. Optional: plenty of spots are just somewhere to go, and forcing a
 * choice would be busywork, so null means nobody said.
 */
enum class SpotType {
    POI, ACCOMMODATION, RESTAURANT;

    companion object {
        fun fromSheet(value: String?): SpotType? {
            val name = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }
}

enum class LinkSource {
    INSTAGRAM, TIKTOK, YOUTUBE, MAPS, WEB;

    companion object {
        fun fromSheet(value: String?): LinkSource = parseEnum(value, WEB)
    }
}

enum class TripItemType {
    FLIGHT, STAY, TRANSPORT, ACTIVITY, DAY_ROUTE;

    companion object {
        fun fromSheet(value: String?): TripItemType = parseEnum(value, ACTIVITY)
    }
}

enum class TravelMode {
    WALKING, TRANSIT, BICYCLING, DRIVING;

    companion object {
        fun fromSheet(value: String?): TravelMode = parseEnum(value, WALKING)

        /** Travel mode only applies to day routes, so a blank cell stays null. */
        fun fromSheetOrNull(value: String?): TravelMode? =
            value?.takeIf { it.isNotBlank() }?.let { fromSheet(it) }
    }
}

private inline fun <reified E : Enum<E>> parseEnum(value: String?, default: E): E {
    val name = value?.trim() ?: return default
    return enumValues<E>().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: default
}

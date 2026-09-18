package com.pat.app.holidayplanner.domain

/**
 * Enums are stored in the sheet as uppercase strings. Anything unrecognised maps to the safe
 * default instead of failing, so a newer app version can add values without breaking older ones.
 */

/**
 * Whether something is a must. Two values on purpose: a three-way scale was never used as more than
 * "yes, this one" versus everything else, so that is what it became.
 */
enum class Priority {
    HIGH, NORMAL;

    companion object {
        /** The old MUST reads as HIGH; LIKE and MAYBE, and anything unknown, read as NORMAL. */
        fun fromSheet(value: String?): Priority {
            val name = value?.trim().orEmpty()
            return if (name.equals(HIGH.name, true) || name.equals("MUST", true)) HIGH else NORMAL
        }
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
 * What kind of place a spot is. POI is the default, so a spot nobody classified is simply somewhere
 * to go rather than a spot with a missing field.
 */
enum class SpotType {
    POI, ACCOMMODATION, RESTAURANT;

    companion object {
        fun fromSheet(value: String?): SpotType = parseEnum(value, POI)
    }
}

enum class LinkSource {
    INSTAGRAM, TIKTOK, YOUTUBE, MAPS, WEB;

    companion object {
        fun fromSheet(value: String?): LinkSource = parseEnum(value, WEB)
    }
}

/**
 * What can sit on a day of a trip. A stay is a spot of type accommodation, and a walk is a route,
 * so three kinds cover it.
 */
enum class TripItemType {
    FLIGHT, SPOT, ROUTE;

    companion object {
        /**
         * Null for anything not in the list, so rows of the kinds that used to exist are dropped on
         * pull rather than quietly turned into something else.
         */
        fun fromSheetOrNull(value: String?): TripItemType? {
            val name = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }
}

/** How Google Maps is asked to get between stops. Routes in this app are always walked. */
enum class TravelMode {
    WALKING, TRANSIT, BICYCLING, DRIVING,
}

private inline fun <reified E : Enum<E>> parseEnum(value: String?, default: E): E {
    val name = value?.trim() ?: return default
    return enumValues<E>().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: default
}

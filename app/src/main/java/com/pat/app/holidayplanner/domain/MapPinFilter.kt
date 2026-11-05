package com.pat.app.holidayplanner.domain

/**
 * Which pins the map shows. One at a time: food, hotels and drone spots barely overlap, so two at
 * once would mostly mean "everything" with extra steps.
 */
enum class MapPinFilter {
    ALL,
    FOOD,
    STAY,
    DRONE,
    ;

    companion object {
        /** Anything unknown or missing is no filter at all, so a stale setting never hides pins. */
        fun parse(value: String?): MapPinFilter = entries.firstOrNull { it.name == value } ?: ALL
    }
}

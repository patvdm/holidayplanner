package com.pat.app.holidayplanner.domain

/**
 * How a list is ordered. The choice is per screen and stays on the device, because it is a matter
 * of taste rather than shared data. Only [MANUAL] allows dragging, since that is the only mode
 * where the order is something a person decided.
 */
enum class SortMode {
    /** High priority first, then each group in its own order: dragged in a city, newest on the Spots tab. */
    MANUAL,
    RECENT,
    ALPHABETICAL,
}

/** The lists that remember their own sort mode. */
enum class SortScope(val prefKey: String, val options: List<SortMode>) {
    COUNTRIES(
        prefKey = "countries",
        options = listOf(SortMode.MANUAL, SortMode.RECENT, SortMode.ALPHABETICAL),
    ),
    // A saved PRIORITY or HEARTS from before reads as MANUAL through parse.
    SPOTS(
        prefKey = "spots",
        options = listOf(SortMode.MANUAL, SortMode.RECENT, SortMode.ALPHABETICAL),
    ),

    /**
     * Every spot across every city. Nobody dragged this list, so its MANUAL order is high priority
     * first, then the newest.
     */
    ALL_SPOTS(
        prefKey = "all_spots",
        options = listOf(SortMode.MANUAL, SortMode.RECENT, SortMode.ALPHABETICAL),
    ),
    ;

    /** An unknown or unset value falls back to the scope's first option, which is its natural order. */
    fun parse(value: String?): SortMode =
        options.firstOrNull { it.name == value } ?: options.first()
}

package com.pat.app.holidayplanner.domain

/**
 * How a list is ordered. The choice is per screen and stays on the device, because it is a matter
 * of taste rather than shared data. Only [MANUAL] allows dragging, since that is the only mode
 * where the order is something a person decided.
 */
enum class SortMode {
    MANUAL,
    PRIORITY,
    HEARTS,
    RECENT,
    ALPHABETICAL,
}

/** The three lists that remember their own sort mode. */
enum class SortScope(val prefKey: String, val options: List<SortMode>) {
    COUNTRIES(
        prefKey = "countries",
        options = listOf(SortMode.MANUAL, SortMode.PRIORITY, SortMode.RECENT, SortMode.ALPHABETICAL),
    ),
    CITIES(
        prefKey = "cities",
        options = listOf(SortMode.MANUAL, SortMode.PRIORITY, SortMode.RECENT, SortMode.ALPHABETICAL),
    ),
    SPOTS(
        prefKey = "spots",
        options = listOf(
            SortMode.MANUAL,
            SortMode.PRIORITY,
            SortMode.HEARTS,
            SortMode.RECENT,
            SortMode.ALPHABETICAL,
        ),
    ),
    ;

    fun parse(value: String?): SortMode =
        options.firstOrNull { it.name == value } ?: SortMode.MANUAL
}

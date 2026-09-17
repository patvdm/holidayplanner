package com.pat.app.holidayplanner.domain

/** What kind of thing a search result is. Results are grouped under these. */
enum class SearchHitType {
    COUNTRY,
    CITY,
    SPOT,
    LINK,
    TRIP,
    TRIP_ITEM,
    ;

    companion object {
        /** Unknown values are dropped rather than guessed at, the same as every other enum here. */
        fun fromRow(value: String): SearchHitType? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Turns what someone typed into a SQL LIKE pattern.
 *
 * The wildcards have to be escaped or they leak: typing "%" would otherwise match every row in the
 * database, and an underscore would quietly match any single character.
 */
object SearchQuery {

    private const val ESCAPE = "\\"

    /** Blank or whitespace-only input is not a search, and should show nothing rather than all. */
    fun isSearchable(raw: String): Boolean = raw.isNotBlank()

    fun like(raw: String): String {
        val escaped = raw.trim()
            .replace(ESCAPE, ESCAPE + ESCAPE)
            .replace("%", ESCAPE + "%")
            .replace("_", ESCAPE + "_")
        return "%$escaped%"
    }
}

package com.pat.app.holidayplanner.domain

import kotlin.time.Duration.Companion.days

/** What kind of thing showed up in the feed. */
enum class FeedEntryType {
    SPOT,
    LINK,
    TRIP,
    HEART,
    ;

    companion object {
        fun fromRow(value: String): FeedEntryType? = entries.firstOrNull { it.name == value }
    }
}

/** A run of the same kind of thing in the same place, as one line of the strip. */
data class FeedGroup(
    val type: FeedEntryType,
    val place: String,
    val count: Int,
    val newestAt: Long,
)

/**
 * The "this week" window and how its contents collapse into sentences.
 *
 * The strip exists so a handful of additions read as one line rather than a wall of rows, which is
 * the difference between glancing at it and ignoring it.
 */
object FeedGrouping {

    private val WINDOW = 7.days

    /** The earliest creation time still counted as this week. */
    fun since(now: Long): Long = now - WINDOW.inWholeMilliseconds

    /**
     * Collapses entries into one group per kind and place, newest activity first.
     *
     * Entries of an unrecognised kind are dropped rather than lumped together, so a future row type
     * arriving from the sheet cannot make the strip claim something wrong.
     */
    fun <T> group(
        items: List<T>,
        typeOf: (T) -> FeedEntryType?,
        placeOf: (T) -> String,
        createdAtOf: (T) -> Long,
    ): List<FeedGroup> = items
        .mapNotNull { item -> typeOf(item)?.let { Triple(it, placeOf(item).trim(), createdAtOf(item)) } }
        .groupBy { (type, place, _) -> type to place }
        .map { (key, group) ->
            FeedGroup(
                type = key.first,
                place = key.second,
                count = group.size,
                newestAt = group.maxOf { it.third },
            )
        }
        .sortedWith(compareByDescending<FeedGroup> { it.newestAt }.thenBy { it.place })
}

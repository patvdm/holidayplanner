package com.pat.app.holidayplanner.ui.explore

import com.pat.app.holidayplanner.data.local.CityRow
import com.pat.app.holidayplanner.data.local.SpotRow
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortMode

/**
 * Ordering happens here rather than in SQL, because the mode changes at runtime and the lists are
 * small. Manual order is by sort key with the id as tiebreaker, and every other mode falls back to
 * the manual order so equal items keep a stable, meaningful sequence.
 */


fun List<CityRow>.sortedPlaces(mode: SortMode): List<CityRow> = when (mode) {
    SortMode.MANUAL -> sortedWith(manualOrder({ it.place.sortKey }, { it.place.meta }))
    SortMode.PRIORITY -> sortedWith(priorityOrder({ it.place.priority }, { it.place.sortKey }, { it.place.meta }))
    SortMode.RECENT -> sortedByDescending { it.place.meta.createdAt }
    SortMode.ALPHABETICAL -> sortedWith(alphabeticalOrder { it.place.name })
    SortMode.HEARTS -> sortedWith(manualOrder({ it.place.sortKey }, { it.place.meta }))
}

fun List<SpotRow>.sortedSpots(mode: SortMode): List<SpotRow> = when (mode) {
    SortMode.MANUAL -> sortedWith(manualOrder({ it.spot.sortKey }, { it.spot.meta }))
    SortMode.PRIORITY -> sortedWith(priorityOrder({ it.spot.priority }, { it.spot.sortKey }, { it.spot.meta }))
    SortMode.RECENT -> sortedByDescending { it.spot.meta.createdAt }
    SortMode.ALPHABETICAL -> sortedWith(alphabeticalOrder { it.spot.name })
    SortMode.HEARTS -> sortedWith(
        compareByDescending<SpotRow> { it.heartCount }
            .then(manualOrder({ it.spot.sortKey }, { it.spot.meta })),
    )
}

private fun <T> manualOrder(sortKey: (T) -> String, meta: (T) -> SyncMeta): Comparator<T> =
    compareBy(sortKey).thenBy { meta(it).id }

private fun <T> priorityOrder(
    priority: (T) -> Priority,
    sortKey: (T) -> String,
    meta: (T) -> SyncMeta,
): Comparator<T> = compareBy<T> { priority(it).ordinal }.then(manualOrder(sortKey, meta))

private fun <T> alphabeticalOrder(name: (T) -> String): Comparator<T> =
    compareBy(String.CASE_INSENSITIVE_ORDER, name)

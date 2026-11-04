package com.pat.app.holidayplanner.ui.explore

import com.pat.app.holidayplanner.data.local.SpotRow
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortMode

/**
 * Ordering happens here rather than in SQL, because the mode changes at runtime and the lists are
 * small. Manual order is by sort key with the id as tiebreaker, and every other mode falls back to
 * the manual order so equal items keep a stable, meaningful sequence.
 */

/**
 * A city's own order puts its high-priority spots first (Pat, 2026-11-04), each group in the order
 * it was dragged into, which makes MANUAL and PRIORITY the same order: the country screen only
 * offers MANUAL, and dragging stays within a spot's own priority group.
 */
fun List<SpotRow>.sortedSpots(mode: SortMode): List<SpotRow> = when (mode) {
    SortMode.MANUAL, SortMode.PRIORITY ->
        sortedWith(priorityOrder({ it.spot.priority }, { it.spot.sortKey }, { it.spot.meta }))
    SortMode.RECENT -> sortedByDescending { it.spot.meta.createdAt }
    SortMode.ALPHABETICAL -> sortedWith(alphabeticalOrder { it.spot.name })
    SortMode.HEARTS -> sortedWith(
        compareByDescending<SpotRow> { it.heartCount }
            .then(manualOrder({ it.spot.sortKey }, { it.spot.meta })),
    )
}

/**
 * The sort keys either side of [movedId] after a drop, taken from spots of its own priority only.
 * A neighbour from the other group would give a key that sorts it somewhere else in its own group.
 * Null when the spot is not in the list.
 */
fun List<SpotRow>.dropNeighbours(movedId: String): Pair<String?, String?>? {
    val moved = firstOrNull { it.spot.meta.id == movedId } ?: return null
    val group = filter { it.spot.priority == moved.spot.priority }
    val index = group.indexOf(moved)
    return group.getOrNull(index - 1)?.spot?.sortKey to group.getOrNull(index + 1)?.spot?.sortKey
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

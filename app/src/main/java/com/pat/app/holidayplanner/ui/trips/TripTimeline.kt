package com.pat.app.holidayplanner.ui.trips

import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.domain.LocalTimes
import java.time.LocalDate

/** Why an item shows on a given day, which decides what its card says. */
enum class ItemRole {
    /** The whole of it happens that day, or it is the day it begins. */
    START,

    /** Only the end lands here, which is how a stay appears again on its check-out day. */
    END,
}

data class TimelineEntry(val item: TripItemEntity, val role: ItemRole)

/** One day of a trip, with everything happening on it in the order it happens. */
data class TimelineDay(val date: LocalDate, val entries: List<TimelineEntry>)

/** Items with no usable time yet, kept where they can still be finished rather than hidden. */
data class Timeline(val days: List<TimelineDay>, val undated: List<TripItemEntity>)

/**
 * Arranges a trip's items into days.
 *
 * A stay is the awkward one: it belongs to the day you arrive and the day you leave, and showing it
 * only on the first would lose the check-out entirely. So it appears twice, labelled differently,
 * while everything else appears once on the day it starts.
 */
fun List<TripItemEntity>.toTimeline(): Timeline {
    val entries = mutableListOf<Pair<LocalDate, TimelineEntry>>()
    val undated = mutableListOf<TripItemEntity>()

    forEach { item ->
        val startDate = LocalTimes.dateOf(item.startLocal)
        val endDate = LocalTimes.dateOf(item.endLocal)
        when {
            // An item sits on the day it starts; a flight that lands next day does not appear twice.
            startDate != null -> entries += startDate to TimelineEntry(item, ItemRole.START)

            endDate != null -> entries += endDate to TimelineEntry(item, ItemRole.END)
            else -> undated += item
        }
    }

    val days = entries
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .map { (date, forDay) -> TimelineDay(date, forDay.sortedWith(byTimeOfDay)) }

    return Timeline(days = days, undated = undated.sortedBy { it.title.lowercase() })
}

/**
 * Within a day: by the time shown on the card, so a check-out at 10:00 sits above a flight at 13:00.
 * An entry with no time of its own leads the day rather than being buried at the bottom.
 */
private val byTimeOfDay = compareBy<TimelineEntry>(
    { timeShown(it) },
    { it.item.title.lowercase() },
)

private fun timeShown(entry: TimelineEntry): String = when (entry.role) {
    ItemRole.START -> LocalTimes.timeOfDay(entry.item.startLocal)
    ItemRole.END -> LocalTimes.timeOfDay(entry.item.endLocal)
}

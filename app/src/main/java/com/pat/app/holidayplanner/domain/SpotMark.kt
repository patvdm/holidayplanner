package com.pat.app.holidayplanner.domain

/**
 * Something a spot is good for, beyond what it is. A spot can carry any number of these, or none,
 * and they are kept apart from [SpotType] because a place to fly a drone is still a viewpoint or a
 * beach: this says what you would go there to do, not what it is.
 *
 * Stored as space-separated keys in one column, so another mark costs a line here and nothing in
 * the sheet. Keys written by a newer version are kept as they are rather than dropped.
 */
enum class SpotMark(val key: String) {
    DRONE("drone"),
    ;

    companion object {
        fun of(key: String): SpotMark? = entries.firstOrNull { it.key == key }
    }
}

/** The marks written on a spot, including any this version does not know about. */
fun String.toMarks(): Set<String> = split(' ').map(String::trim).filter(String::isNotEmpty).toSet()

fun Set<String>.asMarks(): String = sorted().joinToString(" ")

fun String.hasMark(mark: SpotMark): Boolean = mark.key in toMarks()

/** Adds or removes one mark, leaving everything else written there alone. */
fun String.withMark(mark: SpotMark, on: Boolean): String {
    val marks = toMarks()
    return (if (on) marks + mark.key else marks - mark.key).asMarks()
}

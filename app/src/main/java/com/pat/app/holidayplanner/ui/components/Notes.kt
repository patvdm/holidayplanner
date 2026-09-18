package com.pat.app.holidayplanner.ui.components

/**
 * The first line of a note, for a list row, or null when there is nothing to show.
 *
 * Rows show one line because a note is where the reason for saving a place tends to be written,
 * and the reason is nearly always the first thing written.
 */
fun String.firstLine(): String? = lineSequence()
    .map(String::trim)
    .firstOrNull(String::isNotEmpty)

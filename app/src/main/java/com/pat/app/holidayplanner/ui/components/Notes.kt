package com.pat.app.holidayplanner.ui.components

/** A note as a list row shows it: all of it, trimmed, or null when there is nothing written. */
fun String.noteOrNull(): String? = trim().takeIf(String::isNotEmpty)

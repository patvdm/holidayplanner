package com.pat.app.holidayplanner.domain

/**
 * Written into `imageUrl` once a search found nothing, so the search is not run again on every
 * start and on every phone. Blank means nobody has looked yet; a real address means a picture.
 */
const val NO_PICTURE = "none"

/** The address of a picture to show, or null when there is none to show. */
fun String.pictureUrlOrNull(): String? = trim().takeIf { it.isNotEmpty() && it != NO_PICTURE }

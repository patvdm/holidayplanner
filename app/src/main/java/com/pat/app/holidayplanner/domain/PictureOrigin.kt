package com.pat.app.holidayplanner.domain

/**
 * Where a country's, city's or spot's main picture came from, kept beside it in `imageSource` so
 * the app only ever redoes pictures it chose itself and never one somebody picked.
 */
enum class PictureOrigin {
    /** Found by the app's own search, or the search found nothing ([NO_PICTURE]). */
    AUTO,

    /** One of the place's own photos. */
    PHOTO,

    /** Chosen by hand: picked from the candidates or pasted as a link. */
    CHOSEN,
    ;

    /** What is written in the column; blank when the picture is blank, so the search may run. */
    fun stored(url: String): String = if (url.isBlank()) "" else name

    companion object {
        /**
         * Where [imageUrl] came from. Pictures set before the column existed have no origin written,
         * so it is told from the picture itself: nothing yet, or a Wikimedia address, is the app's
         * own doing; anything else somebody chose, a photo of theirs included.
         */
        fun of(imageUrl: String, imageSource: String): PictureOrigin =
            entries.firstOrNull { it.name == imageSource } ?: when {
                imageUrl.pictureUrlOrNull() == null -> AUTO
                imageUrl.isWikimedia() -> AUTO
                else -> CHOSEN
            }
    }
}

/** A picture served by Wikimedia, which is where the automatic search has always looked. */
fun String.isWikimedia(): Boolean = contains("wikimedia.org") || contains("wikipedia.org")

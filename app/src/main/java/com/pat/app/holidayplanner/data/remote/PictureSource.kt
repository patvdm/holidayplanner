package com.pat.app.holidayplanner.data.remote

/** Everything known about a spot that helps find a picture of it. */
data class SpotPictureQuery(
    val name: String,
    val cityName: String,
    val countryName: String,
    /** The country it is in, whose own language may know it when English does not. */
    val isoCode: String,
    val lat: Double? = null,
    val lng: Double? = null,
    /** The spot's own websites, whose share picture is often a photo of the place. */
    val websites: List<String> = emptyList(),
)

/** Where a candidate picture was found, said under it in the chooser. */
enum class PictureFrom { WIKIVOYAGE, WIKIPEDIA, COMMONS, WEBSITE }

/** One picture offered in the chooser. [metres] is how far from the spot it was taken, when known. */
data class PictureCandidate(val url: String, val from: PictureFrom, val metres: Int? = null)

/** Finds a picture of a place. Null means nothing suitable was found; a failure means try later. */
interface PictureSource {

    /** A country or a city: somewhere a travel guide has a page about. */
    suspend fun forPlace(name: String, within: String): Result<String?>

    /** A spot, which may be famous or may be a bar nobody has written about. */
    suspend fun forSpot(query: SpotPictureQuery): Result<String?>

    /** Pictures to choose from for a country or a city, best first. */
    suspend fun candidatesForPlace(name: String, within: String): Result<List<PictureCandidate>>

    /** Pictures to choose from for a spot, best first: the ones that match its name, then what is nearby. */
    suspend fun candidatesForSpot(query: SpotPictureQuery): Result<List<PictureCandidate>>
}

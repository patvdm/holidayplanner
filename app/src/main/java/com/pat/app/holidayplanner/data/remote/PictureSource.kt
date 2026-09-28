package com.pat.app.holidayplanner.data.remote

/** Finds a picture of a place. Null means nothing suitable was found; a failure means try later. */
interface PictureSource {

    /** A country or a city: somewhere a travel guide has a page about. */
    suspend fun forPlace(name: String, within: String): Result<String?>

    /**
     * A spot, which may be famous or may be a bar nobody has written about. [isoCode] is the
     * country it is in, whose own language may know it when English does not.
     */
    suspend fun forSpot(name: String, cityName: String, countryName: String, isoCode: String): Result<String?>
}

package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.domain.pictureUrlOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * The three kinds of place, for the things that treat a country, a city and a spot alike: the main
 * picture each one shows, and where in a country a photo or a link can be moved to.
 */
class Places(
    private val countries: CountryRepository,
    private val cities: CityRepository,
    private val spots: SpotRepository,
) {

    /** Everywhere in a country a photo or link could be moved to. */
    fun observeTargets(countryId: String): Flow<List<AttachmentTarget>> = combine(
        countries.observeCountry(countryId),
        cities.observeCities(countryId),
        spots.observeRowsInCountry(countryId),
    ) { country, cityList, spotRows ->
        if (country == null) emptyList() else attachmentTargets(country, cityList, spotRows.map { it.spot })
    }

    /** The place's main picture as stored: an address, blank, or NO_PICTURE. Null if it is gone. */
    suspend fun pictureOf(owner: AttachmentOwner): String? = when {
        owner.spotId.isNotEmpty() -> spots.observeSpot(owner.spotId).first()?.imageUrl
        owner.cityId.isNotEmpty() -> cities.find(owner.cityId)?.takeUnless { it.meta.deleted }?.imageUrl
        owner.countryId.isNotEmpty() -> countries.observeCountry(owner.countryId).first()?.imageUrl
        else -> null
    }

    /** True when the place exists and shows no picture of its own yet. */
    suspend fun lacksPicture(owner: AttachmentOwner): Boolean =
        pictureOf(owner)?.let { it.pictureUrlOrNull() == null } ?: false

    suspend fun setPicture(owner: AttachmentOwner, url: String) {
        when {
            owner.spotId.isNotEmpty() -> spots.setImageUrl(owner.spotId, url)
            owner.cityId.isNotEmpty() -> cities.setImageUrl(owner.cityId, url)
            owner.countryId.isNotEmpty() -> countries.setImageUrl(owner.countryId, url)
        }
    }
}

package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.domain.PlaceHierarchy

/**
 * Where a picked place will be filed, with the ids needed to save it. [cityId] is empty when no
 * city was named, and the place then belongs to the country itself.
 */
data class ResolvedHierarchy(
    val countryId: String,
    val countryName: String,
    val cityId: String,
    val cityName: String,
) {
    /** The "Netherlands › Den Haag" line shown for confirmation before anything is saved. */
    val breadcrumb: String get() = if (cityName.isBlank()) countryName else "$countryName › $cityName"
}

/**
 * Files a place picked from Google under the right country and city, creating either if this is the
 * first spot there.
 *
 * Countries are matched on their ISO code and cities by name within that country, ignoring case, so
 * picking two spots in Den Haag does not end up with two Den Haags.
 */
class HierarchyResolver(
    private val countryRepository: CountryRepository,
    private val cityRepository: CityRepository,
) {

    /**
     * The id of the country with this ISO code, creating it if this is the first thing filed there.
     *
     * Separate from [resolve] because adding a city only needs the country: resolving the whole
     * hierarchy would create a second city alongside the one being added.
     */
    suspend fun resolveCountry(isoCode: String, countryName: String): String {
        val existing = countryRepository.findByIsoCode(isoCode)
        return existing?.meta?.id ?: countryRepository.add(name = countryName, isoCode = isoCode)
    }

    /**
     * [cityName] overrides what Google said, for when the person used the Change option. A blank one
     * files the place under the country itself rather than inventing a catch-all city.
     */
    suspend fun resolve(hierarchy: PlaceHierarchy, cityName: String = hierarchy.cityName): ResolvedHierarchy {
        val country = countryRepository.findByIsoCode(hierarchy.isoCode)
        val countryId = country?.meta?.id
            ?: countryRepository.add(name = hierarchy.countryName, isoCode = hierarchy.isoCode)
        val countryLabel = country?.name ?: hierarchy.countryName

        val chosenName = cityName.trim()
        if (chosenName.isEmpty()) {
            return ResolvedHierarchy(countryId = countryId, countryName = countryLabel, cityId = "", cityName = "")
        }
        val place = cityRepository.findByName(countryId, chosenName)
        val cityId = place?.meta?.id ?: cityRepository.add(countryId = countryId, name = chosenName)

        return ResolvedHierarchy(
            countryId = countryId,
            countryName = countryLabel,
            cityId = cityId,
            cityName = place?.name ?: chosenName,
        )
    }
}

package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.domain.GENERAL_CITY_NAME
import com.pat.app.holidayplanner.domain.PlaceHierarchy

/** Where a picked place will be filed, with the ids needed to save it. */
data class ResolvedHierarchy(
    val countryId: String,
    val countryName: String,
    val cityId: String,
    val cityName: String,
) {
    /** The "Netherlands › Den Haag" line shown for confirmation before anything is saved. */
    val breadcrumb: String get() = "$countryName › $cityName"
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

    /** [cityName] overrides what Google said, for when the person used the Change option. */
    suspend fun resolve(hierarchy: PlaceHierarchy, cityName: String = hierarchy.cityName): ResolvedHierarchy {
        val country = countryRepository.findByIsoCode(hierarchy.isoCode)
        val countryId = country?.meta?.id
            ?: countryRepository.add(name = hierarchy.countryName, isoCode = hierarchy.isoCode)
        val countryLabel = country?.name ?: hierarchy.countryName

        val chosenName = cityName.trim().ifEmpty { GENERAL_CITY_NAME }
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

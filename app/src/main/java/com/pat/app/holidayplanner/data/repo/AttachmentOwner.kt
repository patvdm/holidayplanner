package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.CityEntity
import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.SpotEntity

/** What a photo or a link hangs on. Exactly one of the three ids is set. */
data class AttachmentOwner(
    val spotId: String = "",
    val cityId: String = "",
    val countryId: String = "",
) {
    /** A file name for an uploaded photo, so the backend folder says what each picture is of. */
    val fileStem: String get() = spotId.ifEmpty { cityId }.ifEmpty { countryId }

    companion object {
        fun of(link: LinkEntity): AttachmentOwner = AttachmentOwner(link.spotId, link.cityId, link.countryId)
    }
}

/** Somewhere a photo or a link can be moved to, with how deep it sits under the country. */
data class AttachmentTarget(val owner: AttachmentOwner, val name: String, val depth: Int)

/**
 * Everything in one country that can carry a photo or a link, in the order the country screen
 * shows it: the country, its own spots, then each city followed by its spots.
 */
fun attachmentTargets(
    country: CountryEntity,
    cities: List<CityEntity>,
    spots: List<SpotEntity>,
): List<AttachmentTarget> {
    val spotsByCity = spots
        .sortedWith(compareBy<SpotEntity> { it.sortKey }.thenBy { it.meta.id })
        .groupBy { it.parentCityId }
    val cityTargets = cities
        .sortedWith(compareBy<CityEntity> { it.sortKey }.thenBy { it.meta.id })
        .flatMap { city ->
            listOf(AttachmentTarget(AttachmentOwner(cityId = city.meta.id), city.name, depth = 1)) +
                spotsByCity[city.meta.id].orEmpty().map { spot ->
                    AttachmentTarget(AttachmentOwner(spotId = spot.meta.id), spot.name, depth = 2)
                }
        }
    val ownSpots = spotsByCity[""].orEmpty().map { spot ->
        AttachmentTarget(AttachmentOwner(spotId = spot.meta.id), spot.name, depth = 1)
    }
    return listOf(AttachmentTarget(AttachmentOwner(countryId = country.meta.id), country.name, depth = 0)) +
        ownSpots + cityTargets
}

/** A country's photos and links, sorted by what they hang on. */
data class CountryAttachments(
    val country: List<LinkEntity> = emptyList(),
    val byCity: Map<String, List<LinkEntity>> = emptyMap(),
    val bySpot: Map<String, List<LinkEntity>> = emptyMap(),
) {
    companion object {
        /**
         * The most specific owner wins, should a row ever carry more than one id: a link that names
         * a spot is about that spot, whatever else it says.
         */
        fun of(links: List<LinkEntity>): CountryAttachments {
            val (onSpots, rest) = links.partition { it.spotId.isNotEmpty() }
            val (onCities, onCountry) = rest.partition { it.cityId.isNotEmpty() }
            return CountryAttachments(
                country = onCountry.filter { it.countryId.isNotEmpty() },
                byCity = onCities.groupBy { it.cityId },
                bySpot = onSpots.groupBy { it.spotId },
            )
        }
    }
}

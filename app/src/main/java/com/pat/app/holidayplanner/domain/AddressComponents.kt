package com.pat.app.holidayplanner.domain

import java.util.Locale

/**
 * One piece of a Google address, in a shape of our own so the domain does not depend on the Places
 * SDK. [shortName] is the abbreviated form, which for a country is the ISO 3166-1 alpha-2 code.
 */
data class AddressPart(
    val types: List<String>,
    val name: String,
    val shortName: String,
)

/** Where a picked place belongs in the Country then Place hierarchy. */
data class PlaceHierarchy(
    val isoCode: String,
    val countryName: String,
    val cityName: String,
)

private const val TYPE_COUNTRY = "country"

/**
 * Types that can name the city or region a spot sits in, best first. Google fills different ones
 * depending on the country: a Dutch address has a locality, a British one often only has a postal
 * town, and a rural address may have nothing below the province.
 */
private val PLACE_NAME_TYPES = listOf(
    "locality",
    "postal_town",
    "administrative_area_level_2",
    "administrative_area_level_1",
)

/**
 * Reads the country and the city or region out of a Google address.
 *
 * Pure on purpose: everything here can be checked against real addresses in a unit test, which is
 * the only way to be confident about a format that varies by country.
 */
object AddressComponents {

    /**
     * Returns null when there is no country component, because a spot with no country has nowhere
     * to live. The caller then falls back to asking the person where it belongs.
     */
    fun resolve(parts: List<AddressPart>): PlaceHierarchy? {
        val country = parts.firstOrNull { TYPE_COUNTRY in it.types } ?: return null
        val isoCode = country.shortName.trim().uppercase(Locale.ENGLISH)
        if (isoCode.isEmpty()) return null

        return PlaceHierarchy(
            isoCode = isoCode,
            countryName = country.name.trim().ifEmpty { isoCode },
            cityName = cityName(parts).orEmpty(),
        )
    }

    /** The best available name for the city or region, or null when Google offered none. */
    fun cityName(parts: List<AddressPart>): String? = PLACE_NAME_TYPES
        .firstNotNullOfOrNull { type ->
            parts.firstOrNull { type in it.types }?.name?.trim()?.takeIf(String::isNotEmpty)
        }
}

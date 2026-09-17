package com.pat.app.holidayplanner.domain

import java.util.Locale

/** Postcodes that sit in front of the town: "2511 CS Den Haag", "1200-204 Lisboa", "75001 Paris". */
private val LEADING_POSTCODE = Regex("""^(\d{4}\s?[A-Z]{2}|\d{4}-\d{3}|\d{4,6})\s+""")

/** Postcodes that trail the town: "London WC1B 3DG", "Mountain View 94043". */
private val TRAILING_POSTCODE = Regex("""\s+([A-Z]{1,2}\d[A-Z\d]?\s*\d[A-Z]{2}|\d{4,6})$""")

/** A part that is only a state or province code plus a postcode, like "CA 94043". */
private val STATE_AND_POSTCODE = Regex("""^[A-Z]{2}\s+\d{4,6}$""")

/** Country shorthands people see in addresses but that are not the official display names. */
private val COUNTRY_SHORTHAND = setOf("UK", "USA", "US", "UAE", "NL", "DE", "FR", "ES", "IT", "PT")

private const val SHORTEST_CITY_NAME = 2

/** The shorthands that are not ISO codes themselves. */
private val ISO_BY_SHORTHAND = mapOf("UK" to "GB", "USA" to "US", "US" to "US", "UAE" to "AE")

/**
 * Guesses at the parts of a postal address.
 *
 * Address formats vary by country and none of this is exact, which is why everything it produces
 * lands in a field the person can correct before saving. It exists to save typing, not to be right
 * every time.
 */
object AddressText {

    /**
     * Whether a line reads as an address rather than a name.
     *
     * Maps shares a place as its name then its address, but sharing a dropped pin or a plain street
     * gives only the address, and calling a spot "Plein 29, 2511 CS Den Haag" helps nobody.
     */
    fun looksLikeAddress(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val commas = trimmed.count { it == ',' }
        val hasDigit = trimmed.any(Char::isDigit)
        return commas >= 2 || (commas >= 1 && hasDigit)
    }

    /**
     * The town out of an address, or empty when there is nothing convincing.
     *
     * Works backwards: drop the country, drop the postcode, and what is left of the last part is
     * usually the town. Falls back a level when the tail turns out to be a state code.
     */
    fun cityFrom(address: String): String {
        val (parts, cityIndex) = split(address)
        return if (cityIndex < 0) "" else stripPostcode(parts[cityIndex])
    }

    /**
     * A usable name for a place that only has an address, which is what sharing a dropped pin gives.
     *
     * Only the first part, because Google writes "Mauritshuis, Plein 29, 2511 CS Den Haag" as often
     * as it writes a bare street: the head of an address is the closest thing to a name, and a spot
     * called "Mauritshuis" beats one called "Mauritshuis, Plein 29". An address that is only a town
     * has no name in it at all, so this gives nothing back rather than repeating the city.
     */
    fun nameFrom(address: String): String {
        val (parts, cityIndex) = split(address)
        return if (cityIndex <= 0) "" else parts.first()
    }

    /**
     * The ISO code of the country named at the end of an address, or empty when it names none.
     *
     * Free and instant, but Google only includes a country when it feels like it, so this is the
     * fallback for when there are no coordinates to ask about instead.
     */
    fun countryFrom(address: String): String {
        val last = address.split(',').map(String::trim).lastOrNull(String::isNotEmpty) ?: return ""
        val match = CountryCatalog.all().firstOrNull { it.name.equals(last, ignoreCase = true) }
        if (match != null) return match.isoCode
        val upper = last.uppercase(Locale.ENGLISH)
        return if (upper in COUNTRY_SHORTHAND) ISO_BY_SHORTHAND[upper].orEmpty() else ""
    }

    /** The address split on commas, with the index of the part holding the town. */
    private fun split(address: String): Pair<List<String>, Int> {
        val parts = address.split(',').map(String::trim).filter(String::isNotEmpty)
        if (parts.isEmpty()) return emptyList<String>() to -1

        val withoutCountry = parts.dropLastWhile(::isCountry).ifEmpty { parts }
        val lastIndex = withoutCountry.lastIndex
        val last = withoutCountry[lastIndex]
        val convincing = stripPostcode(last).length >= SHORTEST_CITY_NAME &&
            !STATE_AND_POSTCODE.matches(last)

        // "Mountain View, CA 94043" leaves a state code behind, so the town is the part before it.
        val cityIndex = if (convincing || lastIndex == 0) lastIndex else lastIndex - 1
        return withoutCountry to cityIndex
    }

    private fun isCountry(part: String): Boolean {
        val upper = part.uppercase(Locale.ENGLISH)
        if (upper in COUNTRY_SHORTHAND) return true
        return CountryCatalog.all().any { it.name.equals(part, ignoreCase = true) }
    }

    private fun stripPostcode(part: String): String =
        part.replace(LEADING_POSTCODE, "").replace(TRAILING_POSTCODE, "").trim()
}

package com.pat.app.holidayplanner.domain

import java.util.Locale

/**
 * The city a spot falls back to when nobody knows exactly where it is, so a country can hold spots
 * before anyone has worked out the towns.
 *
 * This is stored data rather than UI wording, and it goes into the shared sheet where both people
 * see the same row, so it is a plain constant rather than a string resource.
 */
const val GENERAL_CITY_NAME = "General"

/**
 * The country offered first when nothing else says otherwise. Home is where most spots start, and
 * it is the one the plan keeps at the top of the list.
 */
const val DEFAULT_COUNTRY_ISO = "NL"

/** The catch-all country option, or null in the unlikely event the platform has no such code. */
fun defaultCountry(): CountryOption? = CountryCatalog.all().firstOrNull { it.isoCode == DEFAULT_COUNTRY_ISO }

/** The country for an ISO code, falling back to home when the code is unknown or missing. */
fun countryOrDefault(isoCode: String): CountryOption? {
    val code = isoCode.trim().uppercase(Locale.ENGLISH)
    return CountryCatalog.all().firstOrNull { it.isoCode == code } ?: defaultCountry()
}

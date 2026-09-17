package com.pat.app.holidayplanner.domain

import java.util.Locale

private const val FLAG_OFFSET = 0x1F1E6
private const val ISO_CODE_LENGTH = 2

data class CountryOption(val isoCode: String, val name: String, val flag: String)

/** The list of countries offered when adding one, and the flag shown next to a country. */
object CountryCatalog {

    private val options: List<CountryOption> by lazy {
        Locale.getISOCountries()
            .map { code -> CountryOption(isoCode = code, name = displayName(code), flag = flag(code)) }
            .filter { it.name != it.isoCode }
            .sortedBy { it.name.lowercase(Locale.ENGLISH) }
    }

    fun all(): List<CountryOption> = options

    /**
     * The flag emoji for an ISO 3166-1 alpha-2 code, built from the two regional indicator symbols.
     * Anything that is not two letters has no flag.
     */
    fun flag(isoCode: String): String {
        val code = isoCode.trim().uppercase(Locale.ENGLISH)
        if (code.length != ISO_CODE_LENGTH || code.any { it !in 'A'..'Z' }) return ""
        return code.map { Character.toChars(FLAG_OFFSET + (it - 'A')).concatToString() }.joinToString("")
    }

    private fun displayName(isoCode: String): String =
        Locale.Builder().setRegion(isoCode).build().getDisplayCountry(Locale.ENGLISH)
}

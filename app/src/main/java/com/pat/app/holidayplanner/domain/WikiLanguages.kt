package com.pat.app.holidayplanner.domain

import java.util.Locale

/**
 * The Wikipedia most likely to have written about a spot in a country: the language of the place
 * itself. English Wikipedia skips most places that only locals and guidebooks know, and misses
 * famous ones filed under their own name, like "Vredespaleis Den Haag".
 *
 * A fixed table rather than the platform's locale list, whose order is not guaranteed: the
 * Netherlands could come back as Frisian, Spain as Catalan.
 */
object WikiLanguages {

    private val BY_COUNTRY = mapOf(
        // Western Europe. Belgium's spots are mostly Flemish; Brussels is written up in both.
        "NL" to "nl", "BE" to "nl", "LU" to "fr", "FR" to "fr", "MC" to "fr",
        "DE" to "de", "AT" to "de", "CH" to "de", "LI" to "de",
        "ES" to "es", "AD" to "ca", "PT" to "pt", "IT" to "it", "SM" to "it", "VA" to "it", "MT" to "mt",
        // Northern Europe.
        "DK" to "da", "NO" to "no", "SE" to "sv", "FI" to "fi", "IS" to "is",
        "EE" to "et", "LV" to "lv", "LT" to "lt",
        // Central and eastern Europe.
        "PL" to "pl", "CZ" to "cs", "SK" to "sk", "HU" to "hu", "SI" to "sl", "HR" to "hr",
        "BA" to "bs", "RS" to "sr", "ME" to "sr", "MK" to "mk", "AL" to "sq", "XK" to "sq",
        "RO" to "ro", "MD" to "ro", "BG" to "bg", "GR" to "el", "CY" to "el",
        "UA" to "uk", "BY" to "be", "RU" to "ru", "TR" to "tr", "GE" to "ka", "AM" to "hy",
        // Further afield.
        "MX" to "es", "AR" to "es", "CL" to "es", "CO" to "es", "PE" to "es", "CR" to "es", "CU" to "es",
        "BR" to "pt", "JP" to "ja", "KR" to "ko", "CN" to "zh", "TW" to "zh", "TH" to "th", "VN" to "vi",
        "ID" to "id", "MY" to "ms", "IL" to "he", "MA" to "fr", "TN" to "fr", "EG" to "ar", "JO" to "ar",
    )

    /** Wikipedia language code for an ISO country code, or null when English is the best there is. */
    fun forCountry(isoCode: String): String? = BY_COUNTRY[isoCode.trim().uppercase(Locale.ROOT)]
}

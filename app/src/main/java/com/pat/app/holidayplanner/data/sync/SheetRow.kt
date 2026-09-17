package com.pat.app.holidayplanner.data.sync

/**
 * One row from the sheet, keyed by header name. Every cell arrives as a string, and a missing or
 * unparseable cell falls back to an empty value rather than failing the whole pull.
 */
@JvmInline
value class SheetRow(private val values: Map<String, String>) {

    fun str(key: String): String = values[key].orEmpty()

    fun long(key: String): Long = str(key).trim().toLongOrNull() ?: 0L

    fun longOrNull(key: String): Long? = str(key).trim().takeIf(String::isNotEmpty)?.toLongOrNull()

    fun doubleOrNull(key: String): Double? = str(key).trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()

    fun bool(key: String): Boolean = str(key).trim().equals("true", ignoreCase = true)

    fun list(key: String): List<String> =
        str(key).split(',').map(String::trim).filter(String::isNotEmpty)
}

package com.pat.app.holidayplanner.domain

/**
 * Fractional indexing over base-62 keys.
 *
 * A key is a string of digits from [DIGITS], which is in ASCII order, so plain string comparison
 * (SQLite's BINARY collation included) gives the intended order. [between] returns a key strictly
 * between its two neighbours, so moving an item rewrites only that one row.
 *
 * Invariant: a key never ends with the lowest digit, otherwise no key could be placed before it.
 * Keys that break that rule, or that contain characters outside the alphabet, can only arrive from
 * a manual edit in the sheet. They are ignored rather than treated as an error, which can place the
 * new item next to its neighbour instead of between the two, but never crashes.
 */
object SortKey {

    const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    /** A key strictly between [before] and [after]. Nulls mean "no neighbour on that side". */
    fun between(before: String?, after: String?): String {
        val start = before?.takeIf(::isValid)
        val end = after?.takeIf { isValid(it) && (start == null || it > start) }
        return midpoint(start.orEmpty(), end)
    }

    /** The key for the first item in an empty list. */
    fun first(): String = between(null, null)

    /** The key for a new item appended after [last]. */
    fun after(last: String?): String = between(last, null)

    fun isValid(key: String): Boolean =
        key.isNotEmpty() && !key.endsWith(DIGITS.first()) && key.all { it in DIGITS }

    private fun midpoint(before: String, after: String?): String {
        if (after != null) {
            val shared = sharedPrefixLength(before, after)
            if (shared > 0) {
                return after.substring(0, shared) + midpoint(before.drop(shared), after.substring(shared))
            }
        }
        val low = if (before.isEmpty()) 0 else DIGITS.indexOf(before[0])
        val high = if (after == null) DIGITS.length else DIGITS.indexOf(after[0])
        return when {
            high - low > 1 -> DIGITS[(low + high + 1) / 2].toString()
            after != null && after.length > 1 -> after.substring(0, 1)
            else -> DIGITS[low] + midpoint(before.drop(1), null)
        }
    }

    /** Missing digits of [before] count as the lowest digit, so "A" and "A0B" share the prefix "A". */
    private fun sharedPrefixLength(before: String, after: String): Int {
        var shared = 0
        while (shared < after.length && (before.getOrNull(shared) ?: DIGITS.first()) == after[shared]) {
            shared++
        }
        return shared
    }
}

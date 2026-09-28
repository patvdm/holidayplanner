package com.pat.app.holidayplanner.domain

/** Light or dark, chosen in Settings. Dark unless someone picked otherwise. */
enum class ThemeMode {
    DARK,
    LIGHT,

    /** Follows the phone's own dark mode setting. */
    SYSTEM,
    ;

    companion object {
        val DEFAULT = DARK

        fun fromPref(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

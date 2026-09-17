package com.pat.app.holidayplanner.data.repo

/** What a soft delete would take with it, shown in the confirmation dialog before it happens. */
data class DeleteImpact(
    val cities: Int = 0,
    val spots: Int = 0,
    val links: Int = 0,
    val hearts: Int = 0,
    val items: Int = 0,
)

package com.pat.app.holidayplanner.domain

/** Wall clock in epoch millis. Injected so tests can control time. */
fun interface AppClock {
    fun now(): Long
}

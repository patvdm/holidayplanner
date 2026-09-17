package com.pat.app.holidayplanner.data.remote

/** Rows keyed by sheet tab name, with every cell as a string. */
typealias SheetRows = Map<String, List<Map<String, String>>>

data class PullResult(val serverTime: Long, val changes: SheetRows)

data class PushResult(val serverTime: Long, val accepted: Int, val skipped: List<String>, val errors: Int)

/**
 * What sync needs from the backend. [SheetsApi] is the real implementation; tests supply a fake.
 * Every call returns a [Result] rather than throwing, because a failed sync is a normal state.
 */
interface SheetsBackend {

    /** Returns the server clock in epoch millis. */
    suspend fun ping(): Result<Long>

    /** Every row whose server-side `syncedAt` is at or after [since]. */
    suspend fun pull(since: Long): Result<PullResult>

    suspend fun push(rows: SheetRows): Result<PushResult>
}

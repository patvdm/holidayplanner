package com.pat.app.holidayplanner.data.sync

import com.pat.app.holidayplanner.data.remote.PullResult
import com.pat.app.holidayplanner.data.remote.PushError
import com.pat.app.holidayplanner.data.remote.PushResult
import com.pat.app.holidayplanner.data.remote.SheetRows
import com.pat.app.holidayplanner.data.remote.SheetsBackend

/** Records what was pushed and replays a scripted pull. */
class FakeSheetsBackend(
    private val serverTime: Long = 1_000,
    private var pullChanges: SheetRows = emptyMap(),
) : SheetsBackend {

    val pushedBatches = mutableListOf<SheetRows>()
    var pullSince: Long? = null
    var failPush: Throwable? = null
    var failPull: Throwable? = null

    /** What the "sheet" refuses on every push. */
    var refuse: List<PushError> = emptyList()

    /** Runs while the push request is "in flight", to simulate an edit made at the same moment. */
    var duringPush: (suspend () -> Unit)? = null

    override suspend fun ping(): Result<Long> = Result.success(serverTime)

    override suspend fun pull(since: Long): Result<PullResult> {
        pullSince = since
        failPull?.let { return Result.failure(it) }
        return Result.success(PullResult(serverTime = serverTime, changes = pullChanges))
    }

    override suspend fun push(rows: SheetRows): Result<PushResult> {
        pushedBatches += rows
        duringPush?.invoke()
        failPush?.let { return Result.failure(it) }
        val count = rows.values.sumOf { it.size }
        return Result.success(PushResult(serverTime, accepted = count, skipped = emptyList(), errors = refuse))
    }

    fun setPullChanges(changes: SheetRows) {
        pullChanges = changes
    }
}

package com.pat.app.holidayplanner.data.sync

import android.util.Log
import com.pat.app.holidayplanner.data.local.SyncStateEntity
import com.pat.app.holidayplanner.data.local.dao.SyncStateDao
import com.pat.app.holidayplanner.data.remote.PushError
import com.pat.app.holidayplanner.data.remote.SheetsBackend
import com.pat.app.holidayplanner.domain.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "SyncRepository"

/** Rows read per table in one run. Far above anything this app produces between syncs. */
private const val MAX_ROWS_PER_TABLE = 500

/** Rows per push request, to keep Apps Script well inside its execution limits. */
private const val BATCH_ROWS = 300

/**
 * One sync run: push every dirty row, then pull everything the server has stamped since the last
 * successful pull.
 *
 * The initial sync is the same code path with `since = 0`. Local tables are never cleared, and the
 * new `lastServerTime` is stored in the same transaction that applies the rows, so a failure
 * halfway through leaves the device asking for the same window again. Re-applying rows is harmless
 * because the merge is idempotent.
 */
class SyncRepository(
    private val backend: SheetsBackend,
    private val tables: SyncTables,
    private val transactor: Transactor,
    private val syncStateDao: SyncStateDao,
    private val outcomeStore: SyncOutcomeStore,
    private val clock: AppClock,
) {

    private val mutex = Mutex()
    private val runningState = MutableStateFlow(false)

    val running: StateFlow<Boolean> = runningState.asStateFlow()

    /** Runs one full sync. Concurrent callers wait rather than overlapping. */
    suspend fun sync(): Result<Unit> = mutex.withLock {
        runningState.value = true
        val result = try {
            pushThenPull()
        } finally {
            runningState.value = false
        }
        record(result)
        result
    }

    /**
     * Rows the sheet refused stay dirty and go out again next time, and the run still pulls, so one
     * bad row never stops the rest arriving; the run then counts as failed, which puts the reason
     * in Settings.
     */
    /**
     * Syncs, then says how many changes are still only on this phone. An update can drop the local
     * database (see AppDatabase), so it installs only once this is zero, or once someone says so.
     */
    suspend fun sendPending(): Int {
        sync()
        return syncStateDao.observePendingCount().first()
    }

    private suspend fun pushThenPull(): Result<Unit> {
        val refused = push().getOrElse { return Result.failure(it) }
        pull().onFailure { return Result.failure(it) }
        return if (refused.isEmpty()) Result.success(Unit) else Result.failure(RefusedRowsException(refused))
    }

    /** Pushes every dirty row, and returns what the sheet refused. */
    private suspend fun push(): Result<List<PushError>> {
        val pending = tables.all.flatMap { table -> table.pendingRows(MAX_ROWS_PER_TABLE).map { table to it } }
        if (pending.isEmpty()) return Result.success(emptyList())

        val refused = mutableListOf<PushError>()
        pending.chunked(BATCH_ROWS).forEach { batch ->
            val payload = batch.groupBy({ it.first.sheetName }, { it.second.values })
            val result = backend.push(payload).getOrElse { return Result.failure(it) }
            Log.d(TAG, "pushed ${result.accepted} rows, skipped ${result.skipped.size}, refused ${result.errors}")
            refused += result.errors
            val written = batch.filterNot { (table, row) ->
                result.errors.any { it.covers(table.sheetName, row.id) }
            }
            transactor.transaction {
                written.groupBy({ it.first }, { it.second }).forEach { (table, rows) -> table.clearDirty(rows) }
            }
        }
        return Result.success(refused)
    }

    private suspend fun pull(): Result<Unit> {
        val since = syncStateDao.lastServerTime() ?: 0L
        val result = backend.pull(since).getOrElse { return Result.failure(it) }
        transactor.transaction {
            result.changes.forEach { (sheetName, rows) ->
                val table = tables.named(sheetName)
                if (table == null) {
                    Log.d(TAG, "ignoring unknown tab $sheetName")
                } else {
                    Log.d(TAG, "merged ${table.merge(rows)} of ${rows.size} rows into $sheetName")
                }
            }
            syncStateDao.upsert(SyncStateEntity(lastServerTime = result.serverTime))
        }
        return Result.success(Unit)
    }

    private suspend fun record(result: Result<Unit>) {
        result.fold(
            onSuccess = { outcomeStore.recordSyncSuccess(clock.now()) },
            onFailure = { outcomeStore.recordSyncError(it.message ?: it.javaClass.simpleName) },
        )
    }
}

/** The sheet would not take some rows; they stay waiting on this phone and are sent again next time. */
class RefusedRowsException(val refused: List<PushError>) : Exception(
    "The sheet refused ${refused.size} changes: " +
        refused.groupBy { "${it.table} (${it.error})" }.keys.joinToString(", "),
)

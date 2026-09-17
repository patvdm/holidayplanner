package com.pat.app.holidayplanner.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.sync.SyncScheduler
import com.pat.app.holidayplanner.data.sync.SyncStatusRepository
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long to wait for the queued work to actually begin before giving up on seeing it start. */
private val START_TIMEOUT = 2.seconds

/** A sync takes a few seconds; this only stops the spinner turning forever if something hangs. */
private val FINISH_TIMEOUT = 60.seconds

/**
 * Drives the pull to refresh spinner.
 *
 * The spinner only follows a sync the person asked for. Binding it to the sync state itself would
 * make it appear on its own every fifteen minutes, which reads as the app doing something wrong.
 */
class SyncRefreshViewModel(
    private val scheduler: SyncScheduler,
    statusRepository: SyncStatusRepository,
) : ViewModel() {

    private val running: Flow<Boolean> = statusRepository.status.map { it.running }
    private val manual = MutableStateFlow(false)

    val refreshing: StateFlow<Boolean> = manual.asStateFlow()

    fun refresh() {
        if (manual.value) return
        manual.value = true
        viewModelScope.launch {
            scheduler.syncNow()
            // The sync is queued rather than immediate, so wait a moment for it to start. Missing
            // the start is harmless: a run that already finished leaves this false and the wait
            // below returns at once.
            withTimeoutOrNull(START_TIMEOUT) { running.first { it } }
            withTimeoutOrNull(FINISH_TIMEOUT) { running.first { !it } }
            manual.value = false
        }
    }
}

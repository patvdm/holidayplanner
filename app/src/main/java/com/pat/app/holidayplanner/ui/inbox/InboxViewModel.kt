package com.pat.app.holidayplanner.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.InboxRow
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.repo.LinkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

data class InboxUiState(
    val rows: List<InboxRow> = emptyList(),
    val selected: Set<String> = emptySet(),
    /** Set once the person comes back from the app the link opened in. */
    val assigning: Set<String> = emptySet(),
) {
    val selectionMode: Boolean get() = selected.isNotEmpty()
}

/**
 * The inbox of links shared into the app but not yet attached to a spot.
 *
 * Tapping a link opens it elsewhere, which usually means leaving the app entirely. The id is
 * remembered on disk rather than in memory, so the assign sheet still appears on the way back even
 * if the system reclaimed this process while Instagram was in front.
 */
class InboxViewModel(
    private val repository: LinkRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val assigning = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<InboxUiState> = combine(
        repository.observeInboxRows(),
        selected,
        assigning,
    ) { rows, selection, pending ->
        InboxUiState(rows = rows, selected = selection, assigning = pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InboxUiState())

    /** Called before the link is handed to another app, so the way back is known. */
    fun rememberPendingAssign(linkId: String) {
        viewModelScope.launch { prefs.setPendingAssignLinkId(linkId) }
    }

    /** Called when the inbox comes back to the foreground. */
    fun onResumed() {
        viewModelScope.launch {
            val pending = prefs.pendingAssignLinkId.first() ?: return@launch
            prefs.clearPendingAssignLinkId()
            // Only offer to file it if it is still sitting in the inbox.
            if (uiState.value.rows.any { it.link.meta.id == pending }) {
                assigning.value = setOf(pending)
            }
        }
    }

    /** Opens the spot picker for one link, without handing it to another app first. */
    fun startAssign(linkId: String) {
        assigning.value = setOf(linkId)
    }

    fun assignTo(spotId: String) {
        val links = assigning.value
        if (links.isEmpty()) return
        assigning.value = emptySet()
        selected.value = emptySet()
        viewModelScope.launch { repository.assignAll(links, spotId) }
    }

    fun cancelAssign() {
        assigning.value = emptySet()
    }

    /** Files the current selection, which is how several links reach one spot at once. */
    fun assignSelected() {
        assigning.value = selected.value
    }

    fun toggleSelected(linkId: String) {
        selected.value = selected.value.toMutableSet().apply {
            if (!add(linkId)) remove(linkId)
        }
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun delete(linkId: String) {
        selected.value = selected.value - linkId
        viewModelScope.launch { repository.delete(linkId) }
    }
}

package com.pat.app.holidayplanner.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.CityEntity
import com.pat.app.holidayplanner.data.local.SpotRow
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.repo.DeleteImpact
import com.pat.app.holidayplanner.data.repo.CityRepository
import com.pat.app.holidayplanner.data.repo.RouteRepository
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.data.repo.UserRepository
import com.pat.app.holidayplanner.domain.GeoPoint
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.Proximity
import com.pat.app.holidayplanner.domain.SortMode
import com.pat.app.holidayplanner.domain.SortScope
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/** The three filters on the spot list. Null means "everything". */
data class SpotFilters(
    val status: SpotStatus? = null,
    val priority: Priority? = null,
    val type: SpotType? = null,
    val tag: String? = null,
) {
    val any: Boolean get() = status != null || priority != null || type != null || tag != null

    fun matches(row: SpotRow): Boolean =
        (status == null || row.spot.status == status) &&
            (priority == null || row.spot.priority == priority) &&
            (type == null || row.spot.type == type) &&
            (tag == null || tag in row.spot.tags)
}

data class CityUiState(
    val place: CityEntity? = null,
    val rows: List<SpotRow> = emptyList(),
    val tags: List<String> = emptyList(),
    val filters: SpotFilters = SpotFilters(),
    val sortMode: SortMode = SortMode.MANUAL,
    val userCount: Int = 0,
    val pendingDelete: PendingDelete? = null,
) {
    /** Dragging only makes sense in manual order and with nothing filtered out. */
    val canReorder: Boolean get() = sortMode == SortMode.MANUAL && !filters.any
}

/** The spot list plus the two things that shape it, combined separately to stay within combine's arity. */
private data class SpotListState(
    val rows: List<SpotRow>,
    val sortMode: SortMode,
    val filters: SpotFilters,
)

class CityViewModel(
    private val cityId: String,
    private val cityRepository: CityRepository,
    private val spotRepository: SpotRepository,
    userRepository: UserRepository,
    private val routes: RouteRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val filters = MutableStateFlow(SpotFilters())
    private val pendingDelete = MutableStateFlow<PendingDelete?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val spots: Flow<List<SpotRow>> = userRepository.currentUserId.flatMapLatest { userId ->
        spotRepository.observeRows(cityId, userId.orEmpty())
    }

    private val listState: Flow<SpotListState> =
        combine(spots, prefs.sortMode(SortScope.SPOTS), filters, ::SpotListState)

    val uiState: StateFlow<CityUiState> = combine(
        cityRepository.observeCity(cityId),
        listState,
        userRepository.observeUserCount(),
        pendingDelete,
    ) { place, list, userCount, pending ->
        CityUiState(
            place = place,
            rows = list.rows.filter(list.filters::matches).sortedSpots(list.sortMode),
            tags = list.rows.flatMap { it.spot.tags }.distinct().sorted(),
            filters = list.filters,
            sortMode = list.sortMode,
            userCount = userCount,
            pendingDelete = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CityUiState())

    fun setStatusFilter(status: SpotStatus?) = filters.update { it.copy(status = status) }

    fun setPriorityFilter(priority: Priority?) = filters.update { it.copy(priority = priority) }

    fun setTypeFilter(type: SpotType?) = filters.update { it.copy(type = type) }

    fun setTagFilter(tag: String?) = filters.update { it.copy(tag = tag) }

    fun clearFilters() {
        filters.value = SpotFilters()
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch { prefs.setSortMode(SortScope.SPOTS, mode) }
    }

    fun toggleHeart(spotId: String) {
        viewModelScope.launch { spotRepository.toggleHeart(spotId) }
    }

    fun setPriority(id: String, priority: Priority) {
        viewModelScope.launch { spotRepository.setPriority(id, priority) }
    }

    fun setStatus(id: String, status: SpotStatus) {
        viewModelScope.launch { spotRepository.setStatus(id, status) }
    }

    fun move(id: String, previousKey: String?, nextKey: String?) {
        viewModelScope.launch { spotRepository.move(id, previousKey, nextKey) }
    }

    fun renamePlace(name: String) {
        viewModelScope.launch { cityRepository.rename(cityId, name) }
    }

    /** The row already carries the counts, so confirming a delete needs no extra query. */
    fun requestDelete(row: SpotRow) {
        pendingDelete.value = PendingDelete(
            id = row.spot.meta.id,
            name = row.spot.name,
            impact = DeleteImpact(links = row.linkCount, hearts = row.heartCount),
        )
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    /**
     * Starts a route through this city with every spot nobody has visited yet already on it,
     * ordered so it opens as a sensible walk with no extra tap.
     */
    fun createRoute(title: String, onCreated: (String) -> Unit) {
        // Ordered before it is handed over, so the route opens as a sensible walk with no extra tap.
        val unvisited = Proximity
            .orderByProximity(uiState.value.rows.filter { it.spot.status == SpotStatus.UNVISITED }) { row ->
                val lat = row.spot.lat
                val lng = row.spot.lng
                if (lat != null && lng != null) GeoPoint(lat, lng) else null
            }
            .map { it.spot.meta.id }
        viewModelScope.launch { onCreated(routes.create(cityId, title, unvisited)) }
    }

    fun confirmDelete() {
        val target = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { spotRepository.delete(target.id) }
    }
}

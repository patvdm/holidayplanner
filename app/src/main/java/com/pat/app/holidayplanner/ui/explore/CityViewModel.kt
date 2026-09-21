package com.pat.app.holidayplanner.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pat.app.holidayplanner.data.local.CityEntity
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.SpotRow
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.repo.DeleteImpact
import com.pat.app.holidayplanner.data.repo.CityRepository
import com.pat.app.holidayplanner.data.repo.PhotoRepository
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
) {
    val any: Boolean get() = status != null || priority != null || type != null

    fun matches(row: SpotRow): Boolean =
        (status == null || row.spot.status == status) &&
            (priority == null || row.spot.priority == priority) &&
            (type == null || row.spot.type == type)
}

data class CityUiState(
    val place: CityEntity? = null,
    val rows: List<SpotRow> = emptyList(),
    /** The photos and links of each spot, so a row can show them without a query of its own. */
    val attachments: Map<String, List<LinkEntity>> = emptyMap(),
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
    val attachments: Map<String, List<LinkEntity>>,
)

/**
 * What the city screen reads and writes. Grouped because a city row shows a spot, its hearts, its
 * photos and its links at once, and a constructor listing all of them says nothing useful.
 */
class CityRepositories(
    val cities: CityRepository,
    val spots: SpotRepository,
    val users: UserRepository,
    val routes: RouteRepository,
    val photos: PhotoRepository,
)

class CityViewModel(
    private val cityId: String,
    private val repos: CityRepositories,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val filters = MutableStateFlow(SpotFilters())
    private val pendingDelete = MutableStateFlow<PendingDelete?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val spots: Flow<List<SpotRow>> = repos.users.currentUserId.flatMapLatest { userId ->
        repos.spots.observeRows(cityId, userId.orEmpty())
    }

    private val listState: Flow<SpotListState> = combine(
        spots,
        prefs.sortMode(SortScope.SPOTS),
        filters,
        repos.photos.observeByCity(cityId),
        ::SpotListState,
    )

    val uiState: StateFlow<CityUiState> = combine(
        repos.cities.observeCity(cityId),
        listState,
        repos.users.observeUserCount(),
        pendingDelete,
    ) { place, list, userCount, pending ->
        CityUiState(
            place = place,
            rows = list.rows.filter(list.filters::matches).sortedSpots(list.sortMode),
            attachments = list.attachments,
            filters = list.filters,
            sortMode = list.sortMode,
            userCount = userCount,
            pendingDelete = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CityUiState())

    fun setStatusFilter(status: SpotStatus?) = filters.update { it.copy(status = status) }

    fun setPriorityFilter(priority: Priority?) = filters.update { it.copy(priority = priority) }

    fun setTypeFilter(type: SpotType?) = filters.update { it.copy(type = type) }

    fun clearFilters() {
        filters.value = SpotFilters()
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch { prefs.setSortMode(SortScope.SPOTS, mode) }
    }

    fun deletePhoto(photo: LinkEntity) {
        viewModelScope.launch { repos.photos.remove(photo) }
    }

    fun toggleHeart(spotId: String) {
        viewModelScope.launch { repos.spots.toggleHeart(spotId) }
    }

    fun setPriority(id: String, priority: Priority) {
        viewModelScope.launch { repos.spots.setPriority(id, priority) }
    }

    fun setStatus(id: String, status: SpotStatus) {
        viewModelScope.launch { repos.spots.setStatus(id, status) }
    }

    fun move(id: String, previousKey: String?, nextKey: String?) {
        viewModelScope.launch { repos.spots.move(id, previousKey, nextKey) }
    }

    fun renamePlace(name: String) {
        viewModelScope.launch { repos.cities.rename(cityId, name) }
    }

    /** Blank lets the automatic search have another go. */
    fun setImageUrl(url: String) {
        viewModelScope.launch { repos.cities.setImageUrl(cityId, url) }
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
        viewModelScope.launch { onCreated(repos.routes.create(cityId, title, unvisited)) }
    }

    fun confirmDelete() {
        val target = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { repos.spots.delete(target.id) }
    }
}

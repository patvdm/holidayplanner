package com.pat.app.holidayplanner.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Route
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.domain.SearchHitType

enum class AppTab(val rootKey: NavKey, @param:StringRes val label: Int, val icon: ImageVector) {
    EXPLORE(ExploreRootKey, R.string.tab_explore, Icons.Outlined.Explore),
    TRIPS(TripsRootKey, R.string.tab_trips, Icons.Outlined.Luggage),
    ROUTES(RoutesRootKey, R.string.tab_routes, Icons.Outlined.Route),
    SPOTS(SpotsRootKey, R.string.tab_spots, Icons.Outlined.Place),
}

/**
 * One back stack per tab. The stacks of every visited tab are concatenated (in the order the tabs
 * were last selected) into the single list NavDisplay shows, so switching tabs keeps every tab's
 * screens, ViewModels and saved state alive.
 *
 * Back pops the current tab's stack and stops there: every tab root is a root, and leaving one is
 * leaving the app. Walking back through the tabs visited earlier would mean the way out of the app
 * depends on where you have been, which is no way to find it.
 */
@Stable
class TabNavState(
    private val stacks: Map<AppTab, NavBackStack<NavKey>>,
    private val tabOrder: SnapshotStateList<AppTab>,
) {
    val currentTab: AppTab
        get() = tabOrder.last()

    val backStack: List<NavKey> by derivedStateOf {
        tabOrder.flatMap { tab -> stacks.getValue(tab).toList() }
    }

    val currentKey: NavKey
        get() = backStack.last()

    /** True while the map is what is showing, so the tab bar can light its middle button. */
    val onMap: Boolean
        get() = currentKey is MapKey

    fun selectTab(tab: AppTab) {
        // The map belongs to the moment it was opened, not to the tab it was opened over. Leaving it
        // for that same tab goes back to where it was opened, say a trip, rather than to the tab's
        // root: the tab did not look selected, so the tap meant "back there", not "start over".
        if (onMap) {
            back()
            if (tab == currentTab) return
        }
        if (tab == currentTab) {
            popToRoot(tab)
        } else {
            tabOrder.remove(tab)
            tabOrder.add(tab)
        }
    }

    /** Shows the map for the screen that is up now. Tapping again while it shows does nothing. */
    fun openMap() {
        if (onMap) return
        push(MapKey.forScreen(currentKey))
    }

    fun push(key: NavKey) {
        val stack = stacks.getValue(currentTab)
        if (stack.last() != key) stack.add(key)
    }

    /**
     * Closes the current screen, then opens [key] on [tab]. Used by search, where the result often
     * lives under a different tab than the one the search was started from.
     */
    fun openIn(tab: AppTab, key: NavKey) {
        back()
        if (tab != currentTab) selectTab(tab)
        push(key)
    }

    /** Opens a search result, which knows what kind of thing it is but not where that kind lives. */
    fun openSearchHit(type: SearchHitType, id: String, navId: String) {
        when (type) {
            SearchHitType.COUNTRY -> openIn(AppTab.EXPLORE, CountryKey(navId))
            // A city has no screen of its own: it opens as its country, scrolled down to it.
            SearchHitType.CITY -> openIn(AppTab.EXPLORE, CountryKey(navId, cityId = id))
            SearchHitType.SPOT, SearchHitType.LINK -> openIn(AppTab.EXPLORE, SpotKey(navId))
            SearchHitType.TRIP -> openIn(AppTab.TRIPS, TripKey(navId))
            SearchHitType.TRIP_ITEM -> openIn(AppTab.TRIPS, TripItemKey(navId))
        }
    }

    /** Closes the current screen and shows [tab] at its root. */
    fun openTabRoot(tab: AppTab) {
        back()
        if (tab != currentTab) selectTab(tab)
        popToRoot(tab)
    }

    /** False when this tab is already at its root, which is the caller's cue to leave the app. */
    fun back(): Boolean {
        val stack = stacks.getValue(currentTab)
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    private fun popToRoot(tab: AppTab) {
        val stack = stacks.getValue(tab)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

@Composable
fun rememberTabNavState(): TabNavState {
    val explore = rememberNavBackStack(ExploreRootKey)
    val trips = rememberNavBackStack(TripsRootKey)
    val routes = rememberNavBackStack(RoutesRootKey)
    val spots = rememberNavBackStack(SpotsRootKey)
    val tabOrder = rememberSaveable(saver = TabOrderSaver) { mutableStateListOf(AppTab.EXPLORE) }
    return remember(explore, trips, routes, spots, tabOrder) {
        TabNavState(
            stacks = mapOf(
                AppTab.EXPLORE to explore,
                AppTab.TRIPS to trips,
                AppTab.ROUTES to routes,
                AppTab.SPOTS to spots,
            ),
            tabOrder = tabOrder,
        )
    }
}

private val TabOrderSaver = listSaver<SnapshotStateList<AppTab>, String>(
    save = { order -> order.map(AppTab::name) },
    restore = { saved -> mutableStateListOf<AppTab>().apply { addAll(saved.map(AppTab::valueOf)) } },
)

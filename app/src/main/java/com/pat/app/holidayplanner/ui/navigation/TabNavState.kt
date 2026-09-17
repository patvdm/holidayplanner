package com.pat.app.holidayplanner.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Luggage
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
}

/**
 * One back stack per tab. The stacks of every visited tab are concatenated (in the order the tabs
 * were last selected) into the single list NavDisplay shows, so switching tabs keeps every tab's
 * screens, ViewModels and saved state alive. Back pops the current tab's stack, and at a tab root it
 * returns to the previously used tab.
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

    fun selectTab(tab: AppTab) {
        if (tab == currentTab) {
            popToRoot(tab)
        } else {
            tabOrder.remove(tab)
            tabOrder.add(tab)
        }
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
    fun openSearchHit(type: SearchHitType, navId: String) {
        when (type) {
            SearchHitType.COUNTRY -> openIn(AppTab.EXPLORE, CountryKey(navId))
            SearchHitType.CITY -> openIn(AppTab.EXPLORE, CityKey(navId))
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

    fun back() {
        val stack = stacks.getValue(currentTab)
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            tabOrder.size > 1 -> {
                val leaving = tabOrder.removeAt(tabOrder.lastIndex)
                popToRoot(leaving)
            }
        }
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
    val tabOrder = rememberSaveable(saver = TabOrderSaver) { mutableStateListOf(AppTab.EXPLORE) }
    return remember(explore, trips, tabOrder) {
        TabNavState(
            stacks = mapOf(AppTab.EXPLORE to explore, AppTab.TRIPS to trips),
            tabOrder = tabOrder,
        )
    }
}

private val TabOrderSaver = listSaver<SnapshotStateList<AppTab>, String>(
    save = { order -> order.map(AppTab::name) },
    restore = { saved -> mutableStateListOf<AppTab>().apply { addAll(saved.map(AppTab::valueOf)) } },
)

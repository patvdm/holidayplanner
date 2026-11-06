package com.pat.app.holidayplanner.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.pat.app.holidayplanner.R
import com.pat.app.holidayplanner.ui.explore.CountriesScreen
import com.pat.app.holidayplanner.ui.explore.CountryScreen
import com.pat.app.holidayplanner.ui.explore.SpotDetailScreen
import com.pat.app.holidayplanner.ui.map.MapScope
import com.pat.app.holidayplanner.ui.map.MapScreen
import com.pat.app.holidayplanner.ui.routes.RoutesScreen
import com.pat.app.holidayplanner.ui.search.SearchScreen
import com.pat.app.holidayplanner.ui.settings.SettingsScreen
import com.pat.app.holidayplanner.ui.spots.SpotsScreen
import com.pat.app.holidayplanner.ui.trips.RouteBuilderScreen
import com.pat.app.holidayplanner.ui.trips.TripEditorScreen
import com.pat.app.holidayplanner.ui.trips.TripItemEditorScreen
import com.pat.app.holidayplanner.ui.trips.TripScreen
import com.pat.app.holidayplanner.ui.trips.TripsScreen

/** The map sits in the middle of the five buttons, after Explore and Trips. */
private const val MAP_SLOT = 2

@Composable
fun AppNavigation() {
    val nav = rememberTabNavState()
    val activity = LocalActivity.current
    val showTabs = nav.currentKey !is FullScreenKey

    Scaffold(
        // Each screen has its own Scaffold and top bar, which already pad for the status bar. Without
        // this the two stack up and every title sits too low.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    AppTab.entries.forEachIndexed { index, tab ->
                        if (index == MAP_SLOT) {
                            NavigationBarItem(
                                selected = nav.onMap,
                                onClick = nav::openMap,
                                icon = { Icon(Icons.Outlined.Map, contentDescription = null) },
                                label = { Text(stringResource(R.string.map_title)) },
                            )
                        }
                        NavigationBarItem(
                            selected = tab == nav.currentTab && !nav.onMap,
                            onClick = { nav.selectTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = nav.backStack,
            modifier = Modifier.padding(padding),
            // Nothing left to pop means the tab root is showing, and back out of that is out.
            onBack = { if (!nav.back()) activity?.finish() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>(),
            ),
            entryProvider = entryProvider {
                entry<ExploreRootKey> {
                    CountriesScreen(
                        onSearch = { nav.push(SearchKey) },
                        onSettings = { nav.push(SettingsKey) },
                        onOpenCountry = { nav.push(CountryKey(it)) },
                    )
                }
                entry<CountryKey> { key ->
                    CountryScreen(
                        countryId = key.countryId,
                        focusCityId = key.cityId,
                        onBack = nav::back,
                        onOpenSpot = { nav.push(SpotKey(it)) },
                        onOpenRoute = { nav.push(RouteKey(it)) },
                        onShowCityOnMap = { cityId -> nav.push(MapKey(countryId = key.countryId, cityId = cityId)) },
                    )
                }
                entry<SpotKey> { key ->
                    SpotDetailScreen(
                        spotId = key.spotId,
                        tripItemId = key.tripItemId,
                        onBack = nav::back,
                        // The trip stop now points elsewhere, so show that spot in this one's place.
                        onTripSpotChanged = { nav.replace(SpotKey(it, key.tripItemId)) },
                        // The same map as the middle button, which from a spot opens on that spot.
                        onShowOnMap = nav::openMap,
                        onOpenRoute = { nav.push(RouteKey(it)) },
                    )
                }
                entry<SpotsRootKey> {
                    SpotsScreen(
                        onSearch = { nav.push(SearchKey) },
                        onSettings = { nav.push(SettingsKey) },
                        onOpenSpot = { nav.push(SpotKey(it)) },
                    )
                }
                entry<RoutesRootKey> {
                    RoutesScreen(
                        onSearch = { nav.push(SearchKey) },
                        onSettings = { nav.push(SettingsKey) },
                        onOpenRoute = { nav.push(RouteKey(it)) },
                    )
                }
                entry<TripsRootKey> {
                    TripsScreen(
                        onSearch = { nav.push(SearchKey) },
                        onSettings = { nav.push(SettingsKey) },
                        onOpenTrip = { nav.push(TripKey(it)) },
                    )
                }
                entry<TripKey> { key ->
                    TripScreen(
                        tripId = key.tripId,
                        onBack = nav::back,
                        onOpenItem = { nav.push(TripItemKey(it)) },
                        // A spot on the trip opens as the spot itself, with the trip's side of it on top.
                        onOpenStop = { spotId, itemId -> nav.push(SpotKey(spotId, tripItemId = itemId)) },
                        onOpenRoute = { nav.push(RouteKey(it)) },
                        onEditTrip = { nav.push(TripEditorKey(key.tripId)) },
                        onShowDayOnMap = { day -> nav.push(MapKey(tripId = key.tripId, day = day.toString())) },
                    )
                }
                entry<TripEditorKey> { key ->
                    TripEditorScreen(
                        tripId = key.tripId,
                        onBack = nav::back,
                        // Back twice: off the editor, then off the trip that no longer exists.
                        onDeleted = { nav.back(); nav.back() },
                    )
                }
                entry<TripItemKey> { key ->
                    TripItemEditorScreen(
                        itemId = key.itemId,
                        onBack = nav::back,
                        // On top of the item, as from a route, so back comes to the item and the trip.
                        onOpenSpot = { nav.push(SpotKey(it)) },
                        onShowSpotOnMap = { nav.push(MapKey(spotId = it)) },
                    )
                }
                entry<RouteKey> { key ->
                    RouteBuilderScreen(
                        routeId = key.routeId,
                        onBack = nav::back,
                        onDeleted = nav::back,
                        // Pushed onto this tab rather than Explore's, so back comes here.
                        onOpenSpot = { nav.push(SpotKey(it)) },
                        onShowOnMap = nav::openMap,
                    )
                }
                entry<MapKey> { key ->
                    MapScreen(
                        scope = MapScope(key.countryId, key.cityId, key.spotId, key.routeId, key.tripId, key.day),
                        onBack = nav::back,
                        // Over the map rather than on Explore, so back from the spot comes back here.
                        onOpenSpot = { nav.push(SpotKey(it)) },
                        onOpenRoute = { nav.push(RouteKey(it)) },
                    )
                }
                entry<SearchKey> {
                    SearchScreen(onBack = nav::back, onOpen = nav::openSearchHit)
                }
                entry<SettingsKey> { SettingsScreen(onBack = nav::back) }
            },
        )
    }
}

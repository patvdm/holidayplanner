package com.pat.app.holidayplanner.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.pat.app.holidayplanner.ui.explore.CountriesScreen
import com.pat.app.holidayplanner.ui.feed.FeedScreen
import com.pat.app.holidayplanner.ui.explore.CountryScreen
import com.pat.app.holidayplanner.ui.explore.CityScreen
import com.pat.app.holidayplanner.ui.explore.SpotDetailScreen
import com.pat.app.holidayplanner.ui.inbox.InboxScreen
import com.pat.app.holidayplanner.ui.nearme.NearMeScreen
import com.pat.app.holidayplanner.ui.search.SearchScreen
import com.pat.app.holidayplanner.ui.settings.SettingsScreen
import com.pat.app.holidayplanner.ui.trips.RouteBuilderScreen
import com.pat.app.holidayplanner.ui.trips.TripEditorScreen
import com.pat.app.holidayplanner.ui.trips.TripItemEditorScreen
import com.pat.app.holidayplanner.ui.trips.TripScreen
import com.pat.app.holidayplanner.ui.trips.TripsScreen
import com.pat.app.holidayplanner.data.repo.LinkRepository
import org.koin.compose.koinInject

@Composable
fun AppNavigation(linkRepository: LinkRepository = koinInject()) {
    val nav = rememberTabNavState()
    val showTabs = nav.currentKey !is FullScreenKey
    val inboxCount by linkRepository.observeInboxCount().collectAsStateWithLifecycle(initialValue = 0)

    Scaffold(
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == nav.currentTab,
                            onClick = { nav.selectTab(tab) },
                            icon = {
                                if (tab == AppTab.INBOX && inboxCount > 0) {
                                    BadgedBox(badge = { Badge { Text(inboxCount.toString()) } }) {
                                        Icon(tab.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
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
            onBack = nav::back,
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
                        onOpenFeed = { nav.push(FeedKey) },
                        onNearMe = { nav.push(NearMeKey) },
                    )
                }
                entry<CountryKey> { key ->
                    CountryScreen(
                        countryId = key.countryId,
                        onBack = nav::back,
                        onOpenPlace = { nav.push(CityKey(it)) },
                        onOpenSpot = { nav.push(SpotKey(it)) },
                    )
                }
                entry<CityKey> { key ->
                    CityScreen(
                        cityId = key.cityId,
                        onBack = nav::back,
                        onOpenSpot = { nav.push(SpotKey(it)) },
                        onOpenRoute = { nav.push(RouteKey(it)) },
                        onNearMe = { nav.push(NearMeKey) },
                    )
                }
                entry<SpotKey> { key ->
                    SpotDetailScreen(spotId = key.spotId, onBack = nav::back)
                }
                entry<InboxRootKey> {
                    InboxScreen(onSearch = { nav.push(SearchKey) }, onSettings = { nav.push(SettingsKey) })
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
                        onEditTrip = { nav.push(TripEditorKey(key.tripId)) },
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
                        onOpenRoute = { nav.push(RouteKey(key.itemId)) },
                    )
                }
                entry<RouteKey> { key ->
                    RouteBuilderScreen(itemId = key.itemId, onBack = nav::back)
                }
                entry<NearMeKey> {
                    NearMeScreen(
                        onBack = nav::back,
                        onOpenSpot = { nav.openIn(AppTab.EXPLORE, SpotKey(it)) },
                    )
                }
                entry<FeedKey> {
                    FeedScreen(
                        onBack = nav::back,
                        onOpenSpot = { nav.openIn(AppTab.EXPLORE, SpotKey(it)) },
                        onOpenTrip = { nav.openIn(AppTab.TRIPS, TripKey(it)) },
                        onOpenInbox = { nav.openTabRoot(AppTab.INBOX) },
                    )
                }
                entry<SearchKey> {
                    SearchScreen(
                        onBack = nav::back,
                        onOpenCountry = { nav.openIn(AppTab.EXPLORE, CountryKey(it)) },
                        onOpenCity = { nav.openIn(AppTab.EXPLORE, CityKey(it)) },
                        onOpenSpot = { nav.openIn(AppTab.EXPLORE, SpotKey(it)) },
                        onOpenTrip = { nav.openIn(AppTab.TRIPS, TripKey(it)) },
                        onOpenTripItem = { nav.openIn(AppTab.TRIPS, TripItemKey(it)) },
                        onOpenInbox = { nav.openTabRoot(AppTab.INBOX) },
                    )
                }
                entry<SettingsKey> { SettingsScreen(onBack = nav::back) }
            },
        )
    }
}

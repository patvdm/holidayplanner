package com.pat.app.holidayplanner.di

import com.pat.app.holidayplanner.data.repo.CityLift
import com.pat.app.holidayplanner.data.local.AppDatabase
import com.pat.app.holidayplanner.data.location.AndroidReverseGeocoder
import com.pat.app.holidayplanner.data.location.DeviceLocation
import com.pat.app.holidayplanner.data.location.FusedDeviceLocation
import com.pat.app.holidayplanner.data.location.ReverseGeocoder
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.remote.GooglePlacesSearch
import com.pat.app.holidayplanner.data.image.PhotoQueue
import com.pat.app.holidayplanner.data.update.AppUpdater
import com.pat.app.holidayplanner.data.update.UpdateScheduler
import com.pat.app.holidayplanner.data.update.UpdateServer
import com.pat.app.holidayplanner.data.image.PhotoScaler
import com.pat.app.holidayplanner.data.remote.HttpLinkResolver
import com.pat.app.holidayplanner.data.remote.CloudflarePhotos
import com.pat.app.holidayplanner.data.remote.ImageStore
import com.pat.app.holidayplanner.data.remote.RoutedImageStore
import com.pat.app.holidayplanner.data.remote.LinkResolver
import com.pat.app.holidayplanner.data.remote.PictureSource
import com.pat.app.holidayplanner.data.remote.PlacesSearch
import com.pat.app.holidayplanner.data.remote.WikimediaPictures
import com.pat.app.holidayplanner.data.remote.SheetsApi
import com.pat.app.holidayplanner.data.remote.SheetsBackend
import com.pat.app.holidayplanner.data.repo.CountryRepository
import com.pat.app.holidayplanner.data.repo.HierarchyResolver
import com.pat.app.holidayplanner.data.repo.LinkRepository
import com.pat.app.holidayplanner.data.repo.AttachmentRepository
import com.pat.app.holidayplanner.data.repo.LocalWriter
import com.pat.app.holidayplanner.data.repo.CityRepository
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.data.repo.CoordinateBackfill
import com.pat.app.holidayplanner.data.repo.OpeningHoursLookup
import com.pat.app.holidayplanner.data.repo.PictureBackfill
import com.pat.app.holidayplanner.data.repo.Places
import com.pat.app.holidayplanner.data.repo.RouteRepository
import com.pat.app.holidayplanner.data.repo.SearchRepository
import com.pat.app.holidayplanner.data.repo.SpotPinRepository
import com.pat.app.holidayplanner.data.repo.TripRepository
import com.pat.app.holidayplanner.data.repo.UserRepository
import com.pat.app.holidayplanner.data.sync.RoomTransactor
import com.pat.app.holidayplanner.data.sync.SyncOutcomeStore
import com.pat.app.holidayplanner.data.sync.SyncRepository
import com.pat.app.holidayplanner.data.sync.SyncScheduler
import com.pat.app.holidayplanner.data.sync.SyncStatusRepository
import com.pat.app.holidayplanner.data.sync.Transactor
import com.pat.app.holidayplanner.data.sync.syncTables
import com.pat.app.holidayplanner.domain.AppClock
import com.pat.app.holidayplanner.ui.SessionViewModel
import com.pat.app.holidayplanner.ui.explore.CountriesViewModel
import com.pat.app.holidayplanner.ui.explore.CountryViewModel
import com.pat.app.holidayplanner.ui.explore.CountryRepositories
import com.pat.app.holidayplanner.ui.components.PlaceSearchTarget
import com.pat.app.holidayplanner.ui.components.PlaceSearchViewModel
import com.pat.app.holidayplanner.ui.explore.SpotDetailViewModel
import com.pat.app.holidayplanner.ui.share.ShareViewModel
import com.pat.app.holidayplanner.ui.sync.SyncRefreshViewModel
import com.pat.app.holidayplanner.ui.trips.RouteViewModel
import com.pat.app.holidayplanner.ui.trips.TripItemViewModel
import com.pat.app.holidayplanner.ui.trips.TripViewModel
import com.pat.app.holidayplanner.ui.trips.TripsViewModel
import com.pat.app.holidayplanner.ui.onboarding.OnboardingViewModel
import com.pat.app.holidayplanner.ui.map.MapScope
import com.pat.app.holidayplanner.ui.map.MapSources
import com.pat.app.holidayplanner.ui.map.MapViewModel
import com.pat.app.holidayplanner.ui.routes.AddToRouteViewModel
import com.pat.app.holidayplanner.ui.routes.RoutesViewModel
import com.pat.app.holidayplanner.ui.search.SearchViewModel
import com.pat.app.holidayplanner.ui.settings.SettingsViewModel
import com.pat.app.holidayplanner.ui.spots.SpotsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single<AppClock> { AppClock { System.currentTimeMillis() } }
    single { AppPreferences(androidContext()) }
    // The preferences are also where a finished sync run records itself.
    single<SyncOutcomeStore> { get<AppPreferences>() }
    single { AppDatabase.build(androidContext()) }
    single<Transactor> { RoomTransactor(get()) }
    single { SyncScheduler(androidContext()) }

    single { get<AppDatabase>().userDao() }
    single { get<AppDatabase>().countryDao() }
    single { get<AppDatabase>().cityDao() }
    single { get<AppDatabase>().spotDao() }
    single { get<AppDatabase>().linkDao() }
    single { get<AppDatabase>().tripDao() }
    single { get<AppDatabase>().tripItemDao() }
    single { get<AppDatabase>().routeDao() }
    single { get<AppDatabase>().routeStopDao() }
    single { get<AppDatabase>().searchDao() }
    single { get<AppDatabase>().cascadeDao() }
    single { get<AppDatabase>().syncStateDao() }

    single<PlacesSearch> { GooglePlacesSearch(androidContext()) }
    single<LinkResolver> { HttpLinkResolver() }
    single<PictureSource> { WikimediaPictures() }
    single<ReverseGeocoder> { AndroidReverseGeocoder(androidContext()) }
    single<DeviceLocation> { FusedDeviceLocation(androidContext()) }
    single { SheetsApi(get()) }
    single<SheetsBackend> { get<SheetsApi>() }
    single { CloudflarePhotos() }
    // Photos go to Cloudflare when it is set up, and to Drive through the sheet's script otherwise.
    single<ImageStore> { RoutedImageStore(get<CloudflarePhotos>(), get<SheetsApi>()) }
    single { PhotoScaler(androidContext()) }
    single { PhotoQueue(androidContext(), get()) }
    single { UpdateServer() }
    single { AppUpdater(androidContext(), get()) }
    single { UpdateScheduler(androidContext()) }
    single { syncTables(get()) }
    single { SyncRepository(get(), get(), get(), get(), get(), get()) }
    single { SyncStatusRepository(get(), get(), get()) }

    single { LocalWriter(get(), get(), get()) }
    single { UserRepository(get(), get(), get()) }
    single { CountryRepository(get(), get(), get(), get()) }
    single { CityRepository(get(), get(), get(), get()) }
    single { CityLift(get(), get(), get()) }
    single { SpotRepository(get(), get(), get(), get(), get()) }
    single { LinkRepository(get(), get(), get()) }
    single { Places(get(), get(), get()) }
    single { AttachmentRepository(get(), get(), get(), get()) }
    single { CountryRepositories(get(), get(), get(), get(), get(), get()) }
    single { HierarchyResolver(get(), get()) }
    single { TripRepository(get(), get(), get(), get(), get()) }
    single { RouteRepository(get(), get(), get(), get(), get()) }
    single { SearchRepository(get()) }
    single { SpotPinRepository(get()) }
    single { CoordinateBackfill(get(), get(), get()) }
    single { PictureBackfill(get(), get(), get(), get(), get()) }
    single { OpeningHoursLookup(get(), get(), get()) }

    viewModelOf(::SessionViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::CountriesViewModel)
    viewModelOf(::ShareViewModel)
    viewModelOf(::TripsViewModel)
    viewModelOf(::SyncRefreshViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::SpotsViewModel)
    viewModelOf(::RoutesViewModel)

    // These three are scoped to one row, so the id comes from the screen that opened them.
    viewModel { (countryId: String) -> CountryViewModel(countryId, get(), get()) }
    viewModel { (spotId: String) -> SpotDetailViewModel(spotId, get(), get(), get(), get(), get()) }
    viewModel { (tripId: String) -> TripViewModel(tripId, get(), get(), get(), get()) }
    viewModel { (itemId: String) -> TripItemViewModel(itemId, get(), get()) }
    viewModel { (routeId: String) -> RouteViewModel(routeId, get(), get(), get(), get()) }
    viewModel { (spotId: String) -> AddToRouteViewModel(spotId, get(), get()) }
    factory { MapSources(get(), get(), get(), get(), get()) }
    viewModel { (scope: MapScope) -> MapViewModel(scope, get(), get()) }
    viewModel { (target: PlaceSearchTarget) ->
        PlaceSearchViewModel(target, get(), get(), get(), get(), get())
    }
}

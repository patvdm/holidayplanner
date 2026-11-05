package com.pat.app.holidayplanner.data.sync

import com.pat.app.holidayplanner.data.local.AppDatabase

/**
 * The tables sync walks, in dependency order: parents before children, so a fresh pull inserts a
 * country before the cities that point at it. The names must match the sheet tabs exactly.
 *
 * This is a named type rather than a bare list because a dependency container keys on the erased
 * type, where every `List` looks alike.
 */
class SyncTables(val all: List<SyncTable<*>>) {

    fun named(sheetName: String): SyncTable<*>? = all.firstOrNull { it.sheetName == sheetName }
}

fun syncTables(db: AppDatabase): SyncTables = SyncTables(
    listOf(
        SyncTable("Users", db.userDao(), ::userToRow, ::userFromRow),
        SyncTable("Countries", db.countryDao(), ::countryToRow, ::countryFromRow),
        SyncTable("Cities", db.cityDao(), ::cityToRow, ::cityFromRow),
        SyncTable("Spots", db.spotDao(), ::spotToRow, ::spotFromRow),
        SyncTable("Links", db.linkDao(), ::linkToRow, ::linkFromRow),
        SyncTable("Trips", db.tripDao(), ::tripToRow, ::tripFromRow),
        SyncTable("TripItems", db.tripItemDao(), ::tripItemToRow, ::tripItemFromRow),
        SyncTable("Routes", db.routeDao(), ::routeToRow, ::routeFromRow),
        SyncTable("RouteStops", db.routeStopDao(), ::routeStopToRow, ::routeStopFromRow),
    ),
)

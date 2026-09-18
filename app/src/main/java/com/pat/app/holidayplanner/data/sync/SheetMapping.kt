package com.pat.app.holidayplanner.data.sync

import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.HeartEntity
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.CityEntity
import com.pat.app.holidayplanner.data.local.RouteEntity
import com.pat.app.holidayplanner.data.local.RouteStopEntity
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.local.UserEntity
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.domain.TripItemType

/**
 * Entity to sheet row and back. Each pair of functions sits together so the column names can be
 * checked against each other at a glance, and a round-trip test covers every table.
 *
 * `syncedAt` is never pushed, because the server owns it, and `dirty` never leaves the device.
 */

private fun buildRow(meta: SyncMeta, block: MutableMap<String, String>.() -> Unit): Map<String, String> =
    mutableMapOf(
        "id" to meta.id,
        "createdAt" to meta.createdAt.toString(),
        "createdBy" to meta.createdBy,
        "updatedAt" to meta.updatedAt.toString(),
        "updatedBy" to meta.updatedBy,
        "deleted" to meta.deleted.toString(),
    ).apply(block)

/** Rows without an id are skipped: an empty trailing row in the sheet is not a record. */
private fun SheetRow.meta(): SyncMeta? {
    val id = str("id").trim()
    if (id.isEmpty()) return null
    return SyncMeta(
        id = id,
        createdAt = long("createdAt"),
        createdBy = str("createdBy"),
        updatedAt = long("updatedAt"),
        updatedBy = str("updatedBy"),
        syncedAt = long("syncedAt"),
        deleted = bool("deleted"),
        dirty = false,
    )
}

private fun Double?.orBlank(): String = this?.toString().orEmpty()

private fun Long?.orBlank(): String = this?.toString().orEmpty()

// Users -----------------------------------------------------------------------------------------

fun userToRow(entity: UserEntity): Map<String, String> = buildRow(entity.meta) {
    put("name", entity.name)
}

fun userFromRow(row: SheetRow): UserEntity? {
    val meta = row.meta() ?: return null
    return UserEntity(meta = meta, name = row.str("name"))
}

// Countries -------------------------------------------------------------------------------------

fun countryToRow(entity: CountryEntity): Map<String, String> = buildRow(entity.meta) {
    put("name", entity.name)
    put("isoCode", entity.isoCode)
    put("sortKey", entity.sortKey)
    put("priority", entity.priority.name)
}

fun countryFromRow(row: SheetRow): CountryEntity? {
    val meta = row.meta() ?: return null
    return CountryEntity(
        meta = meta,
        name = row.str("name"),
        isoCode = row.str("isoCode").trim().uppercase(),
        sortKey = row.str("sortKey").trim(),
        priority = Priority.fromSheet(row.str("priority")),
    )
}

// Places ----------------------------------------------------------------------------------------

fun cityToRow(entity: CityEntity): Map<String, String> = buildRow(entity.meta) {
    put("countryId", entity.countryId)
    put("name", entity.name)
    put("googlePlaceId", entity.googlePlaceId)
    put("lat", entity.lat.orBlank())
    put("lng", entity.lng.orBlank())
    put("sortKey", entity.sortKey)
    put("priority", entity.priority.name)
}

fun cityFromRow(row: SheetRow): CityEntity? {
    val meta = row.meta() ?: return null
    return CityEntity(
        meta = meta,
        countryId = row.str("countryId").trim(),
        name = row.str("name"),
        googlePlaceId = row.str("googlePlaceId").trim(),
        lat = row.doubleOrNull("lat"),
        lng = row.doubleOrNull("lng"),
        sortKey = row.str("sortKey").trim(),
        priority = Priority.fromSheet(row.str("priority")),
    )
}

// Spots -----------------------------------------------------------------------------------------

fun spotToRow(entity: SpotEntity): Map<String, String> = buildRow(entity.meta) {
    put("parentCityId", entity.parentCityId)
    put("name", entity.name)
    put("type", entity.type.name)
    put("googlePlaceId", entity.googlePlaceId)
    put("lat", entity.lat.orBlank())
    put("lng", entity.lng.orBlank())
    put("address", entity.address)
    put("mapsUrl", entity.mapsUrl)
    put("notes", entity.notes)
    put("tags", entity.tags.joinToString(","))
    put("status", entity.status.name)
    put("sortKey", entity.sortKey)
    put("priority", entity.priority.name)
}

fun spotFromRow(row: SheetRow): SpotEntity? {
    val meta = row.meta() ?: return null
    return SpotEntity(
        meta = meta,
        parentCityId = row.str("parentCityId").trim(),
        name = row.str("name"),
        type = SpotType.fromSheet(row.str("type")),
        googlePlaceId = row.str("googlePlaceId").trim(),
        lat = row.doubleOrNull("lat"),
        lng = row.doubleOrNull("lng"),
        address = row.str("address"),
        mapsUrl = row.str("mapsUrl").trim(),
        notes = row.str("notes"),
        tags = row.list("tags"),
        status = SpotStatus.fromSheet(row.str("status")),
        sortKey = row.str("sortKey").trim(),
        priority = Priority.fromSheet(row.str("priority")),
    )
}

// Links -----------------------------------------------------------------------------------------

fun linkToRow(entity: LinkEntity): Map<String, String> = buildRow(entity.meta) {
    put("spotId", entity.spotId)
    put("url", entity.url)
    put("source", entity.source.name)
    put("note", entity.note)
}

fun linkFromRow(row: SheetRow): LinkEntity? {
    val meta = row.meta() ?: return null
    return LinkEntity(
        meta = meta,
        spotId = row.str("spotId").trim(),
        url = row.str("url").trim(),
        source = LinkSource.fromSheet(row.str("source")),
        note = row.str("note"),
    )
}

// Hearts ----------------------------------------------------------------------------------------

fun heartToRow(entity: HeartEntity): Map<String, String> = buildRow(entity.meta) {
    put("spotId", entity.spotId)
    put("userId", entity.userId)
}

fun heartFromRow(row: SheetRow): HeartEntity? {
    val meta = row.meta() ?: return null
    return HeartEntity(
        meta = meta,
        spotId = row.str("spotId").trim(),
        userId = row.str("userId").trim(),
    )
}

// Trips -----------------------------------------------------------------------------------------

fun tripToRow(entity: TripEntity): Map<String, String> = buildRow(entity.meta) {
    put("name", entity.name)
    put("countryId", entity.countryId)
    put("startDate", entity.startDate)
    put("endDate", entity.endDate)
    put("notes", entity.notes)
}

fun tripFromRow(row: SheetRow): TripEntity? {
    val meta = row.meta() ?: return null
    return TripEntity(
        meta = meta,
        name = row.str("name"),
        countryId = row.str("countryId").trim(),
        startDate = row.str("startDate").trim(),
        endDate = row.str("endDate").trim(),
        notes = row.str("notes"),
    )
}

// Trip items ------------------------------------------------------------------------------------

fun tripItemToRow(entity: TripItemEntity): Map<String, String> = buildRow(entity.meta) {
    put("tripId", entity.tripId)
    put("type", entity.type.name)
    put("title", entity.title)
    put("origin", entity.origin)
    put("destination", entity.destination)
    put("startLocal", entity.startLocal)
    put("startTimeZone", entity.startTimeZone)
    put("endLocal", entity.endLocal)
    put("endTimeZone", entity.endTimeZone)
    put("spotId", entity.spotId)
    put("routeId", entity.routeId)
    put("bookingRef", entity.bookingRef)
    put("costMinor", entity.costMinor.orBlank())
    put("currency", entity.currency)
    put("docUrl", entity.docUrl)
    put("notes", entity.notes)
}

/** Rows of the item kinds that no longer exist are dropped rather than reshaped into something else. */
fun tripItemFromRow(row: SheetRow): TripItemEntity? {
    val meta = row.meta() ?: return null
    val type = TripItemType.fromSheetOrNull(row.str("type")) ?: return null
    return TripItemEntity(
        meta = meta,
        tripId = row.str("tripId").trim(),
        type = type,
        title = row.str("title"),
        origin = row.str("origin"),
        destination = row.str("destination"),
        startLocal = row.str("startLocal").trim(),
        startTimeZone = row.str("startTimeZone").trim(),
        endLocal = row.str("endLocal").trim(),
        endTimeZone = row.str("endTimeZone").trim(),
        spotId = row.str("spotId").trim(),
        routeId = row.str("routeId").trim(),
        bookingRef = row.str("bookingRef").trim(),
        costMinor = row.longOrNull("costMinor"),
        currency = row.str("currency").trim().uppercase(),
        docUrl = row.str("docUrl").trim(),
        notes = row.str("notes"),
    )
}

// Routes ----------------------------------------------------------------------------------------

fun routeToRow(entity: RouteEntity): Map<String, String> = buildRow(entity.meta) {
    put("cityId", entity.cityId)
    put("name", entity.name)
    put("completed", entity.completed.toString())
    put("notes", entity.notes)
}

fun routeFromRow(row: SheetRow): RouteEntity? {
    val meta = row.meta() ?: return null
    return RouteEntity(
        meta = meta,
        cityId = row.str("cityId").trim(),
        name = row.str("name"),
        completed = row.bool("completed"),
        notes = row.str("notes"),
    )
}

// Route stops -----------------------------------------------------------------------------------

fun routeStopToRow(entity: RouteStopEntity): Map<String, String> = buildRow(entity.meta) {
    put("routeId", entity.routeId)
    put("spotId", entity.spotId)
    put("sortKey", entity.sortKey)
}

fun routeStopFromRow(row: SheetRow): RouteStopEntity? {
    val meta = row.meta() ?: return null
    return RouteStopEntity(
        meta = meta,
        routeId = row.str("routeId").trim(),
        spotId = row.str("spotId").trim(),
        sortKey = row.str("sortKey").trim(),
    )
}

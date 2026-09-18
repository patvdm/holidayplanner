package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Soft deletes that follow the hierarchy. Every statement stamps the rows it touches exactly like a
 * normal local write, so the deletions push on the next sync.
 */
@Dao
interface CascadeDao {

    @Query(
        """
        UPDATE countries SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE id = :countryId AND deleted = 0
        """,
    )
    suspend fun deleteCountry(countryId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE cities SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE countryId = :countryId AND deleted = 0
        """,
    )
    suspend fun deleteCitiesOfCountry(countryId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE cities SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE id = :cityId AND deleted = 0
        """,
    )
    suspend fun deleteCity(cityId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE spots SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE deleted = 0 AND parentCityId IN (SELECT id FROM cities WHERE countryId = :countryId)
        """,
    )
    suspend fun deleteSpotsOfCountry(countryId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE spots SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE parentCityId = :cityId AND deleted = 0
        """,
    )
    suspend fun deleteSpotsOfCity(cityId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE spots SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE id = :spotId AND deleted = 0
        """,
    )
    suspend fun deleteSpot(spotId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE links SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE deleted = 0 AND spotId IN (
            SELECT id FROM spots WHERE parentCityId IN (SELECT id FROM cities WHERE countryId = :countryId)
        )
        """,
    )
    suspend fun deleteLinksOfCountry(countryId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE links SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE deleted = 0 AND spotId IN (SELECT id FROM spots WHERE parentCityId = :cityId)
        """,
    )
    suspend fun deleteLinksOfCity(cityId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE links SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE spotId = :spotId AND deleted = 0
        """,
    )
    suspend fun deleteLinksOfSpot(spotId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE hearts SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE deleted = 0 AND spotId IN (
            SELECT id FROM spots WHERE parentCityId IN (SELECT id FROM cities WHERE countryId = :countryId)
        )
        """,
    )
    suspend fun deleteHeartsOfCountry(countryId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE hearts SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE deleted = 0 AND spotId IN (SELECT id FROM spots WHERE parentCityId = :cityId)
        """,
    )
    suspend fun deleteHeartsOfCity(cityId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE hearts SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE spotId = :spotId AND deleted = 0
        """,
    )
    suspend fun deleteHeartsOfSpot(spotId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE trips SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE id = :tripId AND deleted = 0
        """,
    )
    suspend fun deleteTrip(tripId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE trip_items SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE tripId = :tripId AND deleted = 0
        """,
    )
    suspend fun deleteItemsOfTrip(tripId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE route_stops SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE routeId = :routeId AND deleted = 0
        """,
    )
    suspend fun deleteStopsOfRoute(routeId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE routes SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE id = :routeId AND deleted = 0
        """,
    )
    suspend fun deleteRoute(routeId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE route_stops SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE deleted = 0 AND routeId IN (SELECT id FROM routes WHERE cityId = :cityId)
        """,
    )
    suspend fun deleteStopsOfCityRoutes(cityId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE routes SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE cityId = :cityId AND deleted = 0
        """,
    )
    suspend fun deleteRoutesOfCity(cityId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE route_stops SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE deleted = 0 AND routeId IN (
            SELECT id FROM routes WHERE cityId IN (SELECT id FROM cities WHERE countryId = :countryId)
        )
        """,
    )
    suspend fun deleteStopsOfCountryRoutes(countryId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE routes SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE deleted = 0 AND cityId IN (SELECT id FROM cities WHERE countryId = :countryId)
        """,
    )
    suspend fun deleteRoutesOfCountry(countryId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE route_stops SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE spotId = :spotId AND deleted = 0
        """,
    )
    suspend fun deleteStopsOfSpot(spotId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE route_stops SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE deleted = 0 AND spotId IN (SELECT id FROM spots WHERE parentCityId = :cityId)
        """,
    )
    suspend fun deleteStopsOfCity(cityId: String, now: Long, userId: String)

    @Query(
        """
        UPDATE route_stops SET deleted = 1, updatedAt = :now, updatedBy = :userId, dirty = 1
        WHERE deleted = 0 AND spotId IN (
            SELECT id FROM spots WHERE parentCityId IN (SELECT id FROM cities WHERE countryId = :countryId)
        )
        """,
    )
    suspend fun deleteStopsOfCountry(countryId: String, now: Long, userId: String)
}

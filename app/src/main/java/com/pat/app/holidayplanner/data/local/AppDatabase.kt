package com.pat.app.holidayplanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pat.app.holidayplanner.data.local.dao.CascadeDao
import com.pat.app.holidayplanner.data.local.dao.CountryDao
import com.pat.app.holidayplanner.data.local.dao.LinkDao
import com.pat.app.holidayplanner.data.local.dao.CityDao
import com.pat.app.holidayplanner.data.local.dao.RouteDao
import com.pat.app.holidayplanner.data.local.dao.RouteStopDao
import com.pat.app.holidayplanner.data.local.dao.SearchDao
import com.pat.app.holidayplanner.data.local.dao.SpotDao
import com.pat.app.holidayplanner.data.local.dao.SyncStateDao
import com.pat.app.holidayplanner.data.local.dao.TripDao
import com.pat.app.holidayplanner.data.local.dao.TripItemDao
import com.pat.app.holidayplanner.data.local.dao.UserDao

private const val DATABASE_NAME = "holiday_planner.db"

@Database(
    entities = [
        UserEntity::class,
        CountryEntity::class,
        CityEntity::class,
        SpotEntity::class,
        LinkEntity::class,
        TripEntity::class,
        TripItemEntity::class,
        RouteEntity::class,
        RouteStopEntity::class,
        SyncStateEntity::class,
    ],
    version = 16,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun countryDao(): CountryDao
    abstract fun cityDao(): CityDao
    abstract fun spotDao(): SpotDao
    abstract fun linkDao(): LinkDao
    abstract fun tripDao(): TripDao
    abstract fun tripItemDao(): TripItemDao
    abstract fun routeDao(): RouteDao
    abstract fun routeStopDao(): RouteStopDao
    abstract fun searchDao(): SearchDao
    abstract fun cascadeDao(): CascadeDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        /**
         * A schema change drops the database rather than migrating it. The sheet holds everything,
         * and `lastServerTime` lives in the database too, so the next sync pulls it all back.
         * Anything still dirty at that moment is lost, which is acceptable for this app.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

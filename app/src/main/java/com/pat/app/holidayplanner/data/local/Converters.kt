package com.pat.app.holidayplanner.data.local

import androidx.room.TypeConverter
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.domain.TravelMode
import com.pat.app.holidayplanner.domain.TripItemType

/** Lists are comma separated and enums are uppercase names, exactly as the sheet stores them. */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toStringList(value: String): List<String> =
        value.split(',').map(String::trim).filter(String::isNotEmpty)

    @TypeConverter
    fun fromPriority(value: Priority): String = value.name

    @TypeConverter
    fun toPriority(value: String): Priority = Priority.fromSheet(value)

    @TypeConverter
    fun fromSpotStatus(value: SpotStatus): String = value.name

    @TypeConverter
    fun toSpotStatus(value: String): SpotStatus = SpotStatus.fromSheet(value)

    @TypeConverter
    fun fromSpotType(value: SpotType?): String? = value?.name

    @TypeConverter
    fun toSpotType(value: String?): SpotType? = SpotType.fromSheet(value)

    @TypeConverter
    fun fromLinkSource(value: LinkSource): String = value.name

    @TypeConverter
    fun toLinkSource(value: String): LinkSource = LinkSource.fromSheet(value)

    @TypeConverter
    fun fromTripItemType(value: TripItemType): String = value.name

    @TypeConverter
    fun toTripItemType(value: String): TripItemType = TripItemType.fromSheet(value)

    @TypeConverter
    fun fromTravelMode(value: TravelMode?): String? = value?.name

    @TypeConverter
    fun toTravelMode(value: String?): TravelMode? = TravelMode.fromSheetOrNull(value)
}

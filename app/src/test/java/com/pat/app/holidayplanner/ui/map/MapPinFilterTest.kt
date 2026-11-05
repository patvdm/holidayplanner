package com.pat.app.holidayplanner.ui.map

import com.pat.app.holidayplanner.data.local.SpotPin
import com.pat.app.holidayplanner.domain.MapPinFilter
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import org.junit.Assert.assertEquals
import org.junit.Test

class MapPinFilterTest {

    private fun pin(type: SpotType = SpotType.POI, marks: String = "") = SpotPin(
        spotId = "s", name = "Spot", lat = 52.0, lng = 4.0, status = SpotStatus.UNVISITED,
        priority = Priority.NORMAL, type = type, marks = marks, openingHours = "",
        cityId = "c", cityName = "Amsterdam", countryId = "nl", countryName = "Netherlands",
    )

    @Test
    fun `food lets only restaurants through`() {
        assertEquals(true, MapPinFilter.FOOD.lets(pin(SpotType.RESTAURANT)))
        assertEquals(false, MapPinFilter.FOOD.lets(pin()))
    }

    @Test
    fun `drone goes by the mark, whatever kind of place carries it`() {
        assertEquals(true, MapPinFilter.DRONE.lets(pin(marks = "drone")))
        assertEquals(true, MapPinFilter.DRONE.lets(pin(SpotType.RESTAURANT, marks = "drone")))
        assertEquals(false, MapPinFilter.DRONE.lets(pin()))
    }

    @Test
    fun `no filter lets everything through`() {
        assertEquals(true, MapPinFilter.ALL.lets(pin()))
    }

    @Test
    fun `an unknown or missing setting means no filter`() {
        assertEquals(MapPinFilter.ALL, MapPinFilter.parse(null))
        assertEquals(MapPinFilter.ALL, MapPinFilter.parse("SOMETHING_NEW"))
        assertEquals(MapPinFilter.DRONE, MapPinFilter.parse("DRONE"))
    }
}

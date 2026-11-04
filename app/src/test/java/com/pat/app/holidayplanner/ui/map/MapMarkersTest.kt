package com.pat.app.holidayplanner.ui.map

import com.pat.app.holidayplanner.data.local.SpotPin
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import org.junit.Assert.assertEquals
import org.junit.Test

class MapMarkersTest {

    private fun pin(status: SpotStatus = SpotStatus.UNVISITED, priority: Priority = Priority.NORMAL) = SpotPin(
        spotId = "s", name = "Micropia", lat = 52.0, lng = 4.0, status = status, priority = priority,
        type = SpotType.POI, onRoute = false,
        cityId = "c", cityName = "Amsterdam", countryId = "nl", countryName = "Netherlands",
    )

    @Test
    fun `a short name is shown whole`() {
        assertEquals("Micropia", markerLabel("Micropia"))
        assertEquals("Twelve chars", markerLabel("Twelve chars"))
    }

    @Test
    fun `a long name is cut with an ellipsis and stays within the limit`() {
        val label = markerLabel("The Lion's Head Gastro Bar & Brewery")
        assertEquals("The Lion's…", label)
        assertEquals(true, label.length <= MARKER_LABEL_CHARS)
        assertEquals("Amsterdam C…", markerLabel("Amsterdam Centraal Station"))
    }

    @Test
    fun `a cut does not leave a space before the ellipsis`() {
        assertEquals("Duke of…", markerLabel("Duke of Tokyo Amsterdam", max = 9))
    }

    @Test
    fun `surrounding spaces are not counted`() {
        assertEquals("Cabron", markerLabel("  Cabron  "))
    }

    @Test
    fun `visited looks done whatever the priority`() {
        assertEquals(PinStyle.VISITED, pin(SpotStatus.VISITED, Priority.HIGH).pinStyle())
    }

    @Test
    fun `an unvisited high priority spot stands out`() {
        assertEquals(PinStyle.HIGH, pin(priority = Priority.HIGH).pinStyle())
        assertEquals(PinStyle.NORMAL, pin().pinStyle())
    }
}

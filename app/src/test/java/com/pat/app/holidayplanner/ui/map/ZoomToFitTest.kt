package com.pat.app.holidayplanner.ui.map

import com.pat.app.holidayplanner.domain.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomToFitTest {

    @Test
    fun `a walk across a city centre fits at street level`() {
        // Micropia to Tuschinski to Rosario: about three kilometres across Amsterdam.
        val zoom = listOf(GeoPoint(52.3670, 4.9128), GeoPoint(52.3665, 4.8946), GeoPoint(52.3768, 4.8822)).zoomToFit()
        assertTrue("was $zoom", zoom in 13f..15f)
    }

    @Test
    fun `one point, or points on top of each other, zoom right in`() {
        assertEquals(SPOT_ZOOM, listOf(GeoPoint(52.0, 4.0)).zoomToFit())
        assertEquals(SPOT_ZOOM, listOf(GeoPoint(52.0, 4.0), GeoPoint(52.0, 4.0)).zoomToFit())
    }

    @Test
    fun `points across a continent never zoom out past the world view`() {
        assertEquals(WORLD_ZOOM, listOf(GeoPoint(-40.0, -170.0), GeoPoint(60.0, 170.0)).zoomToFit())
    }
}

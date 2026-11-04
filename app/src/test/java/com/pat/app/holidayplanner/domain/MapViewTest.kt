package com.pat.app.holidayplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapViewTest {

    @Test
    fun `a stored view reads back the same`() {
        val view = MapView(52.3667, 4.8945, 14.5f)
        assertEquals(view, MapView.parse(view.format()))
    }

    @Test
    fun `anything else is no memory at all`() {
        assertNull(MapView.parse(null))
        assertNull(MapView.parse(""))
        assertNull(MapView.parse("52.3,4.8"))
        assertNull(MapView.parse("north,4.8,12"))
        assertNull(MapView.parse("95.0,4.8,12"))
        assertNull(MapView.parse("52.3,4.8,0"))
    }
}

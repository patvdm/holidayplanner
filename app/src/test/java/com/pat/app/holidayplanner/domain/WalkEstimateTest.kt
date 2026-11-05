package com.pat.app.holidayplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalkEstimateTest {

    @Test
    fun `fewer than two stops is no walk`() {
        assertNull(WalkEstimate.of(emptyList()))
        assertNull(WalkEstimate.of(listOf(GeoPoint(52.37, 4.89))))
    }

    @Test
    fun `a kilometre straight is about 1300 m through streets and 15 minutes`() {
        // 0.009 degrees of latitude is almost exactly a kilometre.
        val walk = WalkEstimate.of(listOf(GeoPoint(52.0, 4.9), GeoPoint(52.009, 4.9)))!!

        assertEquals(1300, walk.metres)
        assertEquals(15, walk.minutes)
    }

    @Test
    fun `legs add up in walking order`() {
        val there = GeoPoint(52.009, 4.9)
        val home = GeoPoint(52.0, 4.9)

        assertEquals(2600, WalkEstimate.of(listOf(home, there, home))!!.metres)
    }

    @Test
    fun `a tiny walk still shows one step`() {
        val walk = WalkEstimate.of(listOf(GeoPoint(52.0, 4.9), GeoPoint(52.0001, 4.9)))!!

        assertEquals(50, walk.metres)
        assertEquals(5, walk.minutes)
    }
}

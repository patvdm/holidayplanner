package com.pat.app.holidayplanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityTest {

    private val mauritshuis = GeoPoint(52.0802, 4.3146)
    private val binnenhof = GeoPoint(52.0799, 4.3131)
    private val peacePalace = GeoPoint(52.0866, 4.2955)
    private val scheveningen = GeoPoint(52.1073, 4.2745)
    private val lisbon = GeoPoint(38.7223, -9.1393)

    @Test
    fun `two points a short walk apart measure in the low hundreds of metres`() {
        val metres = Proximity.distanceMetres(mauritshuis, binnenhof)

        assertTrue("expected roughly 110m, got $metres", metres in 80.0..160.0)
    }

    @Test
    fun `Amsterdam to Lisbon is about eighteen hundred kilometres`() {
        val metres = Proximity.distanceMetres(GeoPoint(52.3676, 4.9041), lisbon)

        assertTrue("expected roughly 1860km, got ${metres / 1000}km", metres in 1_800_000.0..1_900_000.0)
    }

    @Test
    fun `the distance from a point to itself is nothing`() {
        assertEquals(0.0, Proximity.distanceMetres(mauritshuis, mauritshuis), 0.001)
    }

    @Test
    fun `distance is the same in both directions`() {
        assertEquals(
            Proximity.distanceMetres(mauritshuis, scheveningen),
            Proximity.distanceMetres(scheveningen, mauritshuis),
            0.001,
        )
    }

    @Test
    fun `a walk visits the nearest stop next rather than doubling back`() {
        val stops = listOf(
            "Mauritshuis" to mauritshuis,
            "Scheveningen" to scheveningen,
            "Binnenhof" to binnenhof,
            "Peace Palace" to peacePalace,
        )

        val ordered = Proximity.orderByProximity(stops) { it.second }

        assertEquals(
            listOf("Mauritshuis", "Binnenhof", "Peace Palace", "Scheveningen"),
            ordered.map { it.first },
        )
    }

    @Test
    fun `the first stop stays first, because it was put there on purpose`() {
        val stops = listOf(
            "Scheveningen" to scheveningen,
            "Mauritshuis" to mauritshuis,
            "Binnenhof" to binnenhof,
        )

        val ordered = Proximity.orderByProximity(stops) { it.second }

        assertEquals("Scheveningen", ordered.first().first)
    }

    @Test
    fun `starting from where you are reorders everything including the first`() {
        val stops = listOf(
            "Scheveningen" to scheveningen,
            "Mauritshuis" to mauritshuis,
            "Binnenhof" to binnenhof,
        )

        val ordered = Proximity.orderByProximity(stops, from = binnenhof) { it.second }

        assertEquals(listOf("Binnenhof", "Mauritshuis", "Scheveningen"), ordered.map { it.first })
    }

    @Test
    fun `stops with no coordinates keep their order and go last`() {
        val stops = listOf(
            "No location A" to null,
            "Scheveningen" to scheveningen,
            "No location B" to null,
            "Mauritshuis" to mauritshuis,
        )

        val ordered = Proximity.orderByProximity(stops) { it.second }

        assertEquals(
            listOf("Scheveningen", "Mauritshuis", "No location A", "No location B"),
            ordered.map { it.first },
        )
    }

    @Test
    fun `a list with no coordinates at all is left exactly as it was`() {
        val stops = listOf("A" to null, "B" to null)

        assertEquals(stops, Proximity.orderByProximity(stops) { it.second })
    }

    @Test
    fun `an empty list orders to nothing`() {
        val empty = emptyList<Pair<String, GeoPoint?>>()

        assertEquals(empty, Proximity.orderByProximity(empty) { it.second })
    }

    @Test
    fun `a single stop is already in order`() {
        val stops = listOf("Only" to mauritshuis)

        assertEquals(stops, Proximity.orderByProximity(stops) { it.second })
    }

    @Test
    fun `every stop appears exactly once however they were arranged`() {
        val stops = listOf(
            "a" to scheveningen,
            "b" to mauritshuis,
            "c" to null,
            "d" to peacePalace,
            "e" to binnenhof,
        )

        val ordered = Proximity.orderByProximity(stops) { it.second }

        assertEquals(stops.size, ordered.size)
        assertEquals(stops.map { it.first }.toSet(), ordered.map { it.first }.toSet())
    }
}

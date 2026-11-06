package com.pat.app.holidayplanner.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class TabNavStateTest {

    private fun nav() = TabNavState(
        stacks = AppTab.entries.associateWith { NavBackStack<NavKey>(it.rootKey) },
        tabOrder = mutableStateListOf(AppTab.EXPLORE),
    )

    @Test
    fun `leaving a trip's day map for the trips tab goes back to the trip`() {
        val nav = nav()
        nav.selectTab(AppTab.TRIPS)
        nav.push(TripKey("t1"))
        nav.push(MapKey(tripId = "t1", day = "2026-11-06"))

        nav.selectTab(AppTab.TRIPS)

        assertEquals(TripKey("t1"), nav.currentKey)
    }

    @Test
    fun `tapping the tab that is showing still goes to its root`() {
        val nav = nav()
        nav.selectTab(AppTab.TRIPS)
        nav.push(TripKey("t1"))

        nav.selectTab(AppTab.TRIPS)

        assertEquals(TripsRootKey, nav.currentKey)
    }

    @Test
    fun `a tab keeps its screens while another is used`() {
        val nav = nav()
        nav.selectTab(AppTab.TRIPS)
        nav.push(TripKey("t1"))
        nav.selectTab(AppTab.EXPLORE)
        nav.push(SpotKey("s1"))

        nav.selectTab(AppTab.TRIPS)

        assertEquals(TripKey("t1"), nav.currentKey)
    }
}

package com.pat.app.holidayplanner.ui.explore

import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.SpotHome
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import org.junit.Assert.assertEquals
import org.junit.Test

class SpotHomeChoicesTest {

    private val homes = listOf(
        home("be", "Belgium"),
        home("be", "Belgium", "gent", "Gent"),
        home("nl", "Netherlands"),
        home("nl", "Netherlands", "ams", "Amsterdam"),
        home("nl", "Netherlands", "dh", "The Hague"),
    )

    @Test
    fun `a spot in a city can go to its own country or any other city, this country first`() {
        val choices = spotHomeChoices(homes, spot(cityId = "ams", countryId = ""), hereFirst = "nl")

        assertEquals(listOf("nl/", "nl/dh", "be/", "be/gent"), choices.map { "${it.countryId}/${it.cityId}" })
    }

    @Test
    fun `a spot of a country can go to any of its cities but not to where it already is`() {
        val choices = spotHomeChoices(homes, spot(cityId = "", countryId = "nl"), hereFirst = "nl")

        assertEquals(listOf("nl/ams", "nl/dh", "be/", "be/gent"), choices.map { "${it.countryId}/${it.cityId}" })
    }

    private fun home(countryId: String, countryName: String, cityId: String = "", cityName: String = "") =
        SpotHome(countryId, countryName, isoCode = countryId.uppercase(), cityId = cityId, cityName = cityName)

    private fun spot(cityId: String, countryId: String) = SpotEntity(
        meta = SyncMeta.created("s1", at = 1, by = "u1"),
        parentCityId = cityId,
        parentCountryId = countryId,
        name = "Spot",
        type = SpotType.POI,
        googlePlaceId = "",
        lat = null,
        lng = null,
        address = "",
        mapsUrl = "",
        notes = "",
        status = SpotStatus.UNVISITED,
        sortKey = "V",
        priority = Priority.NORMAL,
        imageUrl = "",
    )
}

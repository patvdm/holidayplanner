package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.CityEntity
import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentOwnerTest {

    @Test
    fun `targets list the country, its own spots, then each city in its order followed by its spots in theirs`() {
        val targets = attachmentTargets(
            country = country("nl", "Netherlands"),
            cities = listOf(city("ams", "Amsterdam", sortKey = "V"), city("dh", "The Hague", sortKey = "G")),
            spots = listOf(
                spot("rijks", "Rijksmuseum", cityId = "ams", sortKey = "k"),
                spot("maur", "Mauritshuis", cityId = "dh", sortKey = "V"),
                spot("vondel", "Vondelpark", cityId = "ams", sortKey = "G"),
                spot("dunes", "Dunes", cityId = "", sortKey = "V"),
            ),
        )

        assertEquals(
            listOf(
                "Netherlands" to 0,
                "Dunes" to 1,
                "The Hague" to 1,
                "Mauritshuis" to 2,
                "Amsterdam" to 1,
                "Vondelpark" to 2,
                "Rijksmuseum" to 2,
            ),
            targets.map { it.name to it.depth },
        )
        assertEquals(AttachmentOwner(countryId = "nl"), targets[0].owner)
        assertEquals(AttachmentOwner(spotId = "dunes"), targets[1].owner)
        assertEquals(AttachmentOwner(cityId = "dh"), targets[2].owner)
        assertEquals(AttachmentOwner(spotId = "maur"), targets[3].owner)
    }

    @Test
    fun `attachments are sorted by what they hang on`() {
        val grouped = CountryAttachments.of(
            listOf(
                link("a", countryId = "nl"),
                link("b", cityId = "ams"),
                link("c", spotId = "rijks"),
                link("d", cityId = "ams"),
            ),
        )

        assertEquals(listOf("a"), grouped.country.map { it.meta.id })
        assertEquals(listOf("b", "d"), grouped.byCity["ams"]?.map { it.meta.id })
        assertEquals(listOf("c"), grouped.bySpot["rijks"]?.map { it.meta.id })
    }

    @Test
    fun `a link naming a spot belongs to the spot whatever else it names`() {
        val grouped = CountryAttachments.of(listOf(link("a", spotId = "rijks", cityId = "ams", countryId = "nl")))

        assertEquals(emptyList<LinkEntity>(), grouped.country)
        assertEquals(emptyMap<String, List<LinkEntity>>(), grouped.byCity)
        assertEquals(listOf("a"), grouped.bySpot["rijks"]?.map { it.meta.id })
    }

    @Test
    fun `a photo is filed under whatever it is of`() {
        assertEquals("rijks", AttachmentOwner(spotId = "rijks").fileStem)
        assertEquals("ams", AttachmentOwner(cityId = "ams").fileStem)
        assertEquals("nl", AttachmentOwner(countryId = "nl").fileStem)
    }

    private fun meta(id: String) = SyncMeta.created(id, at = 1, by = "u1")

    private fun country(id: String, name: String) = CountryEntity(
        meta = meta(id),
        name = name,
        isoCode = "NL",
        sortKey = "V",
        priority = Priority.NORMAL,
        imageUrl = "",
    )

    private fun city(id: String, name: String, sortKey: String) = CityEntity(
        meta = meta(id),
        countryId = "nl",
        name = name,
        googlePlaceId = "",
        lat = null,
        lng = null,
        sortKey = sortKey,
        priority = Priority.NORMAL,
        imageUrl = "",
    )

    private fun spot(id: String, name: String, cityId: String, sortKey: String) = SpotEntity(
        meta = meta(id),
        parentCityId = cityId,
        parentCountryId = if (cityId.isEmpty()) "nl" else "",
        name = name,
        type = SpotType.POI,
        googlePlaceId = "",
        lat = null,
        lng = null,
        address = "",
        mapsUrl = "",
        notes = "",
        status = SpotStatus.UNVISITED,
        sortKey = sortKey,
        priority = Priority.NORMAL,
        imageUrl = "",
    )

    private fun link(id: String, spotId: String = "", cityId: String = "", countryId: String = "") = LinkEntity(
        meta = meta(id),
        spotId = spotId,
        cityId = cityId,
        countryId = countryId,
        url = "https://example.com/$id",
        source = LinkSource.WEB,
        note = "",
    )
}

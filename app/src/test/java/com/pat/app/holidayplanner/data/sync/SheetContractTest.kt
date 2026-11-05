package com.pat.app.holidayplanner.data.sync

import com.pat.app.holidayplanner.data.local.CityEntity
import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.RouteEntity
import com.pat.app.holidayplanner.data.local.RouteStopEntity
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.local.UserEntity
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortKey
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.domain.TripItemType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * What the app, the sheet and the claude.ai connector must agree on, from the app's side. The
 * connector checks the same tools/mcp/contract.json from its side (contract.test.mjs).
 *
 * Unit tests run from the app module's folder, hence the paths up one level.
 */
class SheetContractTest {

    private val contract = Json.parseToJsonElement(File("../tools/mcp/contract.json").readText()).jsonObject

    /** The sheet's columns per tab, read out of backend/Code.gs. */
    private val schema: Map<String, List<String>> by lazy {
        val source = File("../backend/Code.gs").readText()
        fun section(pattern: String) = Regex(pattern, RegexOption.DOT_MATCHES_ALL).find(source)!!.groupValues[1]
        val common = quoted(section("""var COMMON_COLUMNS = \[(.*?)];"""))
        val block = section("""var SCHEMA = \{(.*?)\n};""")
        Regex("""(\w+): COMMON_COLUMNS\.concat\(\[(.*?)]\)""", RegexOption.DOT_MATCHES_ALL).findAll(block)
            .associate { it.groupValues[1] to common + quoted(it.groupValues[2]) }
    }

    private fun quoted(list: String): List<String> =
        Regex("""'([^']*)'""").findAll(list).map { it.groupValues[1] }.toList()

    private val meta = SyncMeta.created("id", 1, "u")

    /** One row of every table, as the app writes it. */
    private val written: Map<String, Map<String, String>> = mapOf(
        "Users" to userToRow(UserEntity(meta, "Pat")),
        "Countries" to countryToRow(CountryEntity(meta, "Netherlands", "NL", "V", Priority.NORMAL, "")),
        "Cities" to cityToRow(CityEntity(meta, "nl", "Amsterdam", "", null, null, "V", Priority.NORMAL, "", "")),
        "Spots" to spotToRow(
            SpotEntity(
                meta, "ams", "", "Micropia", SpotType.POI, "", null, null, "", "", "", SpotStatus.UNVISITED, "",
                "V", Priority.NORMAL, "",
            ),
        ),
        "Links" to linkToRow(LinkEntity(meta, "s", "", "", "https://x", LinkSource.WEB, "")),
        "Trips" to tripToRow(TripEntity(meta, "Amsterdam", "nl", "2026-11-06", "2026-11-08", "")),
        "TripItems" to tripItemToRow(
            TripItemEntity(meta, "t", TripItemType.SPOT, "", "", "", "", "", "", "", "s", "", "", ""),
        ),
        "Routes" to routeToRow(RouteEntity(meta, "ams", "Saturday", false, "")),
        "RouteStops" to routeStopToRow(RouteStopEntity(meta, "r", "s", "V", onMaps = true)),
    )

    @Test
    fun `the app writes exactly the columns the sheet has`() {
        assertEquals(schema.keys, written.keys)
        written.forEach { (table, row) ->
            // syncedAt is the server's to write; the app never sends it.
            assertEquals("columns of $table", schema.getValue(table).toSet() - "syncedAt", row.keys)
        }
    }

    @Test
    fun `the values the connector offers are the app's`() {
        val enums = contract.getValue("enums").jsonObject
        fun listed(name: String) = enums.getValue(name).jsonArray.map { it.jsonPrimitive.content }

        assertEquals(SpotType.entries.map { it.name }, listed("SpotType"))
        assertEquals(Priority.entries.map { it.name }, listed("Priority"))
        assertEquals(SpotStatus.entries.map { it.name }, listed("SpotStatus"))
        assertEquals(TripItemType.entries.map { it.name }, listed("TripItemType"))
    }

    @Test
    fun `the connector's sort keys are the app's`() {
        contract.getValue("sortKeyAfter").jsonArray.forEach { case ->
            val (last, next) = case.jsonArray.map { it.jsonPrimitive.content }
            assertEquals("after \"$last\"", next, SortKey.after(last.ifEmpty { null }))
        }
    }
}

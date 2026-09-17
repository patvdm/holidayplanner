package com.pat.app.holidayplanner.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merge rule, table by table:
 *
 * - no local row            -> take the remote one
 * - local dirty and newer   -> keep the local one, it pushes next run
 * - anything else           -> take the remote one and clear dirty
 */
class SyncMergeTest {

    private val dao = fakeUserDao()
    private val table = SyncTable("Users", dao, ::userToRow, ::userFromRow)

    @Test
    fun `an unknown row is inserted and is not dirty`() = runTest {
        val taken = table.merge(listOf(remoteRow(id = "u1", name = "Pat", updatedAt = 100)))

        assertEquals(1, taken)
        assertEquals("Pat", dao.rows.getValue("u1").name)
        assertFalse(dao.rows.getValue("u1").meta.dirty)
    }

    @Test
    fun `a clean local row is replaced by the remote one`() = runTest {
        dao.rows["u1"] = user(id = "u1", name = "Old", updatedAt = 100, dirty = false)

        table.merge(listOf(remoteRow(id = "u1", name = "New", updatedAt = 50)))

        assertEquals("New", dao.rows.getValue("u1").name)
    }

    @Test
    fun `a dirty local row that is newer wins and stays dirty`() = runTest {
        dao.rows["u1"] = user(id = "u1", name = "Mine", updatedAt = 200, dirty = true)

        val taken = table.merge(listOf(remoteRow(id = "u1", name = "Theirs", updatedAt = 100)))

        assertEquals(0, taken)
        assertEquals("Mine", dao.rows.getValue("u1").name)
        assertTrue(dao.rows.getValue("u1").meta.dirty)
    }

    @Test
    fun `a dirty local row that is older loses to the remote one`() = runTest {
        dao.rows["u1"] = user(id = "u1", name = "Mine", updatedAt = 100, dirty = true)

        table.merge(listOf(remoteRow(id = "u1", name = "Theirs", updatedAt = 200)))

        assertEquals("Theirs", dao.rows.getValue("u1").name)
        assertFalse(dao.rows.getValue("u1").meta.dirty)
    }

    @Test
    fun `equal timestamps let the remote row win, so both devices agree`() = runTest {
        dao.rows["u1"] = user(id = "u1", name = "Mine", updatedAt = 100, dirty = true)

        table.merge(listOf(remoteRow(id = "u1", name = "Theirs", updatedAt = 100)))

        assertEquals("Theirs", dao.rows.getValue("u1").name)
    }

    @Test
    fun `applying the same pull twice changes nothing`() = runTest {
        val rows = listOf(remoteRow(id = "u1", name = "Pat", updatedAt = 100))

        table.merge(rows)
        table.merge(rows)

        assertEquals(1, dao.rows.size)
        assertEquals("Pat", dao.rows.getValue("u1").name)
    }

    @Test
    fun `a row without an id is skipped instead of failing the pull`() = runTest {
        val taken = table.merge(
            listOf(
                mapOf("id" to "", "name" to "Ghost", "updatedAt" to "100"),
                remoteRow(id = "u1", name = "Pat", updatedAt = 100),
            ),
        )

        assertEquals(1, taken)
        assertEquals(setOf("u1"), dao.rows.keys)
    }

    @Test
    fun `a deleted remote row replaces the local one`() = runTest {
        dao.rows["u1"] = user(id = "u1", name = "Pat", updatedAt = 100, dirty = false)

        table.merge(listOf(remoteRow(id = "u1", name = "Pat", updatedAt = 200, deleted = true)))

        assertTrue(dao.rows.getValue("u1").meta.deleted)
    }

    private fun remoteRow(id: String, name: String, updatedAt: Long, deleted: Boolean = false) =
        userToRow(user(id = id, name = name, updatedAt = updatedAt, dirty = false, deleted = deleted)) +
            mapOf("syncedAt" to updatedAt.toString())
}

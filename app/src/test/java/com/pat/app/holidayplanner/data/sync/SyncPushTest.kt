package com.pat.app.holidayplanner.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pushing clears the dirty flag, but only for rows that have not changed since they were handed to
 * the server. An edit made while the request was in flight has to survive and go out again.
 */
class SyncPushTest {

    private val dao = fakeUserDao()
    private val table = SyncTable("Users", dao, ::userToRow, ::userFromRow)

    @Test
    fun `pushed rows carry their id and updated time`() = runTest {
        dao.rows["u1"] = user(id = "u1", name = "Pat", updatedAt = 100, dirty = true)
        dao.rows["u2"] = user(id = "u2", name = "Clean", updatedAt = 100, dirty = false)

        val pending = table.pendingRows(limit = 10)

        assertEquals(1, pending.size)
        assertEquals("u1", pending.first().id)
        assertEquals(100L, pending.first().updatedAt)
        assertEquals("Pat", pending.first().values["name"])
    }

    @Test
    fun `pushed rows never carry the local only columns`() = runTest {
        dao.rows["u1"] = user(id = "u1", name = "Pat", updatedAt = 100, dirty = true, syncedAt = 55)

        val values = table.pendingRows(limit = 10).first().values

        assertFalse("syncedAt belongs to the server", values.containsKey("syncedAt"))
        assertFalse("dirty never leaves the device", values.containsKey("dirty"))
    }

    @Test
    fun `an unchanged row loses its dirty flag after the push`() = runTest {
        dao.rows["u1"] = user(id = "u1", name = "Pat", updatedAt = 100, dirty = true)
        val pushed = table.pendingRows(limit = 10)

        table.clearDirty(pushed)

        assertFalse(dao.rows.getValue("u1").meta.dirty)
    }

    @Test
    fun `a row edited while the push was in flight stays dirty`() = runTest {
        dao.rows["u1"] = user(id = "u1", name = "Pat", updatedAt = 100, dirty = true)
        val pushed = table.pendingRows(limit = 10)

        // The user renames themselves while the request is on the wire.
        dao.rows["u1"] = user(id = "u1", name = "Patrick", updatedAt = 150, dirty = true)

        table.clearDirty(pushed)

        val row = dao.rows.getValue("u1")
        assertTrue("the newer edit must still be pushed", row.meta.dirty)
        assertEquals("Patrick", row.name)
        assertEquals(1, table.pendingRows(limit = 10).size)
    }

    @Test
    fun `the limit caps how many rows go out in one run`() = runTest {
        repeat(5) { index ->
            dao.rows["u$index"] = user(id = "u$index", name = "User $index", updatedAt = 100, dirty = true)
        }

        assertEquals(3, table.pendingRows(limit = 3).size)
    }
}

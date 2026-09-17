package com.pat.app.holidayplanner.data.sync

import com.pat.app.holidayplanner.data.local.SyncStateEntity
import com.pat.app.holidayplanner.data.local.dao.SyncStateDao
import com.pat.app.holidayplanner.domain.AppClock
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One sync run end to end, with the network and the database faked. */
class SyncRepositoryTest {

    private val dao = fakeUserDao()
    private val table = SyncTable("Users", dao, ::userToRow, ::userFromRow)
    private val syncState = FakeSyncStateDao()
    private val outcome = FakeOutcomeStore()
    private val backend = FakeSheetsBackend(serverTime = 5_000)

    private fun repository() = SyncRepository(
        backend = backend,
        tables = SyncTables(listOf(table)),
        transactor = DirectTransactor,
        syncStateDao = syncState,
        outcomeStore = outcome,
        clock = AppClock { 9_999 },
    )

    @Test
    fun `a clean database pushes nothing and still pulls`() = runTest {
        val result = repository().sync()

        assertTrue(result.isSuccess)
        assertTrue(backend.pushedBatches.isEmpty())
        assertEquals(0L, backend.pullSince)
    }

    @Test
    fun `the first pull asks for everything`() = runTest {
        repository().sync()

        assertEquals(0L, backend.pullSince)
    }

    @Test
    fun `a later pull asks only for what the server stamped since last time`() = runTest {
        syncState.lastServerTime = 1_234

        repository().sync()

        assertEquals(1_234L, backend.pullSince)
    }

    @Test
    fun `the new server time is stored after a successful pull`() = runTest {
        repository().sync()

        assertEquals(5_000L, syncState.lastServerTime)
    }

    @Test
    fun `a failed pull leaves the server time alone so the window is asked for again`() = runTest {
        syncState.lastServerTime = 1_234
        backend.failPull = IOException("offline")

        val result = repository().sync()

        assertTrue(result.isFailure)
        assertEquals(1_234L, syncState.lastServerTime)
        assertEquals("offline", outcome.lastError)
    }

    @Test
    fun `a failed push stops the run before pulling`() = runTest {
        dao.rows["u1"] = user(id = "u1", name = "Pat", updatedAt = 100, dirty = true)
        backend.failPush = IOException("offline")

        val result = repository().sync()

        assertTrue(result.isFailure)
        assertNull("pull must not run after a failed push", backend.pullSince)
        assertTrue("the row stays dirty", dao.rows.getValue("u1").meta.dirty)
    }

    @Test
    fun `dirty rows are pushed and then cleared`() = runTest {
        dao.rows["u1"] = user(id = "u1", name = "Pat", updatedAt = 100, dirty = true)

        val result = repository().sync()

        assertTrue(result.isSuccess)
        assertEquals(1, backend.pushedBatches.size)
        assertEquals("Pat", backend.pushedBatches.first().getValue("Users").first()["name"])
        assertTrue(!dao.rows.getValue("u1").meta.dirty)
        assertEquals(9_999L, outcome.lastSuccessAt)
    }

    @Test
    fun `an edit made during the push is still pending afterwards`() = runTest {
        dao.rows["u1"] = user(id = "u1", name = "Pat", updatedAt = 100, dirty = true)
        backend.duringPush = {
            dao.rows["u1"] = user(id = "u1", name = "Patrick", updatedAt = 150, dirty = true)
        }

        repository().sync()

        val row = dao.rows.getValue("u1")
        assertTrue(row.meta.dirty)
        assertEquals("Patrick", row.name)
    }

    @Test
    fun `more than three hundred rows go out in separate requests`() = runTest {
        repeat(301) { index ->
            dao.rows["u$index"] = user(id = "u$index", name = "User $index", updatedAt = 100, dirty = true)
        }

        repository().sync()

        assertEquals(2, backend.pushedBatches.size)
        assertEquals(300, backend.pushedBatches[0].getValue("Users").size)
        assertEquals(1, backend.pushedBatches[1].getValue("Users").size)
    }

    @Test
    fun `pulled rows are applied to the right table`() = runTest {
        backend.setPullChanges(
            mapOf(
                "Users" to listOf(userToRow(user(id = "u9", name = "Remote", updatedAt = 100, dirty = false))),
                "Unknown" to listOf(mapOf("id" to "x")),
            ),
        )

        val result = repository().sync()

        assertTrue(result.isSuccess)
        assertEquals("Remote", dao.rows.getValue("u9").name)
    }
}

private object DirectTransactor : Transactor {
    override suspend fun <R> transaction(block: suspend () -> R): R = block()
}

private class FakeSyncStateDao : SyncStateDao {
    var lastServerTime: Long? = null

    override suspend fun lastServerTime(): Long? = lastServerTime

    override suspend fun upsert(state: SyncStateEntity) {
        lastServerTime = state.lastServerTime
    }

    override fun observePendingCount(): Flow<Int> = flowOf(0)
}

private class FakeOutcomeStore : SyncOutcomeStore {
    var lastSuccessAt: Long? = null
    var lastError: String? = null

    override suspend fun recordSyncSuccess(at: Long) {
        lastSuccessAt = at
        lastError = null
    }

    override suspend fun recordSyncError(message: String) {
        lastError = message
    }
}

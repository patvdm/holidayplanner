package com.pat.app.holidayplanner.data.sync

import androidx.room.withTransaction
import com.pat.app.holidayplanner.data.local.AppDatabase

/** Runs a block in one database transaction. An interface so sync can be tested without Room. */
interface Transactor {
    suspend fun <R> transaction(block: suspend () -> R): R
}

class RoomTransactor(private val db: AppDatabase) : Transactor {
    override suspend fun <R> transaction(block: suspend () -> R): R = db.withTransaction(block)
}

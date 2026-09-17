package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pat.app.holidayplanner.data.local.SyncDao
import com.pat.app.holidayplanner.data.local.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao : SyncDao<UserEntity> {

    @Query("SELECT * FROM users WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeById(id: String): Flow<UserEntity?>

    /** Used to tell "everyone hearted this" from "some people did". */
    @Query("SELECT COUNT(*) FROM users WHERE deleted = 0")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM users WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    override suspend fun dirty(limit: Int): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :id")
    override suspend fun find(id: String): UserEntity?

    @Upsert
    override suspend fun upsert(entities: List<UserEntity>)

    @Query("UPDATE users SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    override suspend fun clearDirty(id: String, updatedAt: Long)
}

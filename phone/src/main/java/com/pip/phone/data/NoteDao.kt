package com.pip.phone.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE status = :status ORDER BY createdAt ASC")
    suspend fun byStatus(status: NoteStatus): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE status = :status LIMIT 1")
    suspend fun nextPending(status: NoteStatus): NoteEntity?

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Query("DELETE FROM notes WHERE id NOT IN (SELECT id FROM notes ORDER BY createdAt DESC LIMIT :limit)")
    suspend fun trimTo(limit: Int)
}
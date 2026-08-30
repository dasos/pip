package com.pip.phone.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val status: NoteStatus = NoteStatus.PENDING,
    val audioPath: String? = null,
)

enum class NoteStatus {
    PENDING,
    UPLOADED,
    FAILED,
}
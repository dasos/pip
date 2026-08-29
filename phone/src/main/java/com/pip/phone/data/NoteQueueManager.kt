package com.pip.phone.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Phone-side queue policies: at most [MAX_ITEMS] notes retained and audio older
 * than [RETENTION_DAYS] is evicted. Oldest items are deleted first.
 */
class NoteQueueManager(private val context: Context) {

    private val audioDir: File
        get() = File(context.filesDir, "received_audio").apply { mkdirs() }

    fun audioDir(): File = audioDir

    /** New file for an incoming watch asset. */
    fun newAudioFile(): File = File(audioDir, "audio_${System.currentTimeMillis()}.wav")

    suspend fun enforcePolicies(dao: NoteDao) = withContext(Dispatchers.IO) {
        // Trim to capacity (newest retained).
        dao.trimTo(MAX_ITEMS)
        // Evict audio files older than retention.
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * DAY_MS
        audioDir.listFiles().orEmpty()
            .filter { it.lastModified() < cutoff }
            .forEach { it.delete() }
        // Drop notes whose audio is gone and never transcribed (abandoned).
        dao.byStatus(NoteStatus.PENDING).forEach { note ->
            val path = note.audioPath
            if (path != null && !File(path).exists()) {
                dao.delete(note.id)
            }
        }
    }

    companion object {
        const val MAX_ITEMS = 20
        const val RETENTION_DAYS = 7L
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
package com.pip.phone.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Phone-side queue policies: at most [MAX_ITEMS] notes retained and uploaded audio
 * older than [RETENTION_DAYS] is evicted. Pending items are preserved.
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
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * DAY_MS
        audioDir.listFiles().orEmpty()
            .filter { it.lastModified() < cutoff }
            .forEach { file ->
                if (dao.byStatus(NoteStatus.UPLOADED).any { it.audioPath == file.absolutePath }) {
                    file.delete()
                }
            }
        dao.byStatus(NoteStatus.UPLOADED).forEach { note ->
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
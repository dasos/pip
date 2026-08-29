package com.pip.wear.audio

import android.content.Context
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Manages the local watch-side queue of undelivered audio recordings.
 *
 * Policies:
 *  - At most [MAX_QUEUE_SIZE] files; when full the oldest file is deleted.
 *  - Files older than [RETENTION_DAYS] are evicted automatically.
 */
class AudioQueueManager(private val context: Context) {

    private val queueDir: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun queueDir(): File = queueDir

    /** Creates a new recording file ready for a [android.media.MediaRecorder]. */
    fun newRecordingFile(): File = File(queueDir, "rec_${UUID.randomUUID()}.wav")

    /**
     * Persistently enqueues [file] under its capture timestamp. Applies retention
     * and capacity eviction, then returns the queue entry.
     */
    fun enqueue(file: File, capturedAt: Instant): QueuedRecording {
        val target = stampFile(file, capturedAt)
        evict()
        return QueuedRecording(target, capturedAt)
    }

    fun list(): List<QueuedRecording> {
        evict()
        return queueDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(EXT) }
            .sortedBy { it.lastModified() }
            .map { QueuedRecording(it, parseTimestamp(it)) }
    }

    /** Deletes any queued file whose name embeds one of [ids]. */
    fun clearSent(ids: List<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toHashSet()
        queueDir.listFiles().orEmpty().forEach { f ->
            if (idSet.any { f.name.contains(it) }) f.delete()
        }
    }

    fun remove(recordingId: String) {
        queueDir.listFiles().orEmpty().forEach { file ->
            if (file.name.contains(recordingId)) file.delete()
        }
    }

    private fun stampFile(file: File, capturedAt: Instant): File {
        val timestamp = capturedAt.toEpochMilli()
        val suffix = file.nameWithoutExtension.substringAfter("rec_", "")
        val target = File(queueDir, "rec_${timestamp}_$suffix$EXT")
        if (file.absolutePath != target.absolutePath) {
            if (file.exists()) {
                file.renameTo(target) || (file.copyTo(target, overwrite = true).let { file.delete(); true })
            }
        }
        return target
    }

    /** Evicts expired files and excess files beyond capacity (oldest first). */
    private fun evict() {
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * DAY_MS
        val files = queueDir.listFiles().orEmpty().sortedBy { it.lastModified() }
        files.filter { it.lastModified() < cutoff }.forEach { it.delete() }
        val before = queueDir.listFiles().orEmpty().sortedBy { it.lastModified() }
        val toRemove = before.size - MAX_QUEUE_SIZE
        if (toRemove > 0) before.take(toRemove).forEach { it.delete() }
    }

    private fun parseTimestamp(file: File): Instant {
        val name = file.nameWithoutExtension
        val token = name.substringAfter("rec_", "").substringBefore("_")
        return token.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: Instant.ofEpochMilli(file.lastModified())
    }

    data class QueuedRecording(
        val file: File,
        val capturedAt: Instant,
    ) {
        val id: String
            get() = "rec_${capturedAt.toEpochMilli()}_${file.nameWithoutExtension.substringAfterLast('_')}"
    }

    companion object {
        private const val DIRECTORY = "RecordedNotes"
        private const val EXT = ".wav"
        const val MAX_QUEUE_SIZE = 20
        const val RETENTION_DAYS = 7L
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
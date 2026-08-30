package com.pip.phone.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pip.phone.data.NoteQueueManager
import com.pip.phone.data.NoteStatus
import com.pip.phone.data.PipDatabase
import com.pip.phone.upload.AudioUploader
import java.io.File

/**
 * Uploads pending recordings (WAV assets received from the watch) to the
 * server. One-shot runs are enqueued when new audio arrives; a periodic
 * variant catches items that were offline earlier.
 */
class AudioUploadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = PipDatabase.get(applicationContext).noteDao()
        NoteQueueManager(applicationContext).enforcePolicies(dao)

        val uploader = AudioUploader(applicationContext)
        var allSucceeded = true

        for (note in dao.byStatus(NoteStatus.PENDING)) {
            val file = note.audioPath?.let { File(it) } ?: continue
            if (!file.exists()) {
                // Audio file was evicted; nothing left to upload.
                dao.delete(note.id)
                continue
            }
            when (uploader.upload(file, note.createdAt)) {
                AudioUploader.UploadOutcome.SUCCESS -> {
                    dao.update(note.copy(status = NoteStatus.UPLOADED))
                }
                AudioUploader.UploadOutcome.AUTH_FAILED -> {
                    // Token revoked: do not burn retries forever.
                    dao.update(note.copy(status = NoteStatus.FAILED))
                }
                else -> allSucceeded = false
            }
        }
        return if (allSucceeded) Result.success() else Result.retry()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<AudioUploadWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
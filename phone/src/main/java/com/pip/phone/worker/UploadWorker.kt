package com.pip.phone.worker

import android.app.Application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pip.phone.data.NoteStatus
import com.pip.phone.data.PipDatabase
import com.pip.phone.upload.NoteUploader

/**
 * Uploads transcribed notes to the server, retrying with backoff on failure.
 */
class UploadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = PipDatabase.get(applicationContext).noteDao()
        val uploader = NoteUploader(applicationContext)

        val ready = dao.byStatus(NoteStatus.TRANSCRIBED)
        if (ready.isEmpty()) return Result.success()

        var allSucceeded = true
        for (note in ready) {
            when (uploader.upload(note.text, note.createdAt)) {
                NoteUploader.UploadOutcome.SUCCESS -> {
                    dao.update(note.copy(status = NoteStatus.UPLOADED))
                }
                NoteUploader.UploadOutcome.AUTH_FAILED -> {
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
            val request = OneTimeWorkRequestBuilder<UploadWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
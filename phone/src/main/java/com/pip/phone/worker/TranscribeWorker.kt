package com.pip.phone.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pip.phone.data.NoteQueueManager
import com.pip.phone.data.NoteStatus
import com.pip.phone.data.PipDatabase
import com.pip.phone.recognition.Transcriber
import java.io.File

/**
 * Retries transcription of notes whose audio is still pending, using ML Kit's
 * file-based on-device transcriber.
 */
class TranscribeWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = PipDatabase.get(applicationContext).noteDao()
        NoteQueueManager(applicationContext).enforcePolicies(dao)

        val pending = dao.byStatus(NoteStatus.PENDING)
        if (pending.isEmpty()) return Result.success()

        var anyFailed = false
        for (note in pending) {
            val path = note.audioPath ?: continue
            val text = Transcriber.transcribe(File(path))
            if (text != null) {
                dao.update(note.copy(text = text, status = NoteStatus.TRANSCRIBED))
            } else {
                anyFailed = true
            }
        }
        UploadWorker.enqueue(applicationContext)
        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TranscribeWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
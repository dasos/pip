package com.pip.wear.worker

import android.app.Application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pip.wear.audio.AudioQueueManager
import com.pip.wear.data.WearSendClient

/**
 * Periodically attempts to deliver queued audio recordings to the paired phone.
 */
class SendWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as Application
        val queue = AudioQueueManager(app)
        if (queue.list().isEmpty()) return Result.success()

        val client = WearSendClient(app)
        if (!client.phoneReachable()) {
            return Result.retry() // Bluetooth not connected; try again next period.
        }

        return try {
            val result = client.pushQueued(queue)
            when (result) {
                WearSendClient.SendResult.SENT -> Result.success()
                else -> Result.retry()
            }
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SendWorker"
    }
}
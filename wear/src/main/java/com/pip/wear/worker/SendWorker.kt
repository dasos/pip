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
        val items = queue.list()
        android.util.Log.d(TAG, "SendWorker doWork: ${items.size} queued item(s)")
        if (items.isEmpty()) return Result.success()

        val client = WearSendClient(app)
        if (!client.phoneReachable()) {
            android.util.Log.d(TAG, "SendWorker: Phone unreachable, will retry")
            return Result.retry() // Bluetooth not connected; try again next period.
        }

        return try {
            val result = client.pushQueued(queue)
            android.util.Log.i(TAG, "SendWorker: pushQueued completed with result $result")
            when (result) {
                WearSendClient.SendResult.SENT -> Result.success()
                else -> Result.retry()
            }
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "SendWorker failed", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SendWorker"
    }
}
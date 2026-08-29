package com.pip.wear.data

import android.app.Application
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.pip.wear.audio.AudioQueueManager
import kotlinx.coroutines.tasks.await

/**
 * Sends recorded audio to the paired phone as an [Asset] over the Wear OS Data Layer.
 */
class WearSendClient(private val app: Application) {

    private val dataClient: DataClient = Wearable.getDataClient(app)
    private val nodeClient = Wearable.getNodeClient(app)

    /** Attempts to push all currently queued recordings to the phone. */
    suspend fun pushQueued(queue: AudioQueueManager): SendResult {
        if (nodeClient.connectedNodes.await().isEmpty()) return SendResult.PHONE_UNREACHABLE

        var sent = 0
        var queued = 0
        for (recording in queue.list()) {
            when (push(recording)) {
                SendResult.SENT -> sent++
                else -> queued++
            }
        }
        return if (sent > 0) SendResult.SENT else if (queued > 0) SendResult.QUEUED else SendResult.SENT
    }

    /** Immediately pushes a single freshly recorded [recording]. */
    suspend fun push(recording: AudioQueueManager.QueuedRecording): SendResult = try {
        sendItem(recording.file, recording.id, recording.capturedAt.toEpochMilli())
        SendResult.SENT
    } catch (t: Throwable) {
        Log.w(TAG, "push failed for ${recording.id}", t)
        SendResult.QUEUED
    }

    private suspend fun sendItem(file: java.io.File, id: String, timestamp: Long) {
        val asset = Asset.createFromUri(Uri.fromFile(file))
        val putReq = PutDataMapRequest.create(requestPath(id)).apply {
            dataMap.putAsset(WearPaths.KEY_AUDIO, asset)
            dataMap.putLong(WearPaths.KEY_TIMESTAMP, timestamp)
            dataMap.putString(WearPaths.KEY_ID, id)
        }
        dataClient.putDataItem(putReq.asPutDataRequest()).await()
    }

    /** Returns true if a phone node is currently connected (Bluetooth reachable). */
    suspend fun phoneReachable(): Boolean = nodeClient.connectedNodes.await().isNotEmpty()

    private fun requestPath(id: String) = "${WearPaths.AUDIO_PATH}/$id"

    sealed class SendResult {
        data object SENT : SendResult()
        data object QUEUED : SendResult()
        data object PHONE_UNREACHABLE : SendResult()
    }

    companion object {
        private const val TAG = "WearSendClient"
    }
}
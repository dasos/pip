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
        val nodes = nodeClient.connectedNodes.await()
        Log.d(TAG, "pushQueued: connectedNodes count = ${nodes.size}, nodes = ${nodes.map { "${it.displayName}(${it.id})" }}")
        if (nodes.isEmpty()) {
            Log.w(TAG, "pushQueued: No connected nodes found, phone is unreachable")
            return SendResult.PHONE_UNREACHABLE
        }

        val items = queue.list()
        Log.d(TAG, "pushQueued: Found ${items.size} queued item(s) to send")
        var sent = 0
        var queued = 0
        for (recording in items) {
            when (push(recording)) {
                SendResult.SENT -> sent++
                else -> queued++
            }
        }
        Log.d(TAG, "pushQueued: result sent=$sent, queued=$queued")
        return if (sent > 0) SendResult.SENT else if (queued > 0) SendResult.QUEUED else SendResult.SENT
    }

    /** Immediately pushes a single freshly recorded [recording]. */
    suspend fun push(recording: AudioQueueManager.QueuedRecording): SendResult = try {
        Log.d(TAG, "push: Starting push for id=${recording.id}, file=${recording.file.name}, size=${recording.file.length()} bytes")
        sendItem(recording.file, recording.id, recording.capturedAt.toEpochMilli())
        Log.i(TAG, "push: Successfully sent data item for ${recording.id}")
        SendResult.SENT
    } catch (t: Throwable) {
        Log.w(TAG, "push failed for ${recording.id}: ${t.message}", t)
        SendResult.QUEUED
    }

    private suspend fun sendItem(file: java.io.File, id: String, timestamp: Long) {
        val path = requestPath(id)
        Log.d(TAG, "sendItem: Creating PutDataMapRequest for path=$path")
        val asset = Asset.createFromUri(Uri.fromFile(file))
        val putReq = PutDataMapRequest.create(path).apply {
            dataMap.putAsset(WearPaths.KEY_AUDIO, asset)
            dataMap.putLong(WearPaths.KEY_TIMESTAMP, timestamp)
            dataMap.putString(WearPaths.KEY_ID, id)
        }
        val result = dataClient.putDataItem(putReq.asPutDataRequest()).await()
        Log.d(TAG, "sendItem: putDataItem completed, uri=${result.uri}")
    }

    /** Returns true if a phone node is currently connected (Bluetooth reachable). */
    suspend fun phoneReachable(): Boolean {
        val nodes = nodeClient.connectedNodes.await()
        val reachable = nodes.isNotEmpty()
        Log.d(TAG, "phoneReachable: reachable=$reachable, nodes=${nodes.map { "${it.displayName}(${it.id})" }}")
        return reachable
    }

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
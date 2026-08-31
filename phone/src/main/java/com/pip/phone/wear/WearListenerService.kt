package com.pip.phone.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.pip.phone.data.NoteEntity
import com.pip.phone.data.NoteQueueManager
import com.pip.phone.data.NoteStatus
import com.pip.phone.data.PipDatabase
import com.pip.phone.worker.AudioUploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream

class WearListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onPeerConnected(node: com.google.android.gms.wearable.Node) {
        Log.i(TAG, "onPeerConnected: ${node.displayName} (${node.id})")
        PhoneWatchLink.onWatchConnected()
    }

    override fun onPeerDisconnected(node: com.google.android.gms.wearable.Node) {
        Log.i(TAG, "onPeerDisconnected: ${node.displayName} (${node.id})")
        PhoneWatchLink.onWatchDisconnected()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: received ${dataEvents.count} event(s)")
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) {
                Log.d(TAG, "onDataChanged: skipping event type ${event.type}")
                continue
            }
            val item = event.dataItem ?: continue
            val path = item.uri?.path ?: continue
            Log.d(TAG, "onDataChanged: event on path=$path, uri=${item.uri}")
            if (path.startsWith(WearPaths.AUDIO_PATH)) {
                val dataMap = DataMapItem.fromDataItem(item).dataMap
                val asset = dataMap.getAsset(WearPaths.KEY_AUDIO)
                if (asset == null) {
                    Log.w(TAG, "onDataChanged: audio path $path had no asset, skipping")
                    continue
                }
                val timestamp = dataMap.getLong(WearPaths.KEY_TIMESTAMP)
                val id = dataMap.getString(WearPaths.KEY_ID) ?: "unknown"
                Log.i(TAG, "onDataChanged: incoming audio id=$id, ts=$timestamp")
                scope.launch { receiveAudio(asset, timestamp, id) }
            } else {
                Log.d(TAG, "onDataChanged: ignoring path $path")
            }
        }
    }

    private suspend fun receiveAudio(asset: Asset, timestamp: Long, id: String) {
        val queue = NoteQueueManager(this)
        val file = queue.newAudioFile()
        if (!downloadAsset(asset, file)) {
            Log.w(TAG, "No audio received for $id: asset download failed")
            return
        }
        Log.i(TAG, "receiveAudio: saved $id to ${file.absolutePath} (${file.length()} bytes)")

        val note = NoteEntity(
            createdAt = timestamp,
            status = NoteStatus.PENDING,
            audioPath = file.absolutePath,
        )
        PipDatabase.get(this).noteDao().insert(note)
        queue.enforcePolicies(PipDatabase.get(this).noteDao())
        Log.d(TAG, "receiveAudio: inserted note for $id, status=${note.status}")

        // Kick off upload immediately.
        AudioUploadWorker.enqueue(this)
        Log.d(TAG, "receiveAudio: enqueued upload worker for $id")

        // Ack back to the watch so it can clear its queue entry.
        ackToWatch(listOf(id))
        Log.i(TAG, "receiveAudio: acked $id to watch")
    }

    private suspend fun downloadAsset(asset: Asset, target: File): Boolean = try {
        Log.d(TAG, "downloadAsset: fetching FD for asset")
        val response = Wearable.getDataClient(this).getFdForAsset(asset).await()
        val bytes = response.inputStream.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
            target.length()
        }
        response.release()
        Log.d(TAG, "downloadAsset: wrote $bytes bytes to ${target.name}")
        true
    } catch (t: Throwable) {
        Log.w(TAG, "Asset download failed", t)
        false
    }

    private suspend fun ackToWatch(ids: List<String>) {
        val putReq = PutDataMapRequest.create(WearPaths.ACK_PATH).apply {
            dataMap.putStringArrayList(WearPaths.KEY_ACK_IDS, ArrayList(ids))
        }
        runCatching { Wearable.getDataClient(this).putDataItem(putReq.asPutDataRequest()).await() }
    }

    companion object {
        private const val TAG = "PhoneWearListener"
    }
}
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

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem ?: continue
            val path = item.uri?.path ?: continue
            if (path.startsWith(WearPaths.AUDIO_PATH)) {
                val dataMap = DataMapItem.fromDataItem(item).dataMap
                val asset = dataMap.getAsset(WearPaths.KEY_AUDIO) ?: continue
                val timestamp = dataMap.getLong(WearPaths.KEY_TIMESTAMP)
                val id = dataMap.getString(WearPaths.KEY_ID) ?: "unknown"
                scope.launch { receiveAudio(asset, timestamp, id) }
            }
        }
    }

    private suspend fun receiveAudio(asset: Asset, timestamp: Long, id: String) {
        val queue = NoteQueueManager(this)
        val file = queue.newAudioFile()
        if (!downloadAsset(asset, file)) {
            Log.w(TAG, "Failed to download asset $id")
            return
        }

        val note = NoteEntity(
            createdAt = timestamp,
            status = NoteStatus.PENDING,
            audioPath = file.absolutePath,
        )
        PipDatabase.get(this).noteDao().insert(note)
        queue.enforcePolicies(PipDatabase.get(this).noteDao())

        // Kick off upload immediately.
        AudioUploadWorker.enqueue(this)

        // Ack back to the watch so it can clear its queue entry.
        ackToWatch(listOf(id))
    }

    private suspend fun downloadAsset(asset: Asset, target: File): Boolean = try {
        val response = Wearable.getDataClient(this).getFdForAsset(asset).await()
        response.inputStream.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        response.release()
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
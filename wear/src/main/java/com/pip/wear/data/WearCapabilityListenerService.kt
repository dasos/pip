package com.pip.wear.data

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.pip.wear.audio.AudioQueueManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WearCapabilityListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: received ${dataEvents.count} event(s)")
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) {
                Log.d(TAG, "onDataChanged: skipping event type ${event.type}")
                continue
            }
            val item = event.dataItem ?: continue
            val path = item.uri?.path ?: continue
            Log.d(TAG, "onDataChanged: processing event on path=$path, uri=${item.uri}")
            val dataMap = DataMapItem.fromDataItem(item).dataMap
            try {
                when {
                    path.startsWith(WearPaths.CONFIG_PATH) -> onConfig(dataMap)
                    path.startsWith(WearPaths.ACK_PATH) -> onAck(dataMap)
                    else -> Log.d(TAG, "onDataChanged: unhandled path $path")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed handling data on $path", t)
            }
        }
    }

    private fun onConfig(dataMap: com.google.android.gms.wearable.DataMap) {
        val serverUrl = dataMap.getString(WearPaths.KEY_SERVER_URL) ?: return
        val token = dataMap.getString(WearPaths.KEY_BEARER_TOKEN) ?: return
        Log.i(TAG, "onConfig: Received new config with serverUrl=$serverUrl")
        scope.launch {
            WatchConfigStore(applicationContext).update(serverUrl, token)
        }
    }

    private fun onAck(dataMap: com.google.android.gms.wearable.DataMap) {
        val ids = dataMap.getStringArrayList(WearPaths.KEY_ACK_IDS) ?: return
        Log.i(TAG, "onAck: Received ACK for ids=$ids")
        AudioQueueManager(applicationContext).clearSent(ids)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "WearCapListener"
    }
}
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
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem ?: continue
            val path = item.uri?.path ?: continue
            val dataMap = DataMapItem.fromDataItem(item).dataMap
            try {
                when {
                    path.startsWith(WearPaths.CONFIG_PATH) -> onConfig(dataMap)
                    path.startsWith(WearPaths.ACK_PATH) -> onAck(dataMap)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed handling data on $path", t)
            }
        }
    }

    private fun onConfig(dataMap: com.google.android.gms.wearable.DataMap) {
        val serverUrl = dataMap.getString(WearPaths.KEY_SERVER_URL) ?: return
        val token = dataMap.getString(WearPaths.KEY_BEARER_TOKEN) ?: return
        scope.launch {
            WatchConfigStore(applicationContext).update(serverUrl, token)
        }
    }

    private fun onAck(dataMap: com.google.android.gms.wearable.DataMap) {
        val ids = dataMap.getStringArrayList(WearPaths.KEY_ACK_IDS) ?: return
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
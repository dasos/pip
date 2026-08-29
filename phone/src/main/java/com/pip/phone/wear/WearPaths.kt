package com.pip.phone.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

object WearPaths {
    const val AUDIO_PATH = "/pip/audio"
    const val ACK_PATH = "/pip/ack"
    const val CONFIG_PATH = "/pip/config"

    const val KEY_AUDIO = "audio"
    const val KEY_TIMESTAMP = "created_at"
    const val KEY_ID = "audio_id"
    const val KEY_ACK_IDS = "ack_ids"
    const val KEY_SERVER_URL = "server_url"
    const val KEY_BEARER_TOKEN = "bearer_token"
}

/** Pushes the phone's server config to the paired watch (forward-looking mirror). */
suspend fun pushConfigToWatch(context: Context, serverUrl: String, bearerToken: String) {
    val putReq = PutDataMapRequest.create(WearPaths.CONFIG_PATH).apply {
        dataMap.putString(WearPaths.KEY_SERVER_URL, serverUrl)
        dataMap.putString(WearPaths.KEY_BEARER_TOKEN, bearerToken)
    }
    runCatching {
        Wearable.getDataClient(context).putDataItem(putReq.asPutDataRequest()).await()
    }
}
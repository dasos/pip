package com.pip.wear.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.configDataStore by preferencesDataStore(name = "pip_config")

class WatchConfigStore(private val context: Context) {

    private val serverUrlKey = stringPreferencesKey("server_url")
    private val bearerTokenKey = stringPreferencesKey("bearer_token")

    val config: Flow<PipConfig> = context.configDataStore.data.map { prefs ->
        PipConfig(
            serverUrl = prefs[serverUrlKey].orEmpty(),
            bearerToken = prefs[bearerTokenKey].orEmpty(),
        )
    }

    suspend fun update(serverUrl: String, bearerToken: String) {
        context.configDataStore.edit { prefs ->
            if (serverUrl.isNotEmpty()) prefs[serverUrlKey] = serverUrl
            if (bearerToken.isNotEmpty()) prefs[bearerTokenKey] = bearerToken
        }
    }
}

data class PipConfig(
    val serverUrl: String = "",
    val bearerToken: String = "",
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && bearerToken.isNotBlank()
}
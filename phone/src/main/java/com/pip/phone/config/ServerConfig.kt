package com.pip.phone.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the server endpoint + bearer token in encrypted preferences.
 * The token is never hardcoded; it is entered once during phone setup.
 */
class ServerConfig(context: Context) {

    private val prefs = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    fun load(): Config? = prefs?.let { p ->
        val url = p.getString(KEY_URL, null) ?: return null
        val token = p.getString(KEY_TOKEN, null) ?: return null
        Config(url, token)
    }

    fun save(config: Config): Boolean {
        return prefs?.edit()?.let { e ->
            e.putString(KEY_URL, config.serverUrl.trim())
            e.putString(KEY_TOKEN, config.bearerToken.trim())
            e.commit()
        } ?: false
    }

    fun isConfigured(): Boolean = load() != null

    data class Config(
        val serverUrl: String,
        val bearerToken: String,
    )

    companion object {
        private const val FILE = "pip_secure_config"
        private const val KEY_URL = "server_url"
        private const val KEY_TOKEN = "bearer_token"
    }
}
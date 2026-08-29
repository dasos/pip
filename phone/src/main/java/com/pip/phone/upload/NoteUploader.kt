package com.pip.phone.upload

import android.content.Context
import com.pip.phone.config.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Uploads a transcribed note to the server via `POST {base}/notes`.
 */
class NoteUploader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * @return [UploadOutcome] describing whether the note was accepted.
     */
    suspend fun upload(text: String, createdAt: Long): UploadOutcome = withContext(Dispatchers.IO) {
        val config = ServerConfig(context).load()
            ?: return@withContext UploadOutcome.FAILED

        val url = config.serverUrl.trim().trimEnd('/') + "/notes"
        val body = buildJson(text, createdAt)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.bearerToken}")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> UploadOutcome.SUCCESS
                    response.code == 401 -> UploadOutcome.AUTH_FAILED
                    else -> UploadOutcome.RETRYABLE
                }
            }
        } catch (t: Throwable) {
            UploadOutcome.RETRYABLE
        }
    }

    private fun buildJson(text: String, createdAt: Long): String {
        val escaped = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        val iso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(createdAt).atOffset(ZoneOffset.UTC))
        return """{"text":"$escaped","created_at":"$iso"}"""
    }

    enum class UploadOutcome {
        SUCCESS,
        RETRYABLE,
        AUTH_FAILED,
        FAILED,
    }
}
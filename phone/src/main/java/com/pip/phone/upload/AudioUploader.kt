package com.pip.phone.upload

import android.content.Context
import com.pip.phone.config.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Uploads a recorded WAV file to the server via `POST {base}/audio`.
 *
 * The request is `multipart/form-data` carrying the WAV bytes plus the
 * capture timestamp. Responses are mapped to [UploadOutcome] so the worker
 * can decide whether to mark the item uploaded, failed, or retry later.
 */
class AudioUploader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun upload(file: File, createdAt: Long): UploadOutcome = withContext(Dispatchers.IO) {
        val config = ServerConfig(context).load()
            ?: return@withContext UploadOutcome.FAILED

        val url = (config.serverUrl.trim().trimEnd('/') + "/audio").toHttpUrlOrNull()
            ?: return@withContext UploadOutcome.RETRYABLE
        val iso = DateTimeFormatter.ISO_INSTANT.format(
            Instant.ofEpochMilli(createdAt).atOffset(ZoneOffset.UTC)
        )
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("created_at", iso)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("audio/wav".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.bearerToken}")
            .post(body)
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

    enum class UploadOutcome {
        SUCCESS,
        RETRYABLE,
        AUTH_FAILED,
        FAILED,
    }
}
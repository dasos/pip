package com.pip.phone.recognition

import android.util.Log
import com.google.mlkit.nl.speechrecognition.SpeechRecognition
import com.google.mlkit.nl.speechrecognition.TranscriberOptions
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * File-based, on-device transcription using ML Kit's beta Transcriber.
 *
 * NOTE: The transcriber reads a pre-recorded WAV file directly (16-bit PCM mono
 * 16 kHz). A ~100 MB language model downloads on first use; after that it works
 * offline. No microphone is required.
 *
 * TODO(verify-on-device): Confirm model-download behavior and WAV acceptance.
 */
object Transcriber {

    /** Returns the transcribed text, or null on failure. */
    suspend fun transcribe(audioFile: File): String? = try {
        val transcriber = SpeechRecognition.getTranscriberClient()
        val options = TranscriberOptions.Builder().setAudioFile(audioFile).build()
        val result = transcriber.transcribe(options).await()
        result.text.ifBlank { null }
    } catch (t: Throwable) {
        Log.w(TAG, "Transcription failed for ${audioFile.name}", t)
        null
    }

    private const val TAG = "Transcriber"
}
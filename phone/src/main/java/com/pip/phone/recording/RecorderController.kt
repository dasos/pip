package com.pip.phone.recording

import android.content.Context
import com.pip.core.recording.WavRecorder
import com.pip.phone.data.NoteQueueManager
import java.io.File

/**
 * Drives the shared [WavRecorder] for the phone's hold-to-record button.
 * Capture runs in-process (no foreground service): the user holds the button,
 * so the activity stays foreground for the whole clip. See the ponytail note
 * in NotesScreen for the ceiling and upgrade path.
 */
class RecorderController(private val context: Context) {

    private var recorder: WavRecorder? = null
    var isActive: Boolean = false
        private set

    /**
     * Starts recording into a fresh file in the notes audio directory.
     * Returns false if the microphone could not be acquired (or a capture was
     * already in progress, as a double-press guard).
     */
    fun start(): Boolean {
        if (isActive) return false
        val file = NoteQueueManager(context).newAudioFile()
        val rec = WavRecorder(file)
        if (!rec.start()) {
            file.delete()
            return false
        }
        recorder = rec
        isActive = true
        return true
    }

    /**
     * Finalizes the WAV header. Clips shorter than one second are discarded so
     * a tap does not create an accidental note.
     */
    fun stop(): StopResult? {
        val rec = recorder ?: return null
        recorder = null
        isActive = false
        val startedAt = rec.startedAt
        rec.close()
        val captured = CapturedNote(rec.outputFile, startedAt.toEpochMilli())
        return if (java.time.Duration.between(startedAt, java.time.Instant.now()).toMillis() < MIN_RECORDING_MS) {
            captured.file.delete()
            StopResult(captured = null, tooShort = true)
        } else {
            StopResult(captured = captured, tooShort = false)
        }
    }

    data class StopResult(val captured: CapturedNote?, val tooShort: Boolean)
    data class CapturedNote(val file: File, val createdAt: Long)

    companion object {
        private const val MIN_RECORDING_MS = 1_000L
    }
}
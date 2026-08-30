package com.pip.wear.recording

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.time.Instant

/**
 * Records microphone input to a 16-bit PCM mono 16 kHz WAV file — the format
 * required by the phone's ML Kit Transcriber.
 */
class WavRecorder(private val outputFile: File) : Closeable {

    private var audioRecord: AudioRecord? = null
    private var stream: BufferedOutputStream? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var dataBytes = 0L
    private val startTime: Instant = Instant.now()
    val startedAt: Instant get() = startTime

    /** Starts recording. Returns false if the microphone could not be acquired. */
    fun start(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) return false

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        val out = BufferedOutputStream(FileOutputStream(outputFile))
        writeWaveHeader(out, 0L)

        try {
            record.startRecording()
        } catch (t: Throwable) {
            runCatching { out.close() }
            record.release()
            return false
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            runCatching { out.close() }
            record.release()
            return false
        }
        audioRecord = record
        stream = out

        job = scope.launch {
            val buffer = ShortArray(minBuf / 2)
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val bytes = toPcmBytes(buffer, read)
                    out.write(bytes)
                    out.flush()
                    dataBytes += bytes.size.toLong()
                }
            }
        }
        return true
    }

    fun elapsedMillis(): Long = System.currentTimeMillis() - startTime.toEpochMilli()

    override fun close() {
        job?.cancel()
        runCatching { audioRecord?.stop() }
        audioRecord?.release()
        audioRecord = null
        runCatching { stream?.flush() }
        runCatching { stream?.close() }
        stream = null
        job = null
        finalizeHeader()
    }

    private fun finalizeHeader() {
        if (!outputFile.exists()) return
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        runCatching {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.seek(4)
                writeIntLittleEndian(raf, (36 + dataBytes).toInt())
                raf.seek(22)
                writeShortLittleEndian(raf, channels)
                raf.seek(24)
                writeIntLittleEndian(raf, SAMPLE_RATE)
                raf.seek(28)
                writeIntLittleEndian(raf, byteRate)
                raf.seek(32)
                writeShortLittleEndian(raf, blockAlign)
                raf.seek(40)
                writeIntLittleEndian(raf, dataBytes.toInt())
            }
        }
    }

    private fun writeWaveHeader(out: BufferedOutputStream, dataSize: Long) {
        val header = ByteArray(WAV_HEADER)
        // RIFF chunk
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLittleEndian(header, 4, (36 + dataSize).toInt())
        // WAVE
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        // fmt chunk
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLittleEndian(header, 16, 16)                 // fmt chunk size
        writeShortLittleEndian(header, 20, 1)                 // PCM
        writeShortLittleEndian(header, 22, 1)                 // channels = mono
        writeIntLittleEndian(header, 24, SAMPLE_RATE)
        writeIntLittleEndian(header, 28, SAMPLE_RATE * 2)     // byte rate
        writeShortLittleEndian(header, 32, 2)                 // block align
        writeShortLittleEndian(header, 34, 16)                // bits per sample
        // data chunk
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeIntLittleEndian(header, 40, dataSize.toInt())
        out.write(header)
        out.flush()
    }

    private fun toPcmBytes(data: ShortArray, read: Int): ByteArray {
        val bytes = ByteArray(read * 2)
        var j = 0
        for (i in 0 until read) {
            val s = data[i].toInt()
            bytes[j++] = (s and 0xFF).toByte()
            bytes[j++] = ((s shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun writeIntLittleEndian(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xFF)
        raf.write((value shr 8) and 0xFF)
        raf.write((value shr 16) and 0xFF)
        raf.write((value shr 24) and 0xFF)
    }

    private fun writeShortLittleEndian(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xFF)
        raf.write((value shr 8) and 0xFF)
    }

    private fun writeIntLittleEndian(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLittleEndian(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val WAV_HEADER = 44
    }
}
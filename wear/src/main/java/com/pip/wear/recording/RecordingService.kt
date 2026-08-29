package com.pip.wear.recording

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pip.wear.R
import com.pip.wear.audio.AudioQueueManager
import com.pip.wear.data.WearSendClient
import com.pip.wear.ui.recording.RecordingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service owning the active microphone capture so recording continues
 * even if the screen turns off mid-hold, and handles the post-release pipeline.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var recorder: WavRecorder? = null
    private var outputFile: File? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var recordingActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()
                beginRecording(intent.getStringExtra(EXTRA_OUTPUT_FILE))
            }
            ACTION_STOP -> stopAndProcess()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, RecordingActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fg_notification_title))
            .setContentText(getString(R.string.fg_notification_text))
            .setSmallIcon(R.drawable.tile_preview)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        acquireWakeLock()
    }

    private fun beginRecording(output: String?) {
        val queue = AudioQueueManager(this)
        val file = output?.let(::File)?.takeIf { !it.exists() } ?: queue.newRecordingFile()

        val rec = WavRecorder(file)
        if (!rec.start()) {
            stopSelfResult()
            return
        }
        recorder = rec
        outputFile = file
        recordingActive = true
        performCaptureHaptic()
    }

    /**
     * Stops capture, enqueues the recording, and immediately attempts delivery.
     * Invoked by the UI (or [ACTION_STOP]) when the button is released.
     */
    fun stopAndProcess() {
        val rec = recorder ?: run { stopSelfResult(); return }
        if (!recordingActive) return
        recordingActive = false
        performCaptureHaptic()
        rec.close()
        recorder = null

        val file = outputFile ?: return
        outputFile = null

        scope.launch {
            val queue = AudioQueueManager(this@RecordingService)
            val entry = queue.enqueue(file, rec.startedAt)

            val result = try {
                WearSendClient(applicationContext as Application).push(entry)
            } catch (t: Throwable) {
                WearSendClient.SendResult.QUEUED
            }
            statusFlow.value = when (result) {
                WearSendClient.SendResult.SENT -> DeliveryStatus.Sent
                else -> DeliveryStatus.Queued
            }
            stopSelfResult()
        }
    }

    private fun stopSelfResult() {
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun performCaptureHaptic() {
        val effect = VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
        @Suppress("DEPRECATION")
        val hasVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val v = getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
            v.hasVibrator()
        } else {
            val v = getSystemService(Vibrator::class.java) ?: return
            v.hasVibrator()
        }
        if (!hasVibrator) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)?.vibrate(effect)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (wakeLock?.isHeld == true) return
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:recording")
        wl.setReferenceCounted(false)
        wl.acquire(WORK_LOCK_DURATION_MS)
        wakeLock = wl
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fg_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.fg_channel_desc) }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        runCatching { recorder?.close() }
        recorder = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
    }

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1001
        private const val WORK_LOCK_DURATION_MS = 5 * 60 * 1000L

        const val ACTION_START = "com.pip.wear.action.START_RECORDING"
        const val ACTION_STOP = "com.pip.wear.action.STOP_RECORDING"
        const val EXTRA_OUTPUT_FILE = "output_file"

        @Volatile
        var instance: RecordingService? = null
            private set

        private val _statusFlow = MutableStateFlow<DeliveryStatus?>(null)
        val statusFlow: StateFlow<DeliveryStatus?> = _statusFlow.asStateFlow()

        fun postStart(context: Context, output: File) {
            val intent = Intent(context, RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_OUTPUT_FILE, output.absolutePath)
            context.startForegroundService(intent)
        }

        fun postStop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
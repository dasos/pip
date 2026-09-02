package com.pip.phone

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.wearable.Wearable
import com.pip.phone.worker.PhoneWorkerBuilder
import com.pip.phone.worker.AudioUploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class PipPhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerWithWearable()
        schedulePeriodicWork()
    }

    /**
     * Makes a real Wear Data Layer call so Play Services registers this app as a
     * recipient for events from the paired watch. Without this, GMS only knows the
     * watch app (which always calls the API when it pushes recordings), and
     * /pip/audio events never reach this app's WearListenerService.
     */
    private fun registerWithWearable() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { Wearable.getNodeClient(this@PipPhoneApp).connectedNodes.await() }
        }
    }

    private fun schedulePeriodicWork() {
        val wm = WorkManager.getInstance(this)

        val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val upload = PeriodicWorkRequestBuilder<AudioUploadWorker>(
            PhoneWorkerBuilder.UPLOAD_PERIOD_MINUTES, TimeUnit.MINUTES
        ).setConstraints(networkConstraint).build()

        wm.enqueueUniquePeriodicWork(
            PhoneWorkerBuilder.UPLOAD_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            upload
        )
    }
}
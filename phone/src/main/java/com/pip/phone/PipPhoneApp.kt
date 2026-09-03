package com.pip.phone

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.wearable.Wearable
import com.pip.phone.wear.PhoneWatchLink
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
     * Registers this app as a Wear Data Layer recipient and seeds the connection
     * state because peer callbacks only report changes after the listener binds.
     */
    private fun registerWithWearable() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                Wearable.getNodeClient(this@PipPhoneApp).connectedNodes.await()
            }.onSuccess { nodes ->
                PhoneWatchLink.setWatchConnected(nodes.isNotEmpty())
            }
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
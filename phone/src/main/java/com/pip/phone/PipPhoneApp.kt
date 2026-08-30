package com.pip.phone

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pip.phone.worker.PhoneWorkerBuilder
import com.pip.phone.worker.AudioUploadWorker
import java.util.concurrent.TimeUnit

class PipPhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        schedulePeriodicWork()
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
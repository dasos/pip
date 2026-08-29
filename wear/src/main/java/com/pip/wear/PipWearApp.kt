package com.pip.wear

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pip.wear.worker.SendWorker
import com.pip.wear.worker.WorkerBuilder
import java.util.concurrent.TimeUnit

class PipWearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleSendWorker()
    }

    private fun scheduleSendWorker() {
        val request = PeriodicWorkRequestBuilder<SendWorker>(
            WorkerBuilder.SEND_PERIOD_MINUTES,
            TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WorkerBuilder.SEND_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
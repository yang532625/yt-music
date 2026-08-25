package com.metrolist.music.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.metrolist.music.BuildConfig
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object UpdateCheckScheduler {
    private const val WORK_NAME = "ytm-update-check"
    private val started = AtomicBoolean(false)

    fun start(context: Context) {
        if (!BuildConfig.UPDATER_AVAILABLE) return
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        runCatching {
            val request =
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build()
            WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }.onFailure { Timber.w(it, "Update check worker not started") }
    }
}

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        UpdateCoordinator.checkForUpdates(applicationContext, notifyIfAvailable = true)
        return Result.success()
    }
}

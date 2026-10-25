package com.stepvault.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stepvault.app.widget.WidgetUpdater
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class StepSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        WidgetUpdater.updateAll(applicationContext)
        Result.success()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "stepvault-step-sync"
        private const val MAX_RETRIES = 3

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StepSyncWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.MINUTES,
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}

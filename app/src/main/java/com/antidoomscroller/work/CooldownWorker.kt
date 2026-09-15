package com.antidoomscroller.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.antidoomscroller.AppContainer
import java.util.concurrent.TimeUnit

/**
 * Keeps the cooldown ticking while the app is closed.
 *
 * Each run only records a pair of clock readings; the arithmetic that decides whether time was
 * really served lives in the core module and is tested there. Missing a run costs nothing - the
 * next checkpoint credits the monotonic time that passed in between.
 */
class CooldownWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.from(applicationContext)
        container.lockRepository.checkpoint()
        container.scrollPassRepository.checkpoint(container.settingsRepository.awaitLoaded().scrollPass)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "cooldown-checkpoint"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CooldownWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

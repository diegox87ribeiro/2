package com.example.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object PriceAlertWorkScheduler {

    private const val PERIODIC_WORK_NAME = "invest_ai_price_target_monitor"
    private const val IMMEDIATE_WORK_NAME = "invest_ai_price_target_check_now"

    fun syncSchedule(context: Context, hasActiveAlerts: Boolean) {
        runCatching {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (hasActiveAlerts) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val periodic = PeriodicWorkRequestBuilder<PriceTargetMonitorWorker>(
                    15,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()
                wm.enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodic
                )
            } else {
                wm.cancelUniqueWork(PERIODIC_WORK_NAME)
            }
        }
    }

    fun triggerImmediateCheck(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val immediate = OneTimeWorkRequestBuilder<PriceTargetMonitorWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, immediate)
        }
    }
}

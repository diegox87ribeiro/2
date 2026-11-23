package com.example.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SmartAlertWorkScheduler {

    private const val PERIODIC_WORK_NAME = "invest_ai_smart_alerts_monitor"
    private const val IMMEDIATE_WORK_NAME = "invest_ai_smart_alerts_check_now"

    fun syncSchedule(context: Context, hasActiveAlertsOrGoals: Boolean) {
        runCatching {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (hasActiveAlertsOrGoals) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val periodic = PeriodicWorkRequestBuilder<SmartAlertMonitorWorker>(
                    15,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
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

    fun triggerImmediateCheck(context: Context, requireNetwork: Boolean = true) {
        runCatching {
            val builder = OneTimeWorkRequestBuilder<SmartAlertMonitorWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            if (requireNetwork) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                builder.setConstraints(constraints)
            }
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, builder.build())
        }
    }
}

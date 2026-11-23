package com.example.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DividendDateAlertWorkScheduler {

    private const val PERIODIC_WORK_NAME = "invest_ai_dividend_date_monitor"
    private const val IMMEDIATE_WORK_NAME = "invest_ai_dividend_date_check_now"

    fun syncSchedule(context: Context, shouldRun: Boolean) {
        runCatching {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (shouldRun) {
                // Sem exigência de rede: avalia Data-Com e Data-Ex localmente usando Room + catálogo
                val periodic = PeriodicWorkRequestBuilder<DividendDateAlertWorker>(
                    6,
                    TimeUnit.HOURS
                ).build()
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
            val req = OneTimeWorkRequestBuilder<DividendDateAlertWorker>().build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, req)
        }
    }
}

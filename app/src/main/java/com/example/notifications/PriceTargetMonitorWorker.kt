package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.InvestDatabase
import com.example.data.repository.LiveMarketDataRepository
import com.example.domain.model.PriceTargetEvaluator

class PriceTargetMonitorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = InvestDatabase.getInstance(applicationContext)
        val dao = db.priceTargetAlertDao()
        val activeAlerts = dao.getActiveAlerts()
        if (activeAlerts.isEmpty()) {
            return Result.success()
        }

        val distinctTickers = activeAlerts
            .map { it.ticker.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
        if (distinctTickers.isEmpty()) {
            return Result.success()
        }

        val liveRepo = LiveMarketDataRepository()
        val quotes = runCatching { liveRepo.fetchBatchQuotes(distinctTickers) }
            .getOrDefault(emptyMap())

        if (quotes.isEmpty()) {
            return if (runAttemptCount < 2) Result.retry() else Result.success()
        }

        val now = System.currentTimeMillis()
        activeAlerts.forEach { alert ->
            val quote = quotes[alert.ticker.trim().uppercase()] ?: return@forEach
            if (PriceTargetEvaluator.isTargetReached(alert, quote.price)) {
                PriceAlertNotificationHelper.sendPriceTargetNotification(
                    context = applicationContext,
                    alert = alert,
                    currentPriceBrl = quote.price,
                    sourceLabel = quote.primarySourceLabel
                )
                dao.markTriggered(alert.id, quote.price, now)
            } else {
                dao.updateLastEvaluated(alert.id, quote.price, now)
            }
        }

        return Result.success()
    }
}

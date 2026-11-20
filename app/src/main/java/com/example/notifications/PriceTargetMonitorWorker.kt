package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.CachedMarketQuoteEntity
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
        val cachedDao = db.cachedMarketQuoteDao()

        // 1. Reentrega imediatamente qualquer notificação pendente salva no Room (funciona mesmo offline)
        val pendingAlerts = dao.getPendingNotificationAlerts()
        pendingAlerts.forEach { pending ->
            val price = pending.lastEvaluatedPriceBrl ?: pending.targetPriceBrl
            val delivered = PriceAlertNotificationHelper.sendPriceTargetNotification(
                context = applicationContext,
                alert = pending,
                currentPriceBrl = price,
                sourceLabel = "Cache local Room"
            )
            if (delivered) {
                dao.markNotificationDelivered(pending.id)
            }
        }

        // 2. Avalia metas ativas armazenadas no Room
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
            // Sem rede no momento: verifica cotações em cache Room mais recentes que o alerta
            val now = System.currentTimeMillis()
            val stillOfflineIds = mutableListOf<Long>()
            activeAlerts.forEach { alert ->
                val cached = cachedDao.getByTicker(alert.ticker.trim().uppercase())
                if (cached != null && cached.fetchedAtMillis >= alert.createdAt && PriceTargetEvaluator.isTargetReached(alert, cached.price)) {
                    val delivered = PriceAlertNotificationHelper.sendPriceTargetNotification(
                        context = applicationContext,
                        alert = alert,
                        currentPriceBrl = cached.price,
                        sourceLabel = "${cached.primarySourceLabel} (Room Offline)"
                    )
                    dao.markTriggered(
                        id = alert.id,
                        evaluatedPriceBrl = cached.price,
                        triggeredAt = now,
                        pendingNotification = !delivered,
                        notificationDelivered = delivered,
                        status = if (delivered) "Meta atingida (Cache Room) • Push entregue" else "Meta atingida • Push pendente"
                    )
                } else {
                    stillOfflineIds.add(alert.id)
                }
            }
            if (stillOfflineIds.isNotEmpty()) {
                dao.markOfflineQueued(stillOfflineIds)
                return if (runAttemptCount < 5) Result.retry() else Result.success()
            }
            return Result.success()
        }

        // Persiste novas cotações no Room para resiliência offline
        val cachedEntities = quotes.values.map { q ->
            CachedMarketQuoteEntity(
                ticker = q.ticker.trim().uppercase(),
                companyName = q.companyName,
                price = q.price,
                changePercent1d = q.changePercent1d,
                dy12m = q.dividendYield12m,
                pvp = q.priceToBook,
                pl = q.priceToEarnings,
                roe = q.roePercent,
                dailyVolumeBrlMillions = q.dailyVolumeBrlMillions,
                lastDividendBrl = q.lastDividendBrl,
                dataComDay = q.dataComDay,
                paymentDay = q.paymentDay,
                primarySourceLabel = q.primarySourceLabel,
                marketTimestampLabel = q.marketTimestampLabel,
                fetchedAtMillis = q.fetchedAtMillis
            )
        }
        cachedDao.upsertAll(cachedEntities)

        val now = System.currentTimeMillis()
        val missingTickersAlertIds = mutableListOf<Long>()
        activeAlerts.forEach { alert ->
            val quote = quotes[alert.ticker.trim().uppercase()]
            if (quote == null) {
                missingTickersAlertIds.add(alert.id)
                return@forEach
            }
            if (PriceTargetEvaluator.isTargetReached(alert, quote.price)) {
                val delivered = PriceAlertNotificationHelper.sendPriceTargetNotification(
                    context = applicationContext,
                    alert = alert,
                    currentPriceBrl = quote.price,
                    sourceLabel = quote.primarySourceLabel
                )
                dao.markTriggered(
                    id = alert.id,
                    evaluatedPriceBrl = quote.price,
                    triggeredAt = now,
                    pendingNotification = !delivered,
                    notificationDelivered = delivered,
                    status = if (delivered) "Meta atingida • Push entregue" else "Meta atingida • Push pendente"
                )
            } else {
                dao.updateLastEvaluated(alert.id, quote.price, now)
            }
        }
        if (missingTickersAlertIds.isNotEmpty()) {
            dao.markOfflineQueued(missingTickersAlertIds)
        }

        return Result.success()
    }
}


package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AlertNotificationHistoryEntity
import com.example.data.local.CachedMarketQuoteEntity
import com.example.data.local.InvestDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.LiveMarketDataRepository
import com.example.data.repository.MarketCatalogRepository
import com.example.domain.model.AssetClass
import com.example.domain.model.DividendMonitorCalculator
import com.example.domain.model.InvestmentGoalType
import com.example.domain.model.PortfolioItemView
import com.example.domain.model.PortfolioType
import com.example.domain.model.SmartAlertEvaluator
import kotlinx.coroutines.flow.first
import java.util.Locale

class SmartAlertMonitorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = InvestDatabase.getInstance(applicationContext)
        val smartDao = db.smartAlertDao()
        val cachedDao = db.cachedMarketQuoteDao()
        val portfolioDao = db.portfolioDao()
        val prefs = PreferencesManager(applicationContext)

        val now = System.currentTimeMillis()
        val todayKey = SmartAlertEvaluator.todayDayKey(now)
        val liveRepo = LiveMarketDataRepository()

        // 1. Avaliar alertas de variações bruscas em ativos específicos
        val activeSwings = smartDao.getActiveSwingAlerts()
        if (activeSwings.isNotEmpty()) {
            val distinctTickers = activeSwings
                .map { it.ticker.trim().uppercase(Locale.ROOT) }
                .filter { it.isNotBlank() }
                .distinct()

            val quotes = if (distinctTickers.isNotEmpty()) {
                runCatching { liveRepo.fetchBatchQuotes(distinctTickers) }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }

            if (quotes.isNotEmpty()) {
                cachedDao.upsertAll(
                    quotes.values.map { q ->
                        CachedMarketQuoteEntity(
                            ticker = q.ticker.trim().uppercase(Locale.ROOT),
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
                )
            }

            activeSwings.forEach { alert ->
                val cleanTicker = alert.ticker.trim().uppercase(Locale.ROOT)
                val live = quotes[cleanTicker]
                val cached = if (live == null) cachedDao.getByTicker(cleanTicker) else null
                val changePct = live?.changePercent1d ?: cached?.changePercent1d ?: return@forEach
                val priceBrl = live?.price ?: cached?.price ?: return@forEach
                val sourceLabel = live?.primarySourceLabel
                    ?: cached?.let { "${it.primarySourceLabel} (Cache Room)" }
                    ?: "Mercado"

                if (SmartAlertEvaluator.isSharpSwingTriggered(alert, changePct, todayKey)) {
                    val delivered = SmartAlertNotificationHelper.sendSharpSwingNotification(
                        context = applicationContext,
                        alert = alert,
                        changePercent1d = changePct,
                        currentPriceBrl = priceBrl,
                        sourceLabel = sourceLabel
                    )
                    smartDao.markSwingTriggered(
                        id = alert.id,
                        changePercent = changePct,
                        priceBrl = priceBrl,
                        triggeredAt = now,
                        dayKey = todayKey
                    )
                    val signed = String.format(Locale.forLanguageTag("pt-BR"), "%+.2f%%", changePct)
                    val moveLabel = if (changePct >= 0.0) "Alta brusca" else "Queda brusca"
                    smartDao.insertNotificationHistory(
                        AlertNotificationHistoryEntity(
                            category = "SWING",
                            ticker = cleanTicker,
                            title = "$moveLabel em $cleanTicker ($signed)",
                            message = "Variação de $signed atingiu o gatilho de ${"%.1f".format(alert.thresholdPercent)}% ($sourceLabel).",
                            deliveredAsPush = delivered,
                            createdAt = now
                        )
                    )
                } else {
                    smartDao.updateSwingLastEvaluated(
                        id = alert.id,
                        changePercent = changePct,
                        priceBrl = priceBrl,
                        evaluatedAt = now
                    )
                }
            }
        }

        // 2. Avaliar metas de investimento ativas
        val activeGoals = smartDao.getActiveInvestmentGoals()
        if (activeGoals.isNotEmpty()) {
            val realPositionEntities = portfolioDao.getPositionsByType(PortfolioType.REAL.name)
            val realPositions = realPositionEntities.map { e ->
                PortfolioItemView(
                    id = e.id,
                    portfolioType = PortfolioType.REAL,
                    ticker = e.ticker,
                    name = e.name,
                    assetClass = runCatching { AssetClass.valueOf(e.assetClass) }.getOrDefault(AssetClass.ACOES_BR),
                    quantity = e.quantity,
                    averagePrice = e.averagePrice,
                    currentPrice = e.currentPrice,
                    sector = e.sector,
                    dataComDay = e.dataComDay,
                    dataSourceLabel = e.dataSourceLabel,
                    isDemo = e.isDemo,
                    hasMarketPrice = e.hasMarketPrice,
                    isCyclical = e.isCyclical
                )
            }
            val prefsState = prefs.preferencesFlow.first()
            val totalEquityBrl = realPositions.sumOf { it.totalCurrentBrl } + prefsState.cashRealBrl
            val allOperations = portfolioDao.observeAllOperations().first()
            val dividendSummary = DividendMonitorCalculator.compute(
                realPositions = realPositions,
                marketAssets = MarketCatalogRepository.catalogAssets,
                operations = allOperations
            )
            val monthlyDividendsBrl = maxOf(
                dividendSummary.projectedMonthlyBrl,
                dividendSummary.receivedMonthlyBrl
            )

            activeGoals.forEach { goal ->
                val progress = SmartAlertEvaluator.computeGoalProgress(
                    goal = goal,
                    totalEquityBrl = totalEquityBrl,
                    estimatedMonthlyDividendsBrl = monthlyDividendsBrl,
                    realPositions = realPositions
                )
                if (progress.isReached) {
                    val delivered = SmartAlertNotificationHelper.sendInvestmentGoalNotification(
                        context = applicationContext,
                        goal = goal,
                        currentValue = progress.currentValue,
                        targetValue = progress.targetValue
                    )
                    smartDao.markGoalTriggered(
                        id = goal.id,
                        currentValue = progress.currentValue,
                        triggeredAt = now
                    )
                    val goalType = InvestmentGoalType.fromCode(goal.goalType)
                    smartDao.insertNotificationHistory(
                        AlertNotificationHistoryEntity(
                            category = "GOAL",
                            ticker = goal.ticker.trim().uppercase(Locale.ROOT),
                            title = "Meta alcançada: ${goal.title}",
                            message = "${goalType.label} chegou a ${SmartAlertEvaluator.formatGoalValue(goalType, progress.currentValue)} (alvo ${SmartAlertEvaluator.formatGoalValue(goalType, progress.targetValue)}).",
                            deliveredAsPush = delivered,
                            createdAt = now
                        )
                    )
                } else {
                    smartDao.updateGoalProgress(goal.id, progress.currentValue)
                }
            }
        }

        return Result.success()
    }
}

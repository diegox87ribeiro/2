package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.InvestDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.MarketCatalogRepository
import com.example.domain.model.AssetClass
import com.example.domain.model.DividendDateAlertEvaluator
import com.example.domain.model.LiveMarketQuote
import com.example.domain.model.PortfolioItemView
import com.example.domain.model.PortfolioType

class DividendDateAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefsManager = PreferencesManager(applicationContext)
        val prefs = prefsManager.getCurrentPreferences()
        if (!prefs.dividendAlertsEnabled || (!prefs.dividendAlertDataCom && !prefs.dividendAlertDataEx)) {
            return Result.success()
        }

        val db = InvestDatabase.getInstance(applicationContext)
        val rawPositions = db.portfolioDao().getPositionsByType(PortfolioType.REAL.name)
        if (rawPositions.isEmpty()) {
            return Result.success()
        }

        val realPositions = rawPositions.map { entity ->
            PortfolioItemView(
                id = entity.id,
                portfolioType = PortfolioType.REAL,
                ticker = entity.ticker,
                name = entity.name,
                assetClass = runCatching { AssetClass.valueOf(entity.assetClass) }.getOrDefault(AssetClass.ACOES_BR),
                quantity = entity.quantity,
                averagePrice = entity.averagePrice,
                currentPrice = entity.currentPrice,
                sector = entity.sector,
                dataComDay = entity.dataComDay,
                dataSourceLabel = entity.dataSourceLabel,
                isDemo = entity.isDemo,
                hasMarketPrice = entity.hasMarketPrice
            )
        }

        val cachedQuotes = db.cachedMarketQuoteDao().getAllOnce().associate { c ->
            c.ticker.trim().uppercase() to LiveMarketQuote(
                ticker = c.ticker,
                companyName = c.companyName,
                price = c.price,
                changePercent1d = c.changePercent1d,
                dividendYield12m = c.dy12m,
                priceToBook = c.pvp,
                priceToEarnings = c.pl,
                roePercent = c.roe,
                dailyVolumeBrlMillions = c.dailyVolumeBrlMillions,
                lastDividendBrl = c.lastDividendBrl,
                dataComDay = c.dataComDay,
                paymentDay = c.paymentDay,
                primarySourceLabel = c.primarySourceLabel,
                consultedSources = listOf(c.primarySourceLabel),
                marketTimestampLabel = c.marketTimestampLabel,
                b3PageUrl = "",
                advfnPageUrl = "",
                fetchedAtMillis = c.fetchedAtMillis
            )
        }

        val upcoming = DividendDateAlertEvaluator.findUpcomingAlerts(
            realPositions = realPositions,
            marketAssets = MarketCatalogRepository.catalogAssets,
            liveQuotesByTicker = cachedQuotes,
            leadDays = prefs.dividendAlertLeadDays,
            notifyDataCom = prefs.dividendAlertDataCom,
            notifyDataEx = prefs.dividendAlertDataEx
        )

        val newlySent = mutableListOf<String>()
        upcoming.forEach { alert ->
            if (alert.dedupeKey in prefs.dividendAlertSentKeys) return@forEach
            val delivered = DividendDateNotificationHelper.sendDividendDateNotification(
                context = applicationContext,
                alert = alert
            )
            if (delivered) {
                newlySent.add(alert.dedupeKey)
            }
        }

        if (newlySent.isNotEmpty()) {
            prefsManager.recordSentDividendAlertKeys(newlySent)
        }

        return Result.success()
    }
}

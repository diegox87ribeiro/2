package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.importexport.ImportParseResult
import com.example.data.importexport.SpreadsheetImporter
import com.example.data.local.AiRecommendationDao
import com.example.data.local.AppPreferencesState
import com.example.data.local.CryptoVirtualDao
import com.example.data.local.CryptoVirtualPositionEntity
import com.example.data.local.ImportHistoryEntity
import com.example.data.local.InvestDatabase
import com.example.data.local.PortfolioDao
import com.example.data.local.PortfolioOperationEntity
import com.example.data.local.PortfolioPositionEntity
import com.example.data.local.PreferencesManager
import com.example.data.local.PriceTargetAlertDao
import com.example.data.local.PriceTargetAlertEntity
import com.example.data.local.SavedAiRecommendationEntity
import com.example.data.local.StrategyChangeLogEntity
import com.example.data.local.StrategyCriteriaEntity
import com.example.data.local.StrategyDao
import com.example.domain.model.AiRecommendation
import com.example.domain.model.AssetClass
import com.example.domain.model.OperationType
import com.example.domain.model.PortfolioItemView
import com.example.domain.model.PortfolioType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PortfolioRepository(
    private val context: Context,
    private val db: InvestDatabase,
    private val prefs: PreferencesManager
) {
    private val portfolioDao: PortfolioDao = db.portfolioDao()
    private val strategyDao: StrategyDao = db.strategyDao()
    private val cryptoDao: CryptoVirtualDao = db.cryptoVirtualDao()
    private val aiDao: AiRecommendationDao = db.aiRecommendationDao()
    private val alertDao: PriceTargetAlertDao = db.priceTargetAlertDao()

    val preferencesFlow: Flow<AppPreferencesState> = prefs.preferencesFlow

    val positionsFlow: Flow<List<PortfolioItemView>> = portfolioDao.observeAllPositions().map { list ->
        list.map { entity ->
            PortfolioItemView(
                id = entity.id,
                portfolioType = runCatching { PortfolioType.valueOf(entity.portfolioType) }.getOrDefault(PortfolioType.REAL),
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
    }

    val operationsFlow: Flow<List<PortfolioOperationEntity>> = portfolioDao.observeAllOperations()
    val importHistoryFlow: Flow<List<ImportHistoryEntity>> = portfolioDao.observeImportHistory()
    val strategyCriteriaFlow: Flow<StrategyCriteriaEntity> = strategyDao.observeCriteria().map { it ?: StrategyCriteriaEntity() }
    val strategyChangeLogFlow: Flow<List<StrategyChangeLogEntity>> = strategyDao.observeChangeLog()
    val cryptoVirtualPositionsFlow: Flow<List<CryptoVirtualPositionEntity>> = cryptoDao.observePositions()
    val savedAiRecommendationsFlow: Flow<List<SavedAiRecommendationEntity>> = aiDao.observeSaved()
    val priceTargetAlertsFlow: Flow<List<PriceTargetAlertEntity>> = alertDao.observeAll()

    suspend fun ensureInitialDefaults() {
        // Inicializa apenas critérios padrão de estratégia e carteira cripto virtual R$ 500
        // NUNCA inventa posições na Carteira Real!
        strategyDao.upsertCriteria(StrategyCriteriaEntity())
    }

    suspend fun initDefaultCryptoWalletIfEmpty(current: List<CryptoVirtualPositionEntity>) {
        if (current.isEmpty()) {
            cryptoDao.upsertAll(defaultAggressiveCryptoWallet())
        }
    }

    suspend fun resetCryptoVirtualWallet() {
        cryptoDao.clearAll()
        cryptoDao.upsertAll(defaultAggressiveCryptoWallet())
    }

    suspend fun updateCryptoVirtualAllocations(
        btcPercent: Double,
        ethPercent: Double,
        solPercent: Double
    ) {
        val totalPct = (btcPercent + ethPercent + solPercent).coerceAtLeast(1.0)
        val totalCapital = 500.0
        val items = listOf(
            CryptoVirtualPositionEntity("BTC", "Bitcoin", totalCapital * (btcPercent / totalPct), 495000.0, 524000.0),
            CryptoVirtualPositionEntity("ETH", "Ethereum", totalCapital * (ethPercent / totalPct), 16200.0, 17900.0),
            CryptoVirtualPositionEntity("SOL", "Solana", totalCapital * (solPercent / totalPct), 980.0, 1190.0)
        )
        cryptoDao.upsertAll(items)
    }

    private fun defaultAggressiveCryptoWallet(): List<CryptoVirtualPositionEntity> = listOf(
        CryptoVirtualPositionEntity("BTC", "Bitcoin", 250.0, 465000.0, 524000.0),
        CryptoVirtualPositionEntity("ETH", "Ethereum", 150.0, 15400.0, 17900.0),
        CryptoVirtualPositionEntity("SOL", "Solana", 100.0, 910.0, 1190.0)
    )

    suspend fun parseSpreadsheetPreview(uri: Uri): ImportParseResult {
        return SpreadsheetImporter.importFromUri(context, uri)
    }

    suspend fun commitRealPortfolioImport(result: ImportParseResult) {
        if (result.positions.isEmpty() && result.dividends.isEmpty()) return
        if (result.positions.isNotEmpty()) {
            val history = ImportHistoryEntity(
                fileName = result.fileName,
                fileType = result.fileType,
                recordCount = result.positions.size,
                cashBalanceBrl = result.cashBalanceBrl ?: 0.0,
                isActiveRealReference = true,
                isDemoData = false,
                notes = if (result.warnings.isEmpty()) "Importação limpa sem alertas." else result.warnings.joinToString(" | ")
            )
            portfolioDao.replaceRealPortfolioFromImport(history, result.positions)
        }
        if (result.dividends.isNotEmpty()) {
            val existingRefs = portfolioDao.getExistingExternalRefIds().toHashSet()
            val newDivs = result.dividends.filter { it.externalRefId.isBlank() || it.externalRefId !in existingRefs }
            if (newDivs.isNotEmpty()) {
                portfolioDao.insertOperations(newDivs)
            }
        }
        result.cashBalanceBrl?.let { prefs.setCashRealBrl(it) }
    }

    suspend fun importSpreadsheet(uri: Uri): ImportParseResult {
        val result = parseSpreadsheetPreview(uri)
        commitRealPortfolioImport(result)
        return result
    }

    suspend fun loadDemoRealPortfolio() {
        val demoPositions = listOf(
            PortfolioPositionEntity(
                portfolioType = "REAL", ticker = "ITUB4", name = "Itaú Unibanco PN",
                assetClass = AssetClass.ACOES_BR.name, quantity = 180.0, averagePrice = 31.50,
                currentPrice = 35.80, hasMarketPrice = true, sector = "Bancos", dataComDay = 3,
                dataSourceLabel = "Demonstração Fictícia", isDemo = true
            ),
            PortfolioPositionEntity(
                portfolioType = "REAL", ticker = "HGLG11", name = "Pátria Logística FII",
                assetClass = AssetClass.FII.name, quantity = 45.0, averagePrice = 158.00,
                currentPrice = 156.40, hasMarketPrice = true, sector = "Logística", dataComDay = 30,
                dataSourceLabel = "Demonstração Fictícia", isDemo = true
            ),
            PortfolioPositionEntity(
                portfolioType = "REAL", ticker = "KNCA11", name = "Kinea Crédito Agro FIAGRO",
                assetClass = AssetClass.FIAGRO.name, quantity = 60.0, averagePrice = 96.20,
                currentPrice = 98.50, hasMarketPrice = true, sector = "CRAs High Grade", dataComDay = 30,
                dataSourceLabel = "Demonstração Fictícia", isDemo = true
            ),
            PortfolioPositionEntity(
                portfolioType = "REAL", ticker = "IVVB11", name = "iShares S&P 500 ETF",
                assetClass = AssetClass.ETF.name, quantity = 14.0, averagePrice = 340.00,
                currentPrice = 382.50, hasMarketPrice = true, sector = "EUA / S&P 500", dataComDay = null,
                dataSourceLabel = "Demonstração Fictícia", isDemo = true
            ),
            PortfolioPositionEntity(
                portfolioType = "REAL", ticker = "SELIC29", name = "Tesouro Selic 2029",
                assetClass = AssetClass.RENDA_FIXA.name, quantity = 0.5, averagePrice = 14900.00,
                currentPrice = 15842.30, hasMarketPrice = true, sector = "Título Público Federal", dataComDay = null,
                dataSourceLabel = "Demonstração Fictícia", isDemo = true
            )
        )
        val history = ImportHistoryEntity(
            fileName = "carteira_demonstracao_ficticia.csv",
            fileType = "DEMO",
            recordCount = demoPositions.size,
            cashBalanceBrl = 4250.0,
            isActiveRealReference = true,
            isDemoData = true,
            notes = "Dados fictícios carregados manualmente para demonstração."
        )
        portfolioDao.replaceRealPortfolioFromImport(history, demoPositions)
        prefs.setCashRealBrl(4250.0)
    }

    suspend fun clearPortfolio(type: PortfolioType) {
        portfolioDao.clearPositionsByType(type.name)
        portfolioDao.clearOperationsByType(type.name)
        if (type == PortfolioType.REAL) {
            portfolioDao.clearActiveRealReference()
            prefs.setCashRealBrl(0.0)
        } else {
            prefs.setCashSimBrl(25000.0)
        }
    }

    suspend fun addManualPosition(
        portfolioType: PortfolioType,
        ticker: String,
        name: String,
        assetClass: AssetClass,
        quantity: Double,
        averagePrice: Double,
        currentPrice: Double?,
        sector: String,
        dataComDay: Int?
    ) {
        val cleanTicker = ticker.trim().uppercase()
        val hasPrice = currentPrice != null && currentPrice > 0.0
        portfolioDao.insertPosition(
            PortfolioPositionEntity(
                portfolioType = portfolioType.name,
                ticker = cleanTicker,
                name = name.ifBlank { cleanTicker },
                assetClass = assetClass.name,
                quantity = quantity,
                averagePrice = averagePrice,
                currentPrice = currentPrice ?: 0.0,
                hasMarketPrice = hasPrice,
                sector = sector.ifBlank { assetClass.shortLabel },
                dataComDay = dataComDay,
                dataSourceLabel = "Registro Manual (${portfolioType.label})",
                isDemo = false
            )
        )
        val today = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())
        portfolioDao.insertOperation(
            PortfolioOperationEntity(
                portfolioType = portfolioType.name,
                ticker = cleanTicker,
                operationType = OperationType.COMPRA.name,
                quantity = quantity,
                unitPrice = averagePrice,
                totalValue = quantity * averagePrice,
                dateIso = today,
                notes = "Registro manual"
            )
        )
    }

    suspend fun syncProfitChartExecutions(
        executions: List<com.example.domain.model.ProfitChartExecution>,
        skippedNonExecutedCount: Int,
        applyToPositions: Boolean
    ): com.example.domain.model.ProfitChartSyncSummary {
        val now = System.currentTimeMillis()
        if (executions.isEmpty()) {
            val msg = if (skippedNonExecutedCount > 0) {
                "Nenhuma ordem executada nova ($skippedNonExecutedCount ordem(ns) não executada(s) ignorada(s))."
            } else {
                "Nenhuma operação executada encontrada na resposta do ProfitChart."
            }
            prefs.updateProfitSyncStatus(now, msg)
            return com.example.domain.model.ProfitChartSyncSummary(
                totalReceived = 0,
                newImportedCount = 0,
                skippedDuplicatesCount = 0,
                skippedNonExecutedCount = skippedNonExecutedCount,
                updatedPositionsCount = 0,
                syncedAtMillis = now,
                message = msg
            )
        }

        val existingRefs = portfolioDao.getExistingExternalRefIds().toHashSet()
        val newExecutions = executions.filter { it.externalRefId !in existingRefs }
        val duplicates = executions.size - newExecutions.size

        if (newExecutions.isNotEmpty()) {
            val opEntities = newExecutions.map { exec ->
                val brokerPrefix = if (exec.brokerAccount.isNotBlank()) "${exec.brokerAccount} • " else ""
                PortfolioOperationEntity(
                    portfolioType = PortfolioType.REAL.name,
                    ticker = exec.ticker,
                    operationType = exec.operationType.name,
                    quantity = exec.quantity,
                    unitPrice = exec.unitPrice,
                    totalValue = exec.totalValue,
                    dateIso = exec.executedAtLabel,
                    notes = "$brokerPrefix${exec.sourceChannel} (${exec.statusLabel})",
                    externalRefId = exec.externalRefId,
                    brokerAccount = exec.brokerAccount
                )
            }
            portfolioDao.insertOperations(opEntities)
        }

        var updatedPositionsCount = 0
        if (applyToPositions && newExecutions.isNotEmpty()) {
            val currentReal = portfolioDao.getPositionsByType(PortfolioType.REAL.name)
                .associateBy { it.ticker.uppercase() }
                .toMutableMap()

            for (exec in newExecutions) {
                val existing = currentReal[exec.ticker.uppercase()]
                when (exec.operationType) {
                    OperationType.COMPRA -> {
                        if (existing == null) {
                            val assetClass = LiveQuoteParsers.inferAssetClassFromTicker(exec.ticker)
                            val inserted = PortfolioPositionEntity(
                                portfolioType = PortfolioType.REAL.name,
                                ticker = exec.ticker,
                                name = exec.ticker,
                                assetClass = assetClass.name,
                                quantity = exec.quantity,
                                averagePrice = exec.unitPrice,
                                currentPrice = exec.unitPrice,
                                hasMarketPrice = true,
                                sector = assetClass.shortLabel,
                                dataComDay = null,
                                dataSourceLabel = "ProfitChart (${exec.brokerAccount.ifBlank { "Execução" }})",
                                isDemo = false
                            )
                            val newId = portfolioDao.insertPosition(inserted)
                            currentReal[exec.ticker.uppercase()] = inserted.copy(id = newId)
                        } else {
                            val totalQty = existing.quantity + exec.quantity
                            val weightedAvg = if (totalQty > 0.0) {
                                ((existing.quantity * existing.averagePrice) + (exec.quantity * exec.unitPrice)) / totalQty
                            } else {
                                exec.unitPrice
                            }
                            portfolioDao.updatePositionById(
                                id = existing.id,
                                ticker = existing.ticker,
                                name = existing.name,
                                assetClass = existing.assetClass,
                                quantity = totalQty,
                                averagePrice = weightedAvg,
                                currentPrice = exec.unitPrice,
                                hasMarketPrice = true,
                                sector = existing.sector,
                                dataComDay = existing.dataComDay,
                                dataSourceLabel = "ProfitChart Sync",
                                updatedAt = now
                            )
                            currentReal[exec.ticker.uppercase()] = existing.copy(
                                quantity = totalQty,
                                averagePrice = weightedAvg,
                                currentPrice = exec.unitPrice,
                                hasMarketPrice = true
                            )
                        }
                        updatedPositionsCount++
                    }

                    OperationType.VENDA -> {
                        if (existing != null) {
                            val remainingQty = (existing.quantity - exec.quantity).coerceAtLeast(0.0)
                            if (remainingQty <= 0.0001) {
                                portfolioDao.deletePositionById(existing.id)
                                currentReal.remove(exec.ticker.uppercase())
                            } else {
                                portfolioDao.updatePositionById(
                                    id = existing.id,
                                    ticker = existing.ticker,
                                    name = existing.name,
                                    assetClass = existing.assetClass,
                                    quantity = remainingQty,
                                    averagePrice = existing.averagePrice,
                                    currentPrice = exec.unitPrice,
                                    hasMarketPrice = true,
                                    sector = existing.sector,
                                    dataComDay = existing.dataComDay,
                                    dataSourceLabel = "ProfitChart Sync",
                                    updatedAt = now
                                )
                                currentReal[exec.ticker.uppercase()] = existing.copy(
                                    quantity = remainingQty,
                                    currentPrice = exec.unitPrice,
                                    hasMarketPrice = true
                                )
                            }
                            updatedPositionsCount++
                        }
                    }

                    else -> Unit
                }
            }
        }

        val msg = when {
            newExecutions.isNotEmpty() ->
                "${newExecutions.size} operação(ões) executada(s) sincronizada(s) do ProfitChart" +
                    if (updatedPositionsCount > 0) " ($updatedPositionsCount posição(ões) atualizada(s))." else "."
            else -> "Nenhuma operação nova ($duplicates já sincronizada(s))."
        }
        prefs.updateProfitSyncStatus(now, msg)

        return com.example.domain.model.ProfitChartSyncSummary(
            totalReceived = executions.size,
            newImportedCount = newExecutions.size,
            skippedDuplicatesCount = duplicates,
            skippedNonExecutedCount = skippedNonExecutedCount,
            updatedPositionsCount = updatedPositionsCount,
            syncedAtMillis = now,
            message = msg
        )
    }

    suspend fun recordOperation(
        portfolioType: PortfolioType,
        ticker: String,
        operationType: OperationType,
        quantity: Double,
        unitPrice: Double,
        notes: String
    ) {
        val total = quantity * unitPrice
        val today = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())
        portfolioDao.insertOperation(
            PortfolioOperationEntity(
                portfolioType = portfolioType.name,
                ticker = ticker.trim().uppercase().ifBlank { "CAIXA" },
                operationType = operationType.name,
                quantity = quantity,
                unitPrice = unitPrice,
                totalValue = total,
                dateIso = today,
                notes = notes
            )
        )
    }

    suspend fun updatePosition(
        id: Long,
        ticker: String,
        name: String,
        assetClass: AssetClass,
        quantity: Double,
        averagePrice: Double,
        currentPrice: Double?,
        sector: String,
        dataComDay: Int?,
        existingSourceLabel: String
    ) {
        val cleanTicker = ticker.trim().uppercase()
        val hasPrice = currentPrice != null && currentPrice > 0.0
        val updatedSource = when {
            existingSourceLabel.contains("editado", ignoreCase = true) -> existingSourceLabel
            existingSourceLabel.isBlank() -> "Edição Manual"
            else -> "$existingSourceLabel (editado)"
        }
        portfolioDao.updatePositionById(
            id = id,
            ticker = cleanTicker,
            name = name.trim().ifBlank { cleanTicker },
            assetClass = assetClass.name,
            quantity = quantity,
            averagePrice = averagePrice,
            currentPrice = if (hasPrice) currentPrice!! else 0.0,
            hasMarketPrice = hasPrice,
            sector = sector.trim().ifBlank { assetClass.shortLabel },
            dataComDay = dataComDay?.takeIf { it in 1..31 },
            dataSourceLabel = updatedSource,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun applyLivePriceToPortfolio(ticker: String, livePrice: Double): Int {
        if (livePrice <= 0.0) return 0
        return portfolioDao.updateCurrentPriceByTicker(
            ticker = ticker.trim().uppercase(),
            currentPrice = livePrice,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun deletePosition(id: Long) = portfolioDao.deletePositionById(id)

    suspend fun updateCashBalance(type: PortfolioType, amount: Double) {
        if (type == PortfolioType.REAL) prefs.setCashRealBrl(amount)
        else prefs.setCashSimBrl(amount)
    }

    suspend fun setAiConsent(granted: Boolean) = prefs.setAiPortfolioConsent(granted)

    suspend fun setFutureIntegrationMeta(supabaseUrl: String, githubRepo: String) =
        prefs.setFutureIntegrationMeta(supabaseUrl, githubRepo)

    suspend fun updateStrategyCriteria(
        oldCriteria: StrategyCriteriaEntity,
        newCriteria: StrategyCriteriaEntity,
        reason: String
    ) {
        strategyDao.upsertCriteria(newCriteria.copy(id = 1, updatedAt = System.currentTimeMillis()))
        val summary = reason.ifBlank {
            "DY mín ${"%.1f".format(newCriteria.minDyPercent)}% • P/VP máx ${"%.2f".format(newCriteria.maxPvp)}x • Liquidez mín R$ ${"%.1f".format(newCriteria.minDailyLiquidityMillions)}M"
        }
        val details = "Antes: DY>=${oldCriteria.minDyPercent}%, P/VP<=${oldCriteria.maxPvp}, Liq>=${oldCriteria.minDailyLiquidityMillions}M, Inad<=${oldCriteria.maxInadimplenciaPercent}% | " +
            "Depois: DY>=${newCriteria.minDyPercent}%, P/VP<=${newCriteria.maxPvp}, Liq>=${newCriteria.minDailyLiquidityMillions}M, Inad<=${newCriteria.maxInadimplenciaPercent}%"
        strategyDao.insertChangeLog(
            StrategyChangeLogEntity(summary = summary, details = details)
        )
    }

    suspend fun saveAiRecommendation(rec: AiRecommendation) {
        aiDao.insert(
            SavedAiRecommendationEntity(
                agentRole = rec.agentRole.name,
                ticker = rec.ticker,
                conclusion = rec.conclusao.label,
                confidence = rec.nivelConfianca,
                thesis = rec.tese,
                usedPersonalData = rec.usedPersonalPortfolioData
            )
        )
    }

    suspend fun getActivePriceTargetAlerts(): List<PriceTargetAlertEntity> = alertDao.getActiveAlerts()

    suspend fun insertPriceTargetAlert(alert: PriceTargetAlertEntity): Long = alertDao.insert(alert)

    suspend fun setPriceTargetAlertEnabled(id: Long, enabled: Boolean) = alertDao.setEnabled(id, enabled)

    suspend fun rearmPriceTargetAlert(id: Long) = alertDao.rearmAlert(id)

    suspend fun markPriceTargetAlertTriggered(id: Long, evaluatedPriceBrl: Double, triggeredAt: Long) =
        alertDao.markTriggered(id, evaluatedPriceBrl, triggeredAt)

    suspend fun updatePriceTargetAlertEvaluated(id: Long, evaluatedPriceBrl: Double, evaluatedAt: Long) =
        alertDao.updateLastEvaluated(id, evaluatedPriceBrl, evaluatedAt)

    suspend fun deletePriceTargetAlert(id: Long) = alertDao.deleteById(id)
}

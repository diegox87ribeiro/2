package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.importexport.ImportParseResult
import com.example.data.importexport.SpreadsheetImporter
import com.example.data.local.AiRecommendationDao
import com.example.data.local.AlertNotificationHistoryEntity
import com.example.data.local.AppPreferencesState
import com.example.data.local.AssetSwingAlertEntity
import com.example.data.local.Ativo
import com.example.data.local.AtivoComTransacoes
import com.example.data.local.CachedMarketQuoteDao
import com.example.data.local.CachedMarketQuoteEntity
import com.example.data.local.CarteiraLocalDao
import com.example.data.local.CryptoVirtualDao
import com.example.data.local.CryptoVirtualPositionEntity
import com.example.data.local.ImportHistoryEntity
import com.example.data.local.InvestDatabase
import com.example.data.local.InvestmentGoalEntity
import com.example.data.local.PortfolioDao
import com.example.data.local.PortfolioOperationEntity
import com.example.data.local.PortfolioPositionEntity
import com.example.data.local.PreferencesManager
import com.example.data.local.PriceTargetAlertDao
import com.example.data.local.PriceTargetAlertEntity
import com.example.data.local.SavedAiRecommendationEntity
import com.example.data.local.SmartAlertDao
import com.example.data.local.StrategyChangeLogEntity
import com.example.data.local.StrategyCriteriaEntity
import com.example.data.local.StrategyDao
import com.example.data.local.Transacao
import com.example.domain.model.AiRecommendation
import com.example.domain.model.AssetClass
import com.example.domain.model.LiveMarketQuote
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
    private val carteiraLocalDao: CarteiraLocalDao = db.carteiraLocalDao()
    private val strategyDao: StrategyDao = db.strategyDao()
    private val cryptoDao: CryptoVirtualDao = db.cryptoVirtualDao()
    private val aiDao: AiRecommendationDao = db.aiRecommendationDao()
    private val alertDao: PriceTargetAlertDao = db.priceTargetAlertDao()
    private val cachedQuoteDao: CachedMarketQuoteDao = db.cachedMarketQuoteDao()
    private val smartAlertDao: SmartAlertDao = db.smartAlertDao()
    private val dividendEventDao: com.example.data.local.DividendEventDao = db.dividendEventDao()

    private val dividendHelper = DividendManagementHelper(
        portfolioDao = portfolioDao,
        dividendEventDao = dividendEventDao,
        onMirrorRealPositions = {
            mirrorRealPositionsToAtivos(portfolioDao.getPositionsByType(PortfolioType.REAL.name))
        },
        onMirrorRealOperation = { op -> mirrorRealOperationToTransacao(op) }
    )

    val carteiraLocalRepository: CarteiraLocalRepository = CarteiraLocalRepository(carteiraLocalDao)

    val preferencesFlow: Flow<AppPreferencesState> = prefs.preferencesFlow
    val ativosLocaisFlow: Flow<List<Ativo>> = carteiraLocalRepository.ativosFlow
    val transacoesLocaisFlow: Flow<List<Transacao>> = carteiraLocalRepository.transacoesFlow
    val ativosComTransacoesFlow: Flow<List<AtivoComTransacoes>> = carteiraLocalRepository.ativosComTransacoesFlow
    val dividendEventsFlow: Flow<List<com.example.data.local.DividendEventEntity>> = dividendEventDao.observeAllDividendEvents()
    val financialAuditLogsFlow: Flow<List<com.example.data.local.FinancialAuditLogEntity>> = dividendEventDao.observeAuditLogs()

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
                hasMarketPrice = entity.hasMarketPrice,
                isCyclical = entity.isCyclical
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
    val swingAlertsFlow: Flow<List<AssetSwingAlertEntity>> = smartAlertDao.observeSwingAlerts()
    val investmentGoalsFlow: Flow<List<InvestmentGoalEntity>> = smartAlertDao.observeInvestmentGoals()
    val alertHistoryFlow: Flow<List<AlertNotificationHistoryEntity>> = smartAlertDao.observeNotificationHistory()
    val cachedLiveQuotesFlow: Flow<Map<String, LiveMarketQuote>> = cachedQuoteDao.observeAll().map { entities ->
        entities.associate { e ->
            val upper = e.ticker.uppercase()
            val inferred = LiveQuoteParsers.inferAssetClassFromTicker(upper)
            val pathSegment = when (inferred) {
                AssetClass.FII -> "fiis"
                AssetClass.FIAGRO -> "fiagros"
                AssetClass.ETF -> "etfs"
                else -> "acoes"
            }
            val trustedDataComSource = e.primarySourceLabel.contains("Investidor10", ignoreCase = true) ||
                e.primarySourceLabel.contains("Brapi", ignoreCase = true) ||
                e.primarySourceLabel.contains("B3", ignoreCase = true)
            val sanitizedDataCom = e.dataComDay?.takeIf { day ->
                day in 1..31 && (trustedDataComSource || day !in 1..3)
            }
            upper to LiveMarketQuote(
                ticker = upper,
                companyName = e.companyName,
                price = e.price,
                changePercent1d = e.changePercent1d,
                dailyVolumeBrlMillions = e.dailyVolumeBrlMillions,
                priceToEarnings = e.pl?.takeIf { it in 0.1..500.0 },
                priceToBook = e.pvp?.takeIf { it in 0.05..30.0 },
                dividendYield12m = e.dy12m?.takeIf { it in 0.01..80.0 },
                roePercent = e.roe?.takeIf { it in -200.0..200.0 },
                lastDividendBrl = e.lastDividendBrl,
                dataComDay = sanitizedDataCom,
                paymentDay = e.paymentDay,
                primarySourceLabel = "${e.primarySourceLabel} (Cache Room)",
                consultedSources = listOf("Cache Room"),
                fetchedAtMillis = e.fetchedAtMillis,
                marketTimestampLabel = e.marketTimestampLabel,
                b3PageUrl = "https://www.b3.com.br/pt_br/market-data-e-indices/servicos-de-dados/market-data/cotacoes/cotacoes/",
                advfnPageUrl = "https://br.advfn.com/p.php?pid=qkquote&symbol=BOV%3A$upper",
                investidor10PageUrl = "https://investidor10.com.br/$pathSegment/${upper.lowercase()}/"
            )
        }
    }

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

    suspend fun commitRealPortfolioImport(result: ImportParseResult): Int {
        if (result.positions.isEmpty() && result.dividends.isEmpty() && result.quickUpdates.isEmpty()) return 0
        var updatedPositionsCount = 0

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
            mirrorRealPositionsToAtivos(result.positions)
            updatedPositionsCount = result.positions.size
        } else if (result.quickUpdates.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val today = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(now))
            val existingReal = portfolioDao.getPositionsByType(PortfolioType.REAL.name)
            val byTicker = existingReal.groupBy { it.ticker.trim().uppercase() }
            val updatedTickers = linkedSetOf<String>()

            result.quickUpdates.forEach { upd ->
                val cleanTicker = upd.ticker.trim().uppercase()
                val matches = byTicker[cleanTicker].orEmpty()
                val dateTag = upd.referenceDate?.takeIf { it.isNotBlank() } ?: today
                matches.forEach { pos ->
                    val newPrice = upd.price?.takeIf { it > 0.0 } ?: pos.currentPrice
                    val newHasPrice = (upd.price != null && upd.price > 0.0) || pos.hasMarketPrice
                    val newDataCom = upd.dataComDay?.takeIf { it in 1..31 } ?: pos.dataComDay
                    val changedPriceOrDataCom = (upd.price != null && upd.price > 0.0) || (upd.dataComDay != null)
                    if (changedPriceOrDataCom) {
                        portfolioDao.updatePositionById(
                            id = pos.id,
                            ticker = pos.ticker,
                            name = pos.name,
                            assetClass = pos.assetClass,
                            quantity = pos.quantity,
                            averagePrice = pos.averagePrice,
                            currentPrice = newPrice,
                            hasMarketPrice = newHasPrice,
                            sector = pos.sector,
                            dataComDay = newDataCom,
                            dataSourceLabel = "Atualização ${result.fileType} ($dateTag)",
                            updatedAt = now,
                            isCyclical = pos.isCyclical
                        )
                        updatedTickers.add(cleanTicker)
                    }
                }
                if (upd.price != null && upd.price > 0.0) {
                    carteiraLocalRepository.atualizarCotacao(cleanTicker, upd.price)
                }
                if (upd.dataComDay != null && upd.dataComDay in 1..31) {
                    carteiraLocalDao.atualizarDatasDividendoSeAusente(
                        ticker = cleanTicker,
                        dataComDia = upd.dataComDay,
                        dataExDia = null,
                        atualizadoEm = now
                    )
                }
            }

            if (updatedTickers.isNotEmpty()) {
                val refreshed = portfolioDao.getPositionsByType(PortfolioType.REAL.name)
                mirrorRealPositionsToAtivos(refreshed)
            }
            updatedPositionsCount = updatedTickers.size

            val history = ImportHistoryEntity(
                fileName = result.fileName,
                fileType = "${result.fileType} (Atualização)",
                recordCount = updatedPositionsCount.coerceAtLeast(result.quickUpdates.size),
                cashBalanceBrl = result.cashBalanceBrl ?: 0.0,
                isActiveRealReference = false,
                isDemoData = false,
                notes = "Atualização rápida: $updatedPositionsCount ativo(s) atualizado(s) e ${result.dividends.size} provento(s) importado(s)."
            )
            portfolioDao.insertImportHistory(history)
        }

        if (result.dividends.isNotEmpty()) {
            val existingRefs = portfolioDao.getExistingExternalRefIds().toHashSet()
            val newDivs = result.dividends.filter { it.externalRefId.isBlank() || it.externalRefId !in existingRefs }
            if (newDivs.isNotEmpty()) {
                portfolioDao.insertOperations(newDivs)
                newDivs.forEach { mirrorRealOperationToTransacao(it) }
            }
        }
        result.cashBalanceBrl?.let { prefs.setCashRealBrl(it) }
        return updatedPositionsCount
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
                currentPrice = 35.80, hasMarketPrice = true, sector = "Bancos", dataComDay = 30,
                isCyclical = true, dataSourceLabel = "Demonstração Fictícia", isDemo = true
            ),
            PortfolioPositionEntity(
                portfolioType = "REAL", ticker = "HGLG11", name = "Pátria Logística FII",
                assetClass = AssetClass.FII.name, quantity = 45.0, averagePrice = 158.00,
                currentPrice = 156.40, hasMarketPrice = true, sector = "Logística", dataComDay = 30,
                isCyclical = true, dataSourceLabel = "Demonstração Fictícia", isDemo = true
            ),
            PortfolioPositionEntity(
                portfolioType = "REAL", ticker = "KNCA11", name = "Kinea Crédito Agro FIAGRO",
                assetClass = AssetClass.FIAGRO.name, quantity = 60.0, averagePrice = 96.20,
                currentPrice = 98.50, hasMarketPrice = true, sector = "CRAs High Grade", dataComDay = 30,
                isCyclical = true, dataSourceLabel = "Demonstração Fictícia", isDemo = true
            ),
            PortfolioPositionEntity(
                portfolioType = "REAL", ticker = "IVVB11", name = "iShares S&P 500 ETF",
                assetClass = AssetClass.ETF.name, quantity = 14.0, averagePrice = 340.00,
                currentPrice = 382.50, hasMarketPrice = true, sector = "EUA / S&P 500", dataComDay = null,
                isCyclical = false, dataSourceLabel = "Demonstração Fictícia", isDemo = true
            ),
            PortfolioPositionEntity(
                portfolioType = "REAL", ticker = "SELIC29", name = "Tesouro Selic 2029",
                assetClass = AssetClass.RENDA_FIXA.name, quantity = 0.5, averagePrice = 14900.00,
                currentPrice = 15842.30, hasMarketPrice = true, sector = "Título Público Federal", dataComDay = null,
                isCyclical = false, dataSourceLabel = "Demonstração Fictícia", isDemo = true
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
        mirrorRealPositionsToAtivos(demoPositions)
        prefs.setCashRealBrl(4250.0)
    }

    suspend fun clearPortfolio(type: PortfolioType) {
        portfolioDao.clearPositionsByType(type.name)
        portfolioDao.clearOperationsByType(type.name)
        if (type == PortfolioType.REAL) {
            portfolioDao.clearActiveRealReference()
            carteiraLocalRepository.limparCarteiraLocal()
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
        dataComDay: Int?,
        isCyclical: Boolean = false
    ) {
        val cleanTicker = ticker.trim().uppercase()
        val hasPrice = currentPrice != null && currentPrice > 0.0
        val positionEntity = PortfolioPositionEntity(
            portfolioType = portfolioType.name,
            ticker = cleanTicker,
            name = name.ifBlank { cleanTicker },
            assetClass = assetClass.name,
            quantity = quantity,
            averagePrice = averagePrice,
            currentPrice = currentPrice ?: 0.0,
            hasMarketPrice = hasPrice,
            sector = sector.ifBlank { assetClass.shortLabel },
            dataComDay = if (isCyclical) dataComDay else null,
            isCyclical = isCyclical,
            dataSourceLabel = "Registro Manual (${portfolioType.label})",
            isDemo = false
        )
        portfolioDao.insertPosition(positionEntity)
        val today = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())
        val opEntity = PortfolioOperationEntity(
            portfolioType = portfolioType.name,
            ticker = cleanTicker,
            operationType = OperationType.COMPRA.name,
            quantity = quantity,
            unitPrice = averagePrice,
            totalValue = quantity * averagePrice,
            dateIso = today,
            notes = "Registro manual"
        )
        portfolioDao.insertOperation(opEntity)
        if (portfolioType == PortfolioType.REAL) {
            mirrorRealPositionsToAtivos(listOf(positionEntity))
            mirrorRealOperationToTransacao(opEntity)
        }
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
                                isCyclical = false,
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
                                updatedAt = now,
                                isCyclical = existing.isCyclical
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
                                    updatedAt = now,
                                    isCyclical = existing.isCyclical
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
            mirrorRealPositionsToAtivos(currentReal.values.toList())
        }
        if (newExecutions.isNotEmpty()) {
            newExecutions.forEach { exec ->
                val brokerPrefix = if (exec.brokerAccount.isNotBlank()) "${exec.brokerAccount} • " else ""
                mirrorRealOperationToTransacao(
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
                )
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

    suspend fun commitRecognizedScreenshotOrders(
        portfolioType: PortfolioType,
        orders: List<com.example.domain.model.RecognizedOrderDraft>,
        applyToPositions: Boolean
    ): com.example.domain.model.OrderScreenshotLaunchSummary {
        val now = System.currentTimeMillis()
        val today = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(now))
        val validOrders = orders.filter { it.isReadyToLaunch }
        if (validOrders.isEmpty()) {
            return com.example.domain.model.OrderScreenshotLaunchSummary(
                launchedCount = 0,
                skippedDuplicatesCount = 0,
                updatedPositionsCount = 0,
                message = "Nenhuma ordem válida selecionada para lançamento."
            )
        }

        val existingRefs = portfolioDao.getExistingExternalRefIds().toHashSet()
        val newOrders = validOrders.filter { it.buildExternalRefId() !in existingRefs }
        val duplicates = validOrders.size - newOrders.size

        if (newOrders.isEmpty()) {
            return com.example.domain.model.OrderScreenshotLaunchSummary(
                launchedCount = 0,
                skippedDuplicatesCount = duplicates,
                updatedPositionsCount = 0,
                message = "Todas as $duplicates ordem(ns) selecionadas desta nota/print já foram lançadas anteriormente."
            )
        }

        val opEntities = newOrders.map { order ->
            val cleanTicker = order.ticker.trim().uppercase()
            val total = order.computedTotalValue
            val resolvedDate = order.dateIso.trim().ifBlank { today }
            val isBrokerageNote = order.noteNumber.isNotBlank() ||
                order.specificationTitle.isNotBlank() ||
                order.isFuturesContract
            val originLabel = if (isBrokerageNote) "Nota Corretagem IA" else "Print IA"
            val detailNotes = buildList {
                if (order.brokerLabel.isNotBlank()) add(order.brokerLabel)
                add(originLabel)
                if (order.noteNumber.isNotBlank()) add("Nota #${order.noteNumber}")
                if (order.isFuturesContract) add("BM&F")
                if (order.isDayTrade) add("Day Trade")
                if (order.orderTypeLabel.isNotBlank() && !order.orderTypeLabel.equals("Day Trade", ignoreCase = true)) {
                    add(order.orderTypeLabel)
                }
                if (order.timeLabel.isNotBlank()) add(order.timeLabel)
                if (order.feesBrl > 0.0) {
                    add("Taxas R$ ${String.format(Locale("pt", "BR"), "%.2f", order.feesBrl)}")
                }
                if (order.notes.isNotBlank()) add(order.notes)
            }.joinToString(" • ")

            PortfolioOperationEntity(
                portfolioType = portfolioType.name,
                ticker = cleanTicker,
                operationType = order.operationType.name,
                quantity = order.quantity,
                unitPrice = order.unitPrice,
                totalValue = total,
                dateIso = resolvedDate,
                notes = detailNotes,
                externalRefId = order.buildExternalRefId(),
                brokerAccount = order.brokerLabel
            )
        }
        portfolioDao.insertOperations(opEntities)

        var updatedPositionsCount = 0
        var futuresLoggedOnlyCount = 0
        if (applyToPositions) {
            val currentMap = portfolioDao.getPositionsByType(portfolioType.name)
                .associateBy { it.ticker.trim().uppercase() }
                .toMutableMap()

            for (order in newOrders) {
                if (order.isFuturesContract) {
                    // Contratos futuros BM&F (ex.: WINZ26, WDO) são cotados em pontos e liquidados por ajuste diário,
                    // portanto ficam registrados no histórico de operações sem distorcer a custódia à vista.
                    futuresLoggedOnlyCount++
                    continue
                }
                val cleanTicker = order.ticker.trim().uppercase()
                val existing = currentMap[cleanTicker]
                val isBrokerageNote = order.noteNumber.isNotBlank() || order.specificationTitle.isNotBlank()
                val originTag = if (isBrokerageNote) "Nota IA" else "Print IA"
                val sourceTag = if (order.brokerLabel.isNotBlank()) {
                    "$originTag (${order.brokerLabel})"
                } else {
                    "$originTag (${portfolioType.label})"
                }

                when (order.operationType) {
                    OperationType.COMPRA -> {
                        if (existing == null) {
                            val assetClass = LiveQuoteParsers.inferAssetClassFromTicker(cleanTicker)
                            val inserted = PortfolioPositionEntity(
                                portfolioType = portfolioType.name,
                                ticker = cleanTicker,
                                name = order.specificationTitle.ifBlank { cleanTicker },
                                assetClass = assetClass.name,
                                quantity = order.quantity,
                                averagePrice = order.unitPrice,
                                currentPrice = order.unitPrice,
                                hasMarketPrice = true,
                                sector = assetClass.shortLabel,
                                dataComDay = null,
                                isCyclical = false,
                                dataSourceLabel = sourceTag,
                                isDemo = false
                            )
                            val newId = portfolioDao.insertPosition(inserted)
                            currentMap[cleanTicker] = inserted.copy(id = newId)
                        } else {
                            val totalQty = existing.quantity + order.quantity
                            val weightedAvg = if (totalQty > 0.0) {
                                ((existing.quantity * existing.averagePrice) + (order.quantity * order.unitPrice)) / totalQty
                            } else {
                                order.unitPrice
                            }
                            portfolioDao.updatePositionById(
                                id = existing.id,
                                ticker = existing.ticker,
                                name = existing.name,
                                assetClass = existing.assetClass,
                                quantity = totalQty,
                                averagePrice = weightedAvg,
                                currentPrice = order.unitPrice,
                                hasMarketPrice = true,
                                sector = existing.sector,
                                dataComDay = existing.dataComDay,
                                dataSourceLabel = sourceTag,
                                updatedAt = now,
                                isCyclical = existing.isCyclical
                            )
                            currentMap[cleanTicker] = existing.copy(
                                quantity = totalQty,
                                averagePrice = weightedAvg,
                                currentPrice = order.unitPrice,
                                hasMarketPrice = true
                            )
                        }
                        updatedPositionsCount++
                    }

                    OperationType.VENDA -> {
                        if (existing != null) {
                            val remainingQty = (existing.quantity - order.quantity).coerceAtLeast(0.0)
                            if (remainingQty <= 0.0001) {
                                portfolioDao.deletePositionById(existing.id)
                                currentMap.remove(cleanTicker)
                                if (portfolioType == PortfolioType.REAL) {
                                    carteiraLocalRepository.excluirAtivoPorTicker(cleanTicker)
                                }
                            } else {
                                portfolioDao.updatePositionById(
                                    id = existing.id,
                                    ticker = existing.ticker,
                                    name = existing.name,
                                    assetClass = existing.assetClass,
                                    quantity = remainingQty,
                                    averagePrice = existing.averagePrice,
                                    currentPrice = order.unitPrice,
                                    hasMarketPrice = true,
                                    sector = existing.sector,
                                    dataComDay = existing.dataComDay,
                                    dataSourceLabel = sourceTag,
                                    updatedAt = now,
                                    isCyclical = existing.isCyclical
                                )
                                currentMap[cleanTicker] = existing.copy(
                                    quantity = remainingQty,
                                    currentPrice = order.unitPrice,
                                    hasMarketPrice = true
                                )
                            }
                            updatedPositionsCount++
                        }
                    }

                    else -> Unit
                }
            }
            if (portfolioType == PortfolioType.REAL) {
                mirrorRealPositionsToAtivos(currentMap.values.toList())
            }
        }

        if (portfolioType == PortfolioType.REAL) {
            opEntities.forEach { mirrorRealOperationToTransacao(it) }
        }

        val posSuffix = if (updatedPositionsCount > 0) " e $updatedPositionsCount posição(ões) atualizada(s)" else ""
        val bmfSuffix = if (futuresLoggedOnlyCount > 0) " • $futuresLoggedOnlyCount operação(ões) BM&F registrada(s) no histórico" else ""
        val dupSuffix = if (duplicates > 0) " ($duplicates duplicada(s) ignorada(s))" else ""
        val msg = "${newOrders.size} ordem(ns) lançada(s) na ${portfolioType.label}$posSuffix$bmfSuffix$dupSuffix."

        return com.example.domain.model.OrderScreenshotLaunchSummary(
            launchedCount = newOrders.size,
            skippedDuplicatesCount = duplicates,
            updatedPositionsCount = updatedPositionsCount,
            message = msg
        )
    }

    suspend fun recordOperation(
        portfolioType: PortfolioType,
        ticker: String,
        operationType: OperationType,
        quantity: Double,
        unitPrice: Double,
        notes: String,
        dateIso: String? = null
    ) {
        val total = quantity * unitPrice
        val resolvedDate = dateIso?.trim()?.takeIf { it.isNotBlank() }
            ?: SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())
        val opEntity = PortfolioOperationEntity(
            portfolioType = portfolioType.name,
            ticker = ticker.trim().uppercase().ifBlank { "CAIXA" },
            operationType = operationType.name,
            quantity = quantity,
            unitPrice = unitPrice,
            totalValue = total,
            dateIso = resolvedDate,
            notes = notes
        )
        val opId = portfolioDao.insertOperation(opEntity)
        if (portfolioType == PortfolioType.REAL) {
            mirrorRealOperationToTransacao(opEntity.copy(id = opId))
            if (operationType == OperationType.PROVENTO) {
                dividendHelper.mirrorLegacyProventoOperation(
                    opId = opId,
                    ticker = opEntity.ticker,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    totalValue = total,
                    dateIso = resolvedDate,
                    notes = notes
                )
            }
        }
    }

    suspend fun deleteOperation(id: Long) {
        dividendHelper.deleteOperationWithAudit(id)
    }

    suspend fun updateOperationWithRecalc(
        operationId: Long,
        ticker: String,
        operationType: OperationType,
        quantity: Double,
        unitPrice: Double,
        dateIso: String,
        notes: String
    ): Boolean = dividendHelper.updateOperationWithRecalc(
        operationId = operationId,
        ticker = ticker,
        operationType = operationType,
        quantity = quantity,
        unitPrice = unitPrice,
        dateIso = dateIso,
        notes = notes
    )

    suspend fun saveDividendEvent(
        id: Long?,
        ticker: String,
        eventType: com.example.domain.model.DividendEventType,
        status: com.example.domain.model.DividendStatus,
        exDate: String,
        paymentDate: String,
        unitAmount: Double,
        quantityReference: Double,
        totalAmount: Double,
        source: String,
        notes: String,
        isRecurring: Boolean,
        reason: String = ""
    ): Long = dividendHelper.saveDividendEvent(
        id = id,
        ticker = ticker,
        eventType = eventType,
        status = status,
        exDate = exDate,
        paymentDate = paymentDate,
        unitAmount = unitAmount,
        quantityReference = quantityReference,
        totalAmount = totalAmount,
        source = source,
        notes = notes,
        isRecurring = isRecurring,
        reason = reason
    )

    suspend fun transitionDividendStatus(
        eventId: Long,
        draftProjection: com.example.domain.model.DividendDisplayEvent?,
        targetStatus: com.example.domain.model.DividendStatus,
        reason: String = ""
    ): Long = dividendHelper.transitionDividendStatus(
        eventId = eventId,
        draftProjection = draftProjection,
        targetStatus = targetStatus,
        reason = reason
    )

    suspend fun softDeleteDividendEvent(eventId: Long, reason: String = "Exclusão pelo usuário"): Boolean =
        dividendHelper.softDeleteDividendEvent(eventId, reason)

    suspend fun restoreDividendEvent(eventId: Long): Boolean =
        dividendHelper.restoreDividendEvent(eventId)

    suspend fun renameDividendTicker(oldTicker: String, newTicker: String): Int =
        dividendHelper.renameDividendTicker(oldTicker, newTicker)

    suspend fun syncConfirmedDividendsFromQuotes(
        realPositions: List<PortfolioItemView>,
        quotesByTicker: Map<String, LiveMarketQuote>
    ): DividendSyncResult = dividendHelper.syncConfirmedDividendsFromQuotes(realPositions, quotesByTicker)

    suspend fun restoreFromAuditLog(auditId: Long): String =
        dividendHelper.restoreFromAuditLog(auditId)

    suspend fun updateDividendAutoSyncStatus(epochMs: Long, message: String) {
        prefs.updateDividendAutoSyncStatus(epochMs, message)
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
        existingSourceLabel: String,
        isCyclical: Boolean = true
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
            updatedAt = System.currentTimeMillis(),
            isCyclical = isCyclical
        )
        val realPositions = portfolioDao.getPositionsByType(PortfolioType.REAL.name)
        if (realPositions.any { it.id == id }) {
            mirrorRealPositionsToAtivos(realPositions)
        }
    }

    suspend fun applyLivePriceToPortfolio(
        ticker: String,
        livePrice: Double,
        liveDataComDay: Int? = null,
        liveDataExDay: Int? = null,
        isMonthlyDividendPayer: Boolean? = null
    ): Int {
        if (livePrice <= 0.0) return 0
        val cleanTicker = ticker.trim().uppercase()
        val now = System.currentTimeMillis()
        carteiraLocalRepository.atualizarCotacao(cleanTicker, livePrice)
        if (isMonthlyDividendPayer != null) {
            val validDay = liveDataComDay?.takeIf { it in 1..31 }
            applyMonthlyDividendClassification(
                ticker = cleanTicker,
                isMonthlyPayer = isMonthlyDividendPayer,
                dataComDay = if (isMonthlyDividendPayer) validDay else null
            )
            if (isMonthlyDividendPayer && (validDay != null || liveDataExDay != null)) {
                carteiraLocalDao.atualizarDatasDividendoSeAusente(
                    ticker = cleanTicker,
                    dataComDia = validDay,
                    dataExDia = liveDataExDay?.takeIf { it in 1..31 },
                    atualizadoEm = now
                )
            }
        }
        return portfolioDao.updateCurrentPriceByTicker(
            ticker = cleanTicker,
            currentPrice = livePrice,
            updatedAt = now
        )
    }

    suspend fun applyMonthlyDividendClassification(
        ticker: String,
        isMonthlyPayer: Boolean,
        dataComDay: Int?
    ): Int {
        val cleanTicker = ticker.trim().uppercase()
        val now = System.currentTimeMillis()
        val validDay = if (isMonthlyPayer) dataComDay?.takeIf { it in 1..31 } else null
        carteiraLocalDao.atualizarClassificacaoMensal(
            ticker = cleanTicker,
            ehMensal = isMonthlyPayer,
            dataComDia = validDay,
            atualizadoEm = now
        )
        return portfolioDao.updateMonthlyDividendClassificationByTicker(
            ticker = cleanTicker,
            isMonthlyPayer = isMonthlyPayer,
            dataComDay = validDay,
            updatedAt = now
        )
    }

    suspend fun deletePosition(id: Long) {
        val targetPos = portfolioDao.getPositionById(id)
        if (targetPos != null) {
            dividendHelper.recordPositionDeletionAudit(targetPos)
        }
        val targetReal = targetPos?.takeIf { it.portfolioType == PortfolioType.REAL.name }
        portfolioDao.deletePositionById(id)
        if (targetReal != null) {
            val stillExists = portfolioDao.getPositionsByType(PortfolioType.REAL.name)
                .any { it.ticker.equals(targetReal.ticker, ignoreCase = true) }
            if (!stillExists) {
                carteiraLocalRepository.excluirAtivoPorTicker(targetReal.ticker)
            }
        }
    }

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

    suspend fun getPendingNotificationAlerts(): List<PriceTargetAlertEntity> = alertDao.getPendingNotificationAlerts()

    suspend fun insertPriceTargetAlert(alert: PriceTargetAlertEntity): Long = alertDao.insert(alert)

    suspend fun setPriceTargetAlertEnabled(id: Long, enabled: Boolean) = alertDao.setEnabled(id, enabled)

    suspend fun rearmPriceTargetAlert(id: Long) = alertDao.rearmAlert(id)

    suspend fun markPriceTargetAlertTriggered(
        id: Long,
        evaluatedPriceBrl: Double,
        triggeredAt: Long,
        notificationDelivered: Boolean = true
    ) {
        val status = if (notificationDelivered) {
            "Meta atingida • Push entregue"
        } else {
            "Meta atingida • Push pendente"
        }
        alertDao.markTriggered(
            id = id,
            evaluatedPriceBrl = evaluatedPriceBrl,
            triggeredAt = triggeredAt,
            pendingNotification = !notificationDelivered,
            notificationDelivered = notificationDelivered,
            status = status
        )
    }

    suspend fun markPriceTargetAlertNotificationDelivered(id: Long) =
        alertDao.markNotificationDelivered(id)

    suspend fun markPriceTargetAlertsOfflineQueued(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            alertDao.markOfflineQueued(ids)
        }
    }

    suspend fun updatePriceTargetAlertEvaluated(
        id: Long,
        evaluatedPriceBrl: Double,
        evaluatedAt: Long,
        status: String = "Monitorando"
    ) = alertDao.updateLastEvaluated(id, evaluatedPriceBrl, evaluatedAt, status)

    suspend fun deletePriceTargetAlert(id: Long) = alertDao.deleteById(id)

    suspend fun saveCachedLiveQuotes(quotes: Collection<LiveMarketQuote>) {
        if (quotes.isEmpty()) return
        val entities = quotes.map { q ->
            CachedMarketQuoteEntity(
                ticker = q.ticker.trim().uppercase(),
                companyName = q.companyName,
                price = q.price,
                changePercent1d = q.changePercent1d,
                dy12m = q.dividendYield12m?.takeIf { it in 0.01..80.0 },
                pvp = q.priceToBook?.takeIf { it in 0.05..30.0 },
                pl = q.priceToEarnings?.takeIf { it in 0.1..500.0 },
                roe = q.roePercent?.takeIf { it in -200.0..200.0 },
                dailyVolumeBrlMillions = q.dailyVolumeBrlMillions,
                lastDividendBrl = q.lastDividendBrl,
                dataComDay = q.dataComDay?.takeIf { it in 1..31 },
                paymentDay = q.paymentDay?.takeIf { it in 1..31 },
                primarySourceLabel = q.primarySourceLabel,
                marketTimestampLabel = q.marketTimestampLabel,
                fetchedAtMillis = q.fetchedAtMillis
            )
        }
        cachedQuoteDao.upsertAll(entities)
    }

    suspend fun saveCachedLiveQuote(quote: LiveMarketQuote) {
        saveCachedLiveQuotes(listOf(quote))
    }

    suspend fun saveDividendDateAlertConfig(
        enabled: Boolean,
        leadDays: Int,
        notifyDataCom: Boolean,
        notifyDataEx: Boolean
    ) {
        prefs.saveDividendDateAlertConfig(
            enabled = enabled,
            leadDays = leadDays,
            notifyDataCom = notifyDataCom,
            notifyDataEx = notifyDataEx
        )
    }

    suspend fun recordSentDividendAlertKeys(newKeys: Collection<String>) {
        prefs.recordSentDividendAlertKeys(newKeys)
    }

    private fun PortfolioPositionEntity.toAtivo(): Ativo = Ativo(
        ticker = ticker.trim().uppercase(),
        nome = name.ifBlank { ticker.trim().uppercase() },
        classe = assetClass,
        setor = sector.ifBlank { "Geral" },
        quantidade = quantity,
        precoMedio = averagePrice,
        precoAtual = currentPrice,
        possuiCotacaoMercado = hasMarketPrice,
        dataComDia = dataComDay,
        ehCiclico = isCyclical,
        origemDados = dataSourceLabel.ifBlank { "Local" },
        atualizadoEm = updatedAt
    )

    private suspend fun mirrorRealPositionsToAtivos(positions: List<PortfolioPositionEntity>) {
        val ativos = positions
            .filter { it.portfolioType == PortfolioType.REAL.name && it.ticker.isNotBlank() }
            .map { it.toAtivo() }
        if (ativos.isNotEmpty()) {
            carteiraLocalRepository.sincronizarAtivos(ativos)
        }
    }

    private suspend fun mirrorRealOperationToTransacao(op: PortfolioOperationEntity) {
        val cleanTicker = op.ticker.trim().uppercase().ifBlank { "CAIXA" }
        val existingAtivo = carteiraLocalDao.buscarAtivoPorTicker(cleanTicker)
        if (existingAtivo == null) {
            val inferredClass = if (cleanTicker == "CAIXA") {
                AssetClass.RENDA_FIXA
            } else {
                LiveQuoteParsers.inferAssetClassFromTicker(cleanTicker)
            }
            carteiraLocalDao.inserirAtivo(
                Ativo(
                    ticker = cleanTicker,
                    nome = cleanTicker,
                    classe = inferredClass.name,
                    setor = inferredClass.shortLabel,
                    quantidade = 0.0,
                    precoMedio = op.unitPrice.coerceAtLeast(0.0),
                    precoAtual = op.unitPrice.coerceAtLeast(0.0),
                    possuiCotacaoMercado = op.unitPrice > 0.0,
                    origemDados = "Transação Local"
                )
            )
        }
        carteiraLocalDao.inserirTransacao(
            Transacao(
                ativoTicker = cleanTicker,
                tipo = op.operationType,
                quantidade = op.quantity,
                precoUnitario = op.unitPrice,
                valorTotal = op.totalValue,
                dataIso = op.dateIso,
                observacoes = op.notes,
                referenciaExterna = op.externalRefId,
                criadoEm = op.createdAt
            )
        )
    }

    // --- Smart Alerts (Variações Bruscas & Metas de Investimento) ---
    suspend fun getActiveSwingAlerts(): List<AssetSwingAlertEntity> = smartAlertDao.getActiveSwingAlerts()

    suspend fun insertSwingAlert(alert: AssetSwingAlertEntity): Long = smartAlertDao.insertSwingAlert(alert)

    suspend fun setSwingAlertEnabled(id: Long, enabled: Boolean) = smartAlertDao.setSwingAlertEnabled(id, enabled)

    suspend fun updateSwingLastEvaluated(id: Long, changePercent: Double, priceBrl: Double, evaluatedAt: Long) =
        smartAlertDao.updateSwingLastEvaluated(id, changePercent, priceBrl, evaluatedAt)

    suspend fun markSwingAlertTriggered(
        id: Long,
        changePercent: Double,
        priceBrl: Double,
        triggeredAt: Long,
        dayKey: String
    ) = smartAlertDao.markSwingTriggered(id, changePercent, priceBrl, triggeredAt, dayKey)

    suspend fun resetSwingAlertCooldown(id: Long) = smartAlertDao.resetSwingCooldown(id)

    suspend fun deleteSwingAlert(id: Long) = smartAlertDao.deleteSwingAlertById(id)

    suspend fun getActiveInvestmentGoals(): List<InvestmentGoalEntity> = smartAlertDao.getActiveInvestmentGoals()

    suspend fun insertInvestmentGoal(goal: InvestmentGoalEntity): Long = smartAlertDao.insertInvestmentGoal(goal)

    suspend fun setInvestmentGoalEnabled(id: Long, enabled: Boolean) = smartAlertDao.setInvestmentGoalEnabled(id, enabled)

    suspend fun updateInvestmentGoalProgress(id: Long, currentValue: Double) =
        smartAlertDao.updateGoalProgress(id, currentValue)

    suspend fun markInvestmentGoalTriggered(id: Long, currentValue: Double, triggeredAt: Long) =
        smartAlertDao.markGoalTriggered(id, currentValue, triggeredAt)

    suspend fun rearmInvestmentGoal(id: Long) = smartAlertDao.rearmInvestmentGoal(id)

    suspend fun deleteInvestmentGoal(id: Long) = smartAlertDao.deleteInvestmentGoalById(id)

    suspend fun insertNotificationHistory(entry: AlertNotificationHistoryEntity): Long =
        smartAlertDao.insertNotificationHistory(entry)

    suspend fun clearNotificationHistory() = smartAlertDao.clearNotificationHistory()
}

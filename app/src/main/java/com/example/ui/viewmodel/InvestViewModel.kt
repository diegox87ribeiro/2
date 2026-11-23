package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.formatFirebaseAiError
import com.example.data.importexport.ImportParseResult
import com.example.data.importexport.SpreadsheetImporter
import com.example.data.local.AlertNotificationHistoryEntity
import com.example.data.local.AppPreferencesState
import com.example.data.local.AssetSwingAlertEntity
import com.example.data.local.CryptoVirtualPositionEntity
import com.example.data.local.ImportHistoryEntity
import com.example.data.local.InvestDatabase
import com.example.data.local.InvestmentGoalEntity
import com.example.data.local.PortfolioOperationEntity
import com.example.data.local.PreferencesManager
import com.example.data.local.PriceTargetAlertEntity
import com.example.data.local.SavedAiRecommendationEntity
import com.example.data.local.StrategyChangeLogEntity
import com.example.data.local.StrategyCriteriaEntity
import com.example.data.repository.AiRepository
import com.example.data.repository.FirebaseAiRepository
import com.example.data.repository.GitHubFutureAdapter
import com.example.data.repository.IntegrationAdapterStatus
import com.example.data.repository.LiveMarketDataRepository
import com.example.data.repository.LiveQuoteParsers
import com.example.data.repository.MarketCatalogRepository
import com.example.data.repository.OrderScreenshotRecognizer
import com.example.data.repository.PortfolioRepository
import com.example.data.repository.ProfitChartConnector
import com.example.data.repository.ReportExporter
import com.example.data.repository.SupabaseFutureAdapter
import com.example.domain.model.AiAgentRole
import com.example.domain.model.AiRecommendation
import com.example.domain.model.AssetClass
import com.example.domain.model.BenchmarkSeries
import com.example.domain.model.DividendDateAlertEvaluator
import com.example.domain.model.DividendDateEventType
import com.example.domain.model.DividendMonitorCalculator
import com.example.domain.model.HhiRiskReport
import com.example.domain.model.InvestmentGoalType
import com.example.domain.model.LiveMarketQuote
import com.example.domain.model.LiveQuoteLookupState
import com.example.domain.model.MarketAsset
import com.example.domain.model.OperationType
import com.example.domain.model.OrderScreenshotRecognitionState
import com.example.domain.model.PortfolioItemView
import com.example.domain.model.PortfolioType
import com.example.domain.model.PriceAlertCondition
import com.example.domain.model.PriceTargetEvaluator
import com.example.domain.model.ProfitChartConnectionState
import com.example.domain.model.RecognizedOrderDraft
import com.example.domain.model.SimulationScenarioResult
import com.example.domain.model.SmartAlertEvaluator
import com.example.domain.model.SwingDirection
import com.example.notifications.DividendDateAlertWorkScheduler
import com.example.notifications.DividendDateNotificationHelper
import com.example.notifications.PriceAlertNotificationHelper
import com.example.notifications.PriceAlertWorkScheduler
import com.example.notifications.SmartAlertNotificationHelper
import com.example.notifications.SmartAlertWorkScheduler
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InvestDashboardState(
    val realPositions: List<PortfolioItemView> = emptyList(),
    val simPositions: List<PortfolioItemView> = emptyList(),
    val operations: List<PortfolioOperationEntity> = emptyList(),
    val importHistory: List<ImportHistoryEntity> = emptyList(),
    val latestRealImport: ImportHistoryEntity? = null,
    val preferences: AppPreferencesState = AppPreferencesState(),
    val realHhi: HhiRiskReport = HhiRiskReport.calculate(emptyList(), 0.0),
    val simHhi: HhiRiskReport = HhiRiskReport.calculate(emptyList(), 25000.0),
    val strategyCriteria: StrategyCriteriaEntity = StrategyCriteriaEntity(),
    val strategyChangeLog: List<StrategyChangeLogEntity> = emptyList(),
    val cryptoVirtualPositions: List<CryptoVirtualPositionEntity> = emptyList(),
    val savedAiRecommendations: List<SavedAiRecommendationEntity> = emptyList(),
    val marketAssets: List<MarketAsset> = MarketCatalogRepository.catalogAssets,
    val benchmarks: List<BenchmarkSeries> = MarketCatalogRepository.benchmarks12m,
    val supabaseStatus: IntegrationAdapterStatus = SupabaseFutureAdapter().getStatus(""),
    val githubStatus: IntegrationAdapterStatus = GitHubFutureAdapter().getStatus(""),
    val liveQuotesByTicker: Map<String, LiveMarketQuote> = emptyMap(),
    val liveLookupState: LiveQuoteLookupState = LiveQuoteLookupState.Idle,
    val isLiveBatchRefreshing: Boolean = false,
    val profitConnectionState: ProfitChartConnectionState = ProfitChartConnectionState.Disconnected,
    val priceTargetAlerts: List<PriceTargetAlertEntity> = emptyList(),
    val isCheckingPriceAlerts: Boolean = false
)

data class SmartAlertsUiState(
    val swingAlerts: List<AssetSwingAlertEntity> = emptyList(),
    val investmentGoals: List<InvestmentGoalEntity> = emptyList(),
    val history: List<AlertNotificationHistoryEntity> = emptyList(),
    val isCheckingSmartAlerts: Boolean = false
)

class InvestViewModel(application: Application) : AndroidViewModel(application) {

    private val db = InvestDatabase.getInstance(application)
    private val prefsManager = PreferencesManager(application)
    private val repository = PortfolioRepository(application, db, prefsManager)
    private val liveMarketRepository = LiveMarketDataRepository()
    private val profitConnector = ProfitChartConnector(application)
    private val orderScreenshotRecognizer = OrderScreenshotRecognizer(application)
    private val supabaseAdapter = SupabaseFutureAdapter()
    private val githubAdapter = GitHubFutureAdapter()

    private val _selectedPortfolioType = MutableStateFlow(PortfolioType.REAL)
    val selectedPortfolioType: StateFlow<PortfolioType> = _selectedPortfolioType.asStateFlow()

    private val _liveQuotesByTicker = MutableStateFlow<Map<String, LiveMarketQuote>>(emptyMap())
    private val _liveLookupState = MutableStateFlow<LiveQuoteLookupState>(LiveQuoteLookupState.Idle)
    private val _isLiveBatchRefreshing = MutableStateFlow(false)
    private val _isCheckingPriceAlerts = MutableStateFlow(false)
    private val _isCheckingSmartAlerts = MutableStateFlow(false)
    private val _profitConnectionState = MutableStateFlow<ProfitChartConnectionState>(ProfitChartConnectionState.Disconnected)
    private var profitPollingJob: Job? = null

    private val _importFeedback = MutableStateFlow<ImportParseResult?>(null)
    val importFeedback: StateFlow<ImportParseResult?> = _importFeedback.asStateFlow()

    private val _pendingImportPreview = MutableStateFlow<ImportParseResult?>(null)
    val pendingImportPreview: StateFlow<ImportParseResult?> = _pendingImportPreview.asStateFlow()

    private val _orderScreenshotState = MutableStateFlow<OrderScreenshotRecognitionState>(OrderScreenshotRecognitionState.Idle)
    val orderScreenshotState: StateFlow<OrderScreenshotRecognitionState> = _orderScreenshotState.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    val smartAlertsState: StateFlow<SmartAlertsUiState> = combine(
        repository.swingAlertsFlow,
        repository.investmentGoalsFlow,
        repository.alertHistoryFlow,
        _isCheckingSmartAlerts
    ) { swings, goals, history, checking ->
        SmartAlertsUiState(
            swingAlerts = swings,
            investmentGoals = goals,
            history = history,
            isCheckingSmartAlerts = checking
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SmartAlertsUiState()
    )

    private val mergedLiveQuotesFlow = combine(
        repository.cachedLiveQuotesFlow,
        _liveQuotesByTicker
    ) { cached, fresh -> cached + fresh }

    val dashboardState: StateFlow<InvestDashboardState> = combine(
        repository.positionsFlow,
        repository.operationsFlow,
        repository.importHistoryFlow,
        repository.preferencesFlow,
        repository.strategyCriteriaFlow,
        repository.strategyChangeLogFlow,
        repository.cryptoVirtualPositionsFlow,
        repository.savedAiRecommendationsFlow,
        mergedLiveQuotesFlow,
        _liveLookupState,
        _isLiveBatchRefreshing,
        _profitConnectionState,
        repository.priceTargetAlertsFlow,
        _isCheckingPriceAlerts
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val positions = values[0] as List<PortfolioItemView>
        @Suppress("UNCHECKED_CAST")
        val ops = values[1] as List<PortfolioOperationEntity>
        @Suppress("UNCHECKED_CAST")
        val imports = values[2] as List<ImportHistoryEntity>
        val prefs = values[3] as AppPreferencesState
        val criteria = values[4] as StrategyCriteriaEntity
        @Suppress("UNCHECKED_CAST")
        val strategyLog = values[5] as List<StrategyChangeLogEntity>
        @Suppress("UNCHECKED_CAST")
        val crypto = values[6] as List<CryptoVirtualPositionEntity>
        @Suppress("UNCHECKED_CAST")
        val savedAi = values[7] as List<SavedAiRecommendationEntity>
        @Suppress("UNCHECKED_CAST")
        val liveQuotes = values[8] as Map<String, LiveMarketQuote>
        val lookupState = values[9] as LiveQuoteLookupState
        val batchRefreshing = values[10] as Boolean
        val profitState = values[11] as ProfitChartConnectionState
        @Suppress("UNCHECKED_CAST")
        val alerts = values[12] as List<PriceTargetAlertEntity>
        val checkingAlerts = values[13] as Boolean

        val realPos = positions.filter { it.portfolioType == PortfolioType.REAL }
        val simPos = positions.filter { it.portfolioType == PortfolioType.SIMULATION }
        val latestReal = imports.firstOrNull { it.isActiveRealReference } ?: imports.firstOrNull()

        val realCyclicalByTicker = realPos.associate { it.ticker.trim().uppercase() to it.isCyclical }
        val updatedCatalog = MarketCatalogRepository.catalogAssets.map { base ->
            val upper = base.ticker.uppercase()
            val live = liveQuotes[upper]
            val merged = if (live != null) LiveMarketDataRepository.mergeLiveQuoteIntoMarketAsset(base, live) else base
            val customCyclical = realCyclicalByTicker[upper]
            if (customCyclical != null) merged.copy(isCyclical = customCyclical) else merged
        }
        val catalogTickers = updatedCatalog.map { it.ticker.uppercase() }.toSet()
        val extraLiveAssets = liveQuotes.values
            .filter { it.ticker.uppercase() !in catalogTickers }
            .map { quote ->
                val merged = LiveMarketDataRepository.mergeLiveQuoteIntoMarketAsset(null, quote)
                val customCyclical = realCyclicalByTicker[quote.ticker.uppercase()]
                if (customCyclical != null) merged.copy(isCyclical = customCyclical) else merged
            }

        InvestDashboardState(
            realPositions = realPos,
            simPositions = simPos,
            operations = ops,
            importHistory = imports,
            latestRealImport = latestReal,
            preferences = prefs,
            realHhi = HhiRiskReport.calculate(realPos, prefs.cashRealBrl),
            simHhi = HhiRiskReport.calculate(simPos, prefs.cashSimBrl),
            strategyCriteria = criteria,
            strategyChangeLog = strategyLog,
            cryptoVirtualPositions = crypto,
            savedAiRecommendations = savedAi,
            marketAssets = extraLiveAssets + updatedCatalog,
            benchmarks = MarketCatalogRepository.benchmarks12m,
            supabaseStatus = supabaseAdapter.getStatus(prefs.supabaseEndpointConfigured),
            githubStatus = githubAdapter.getStatus(prefs.githubRepoConfigured),
            liveQuotesByTicker = liveQuotes,
            liveLookupState = lookupState,
            isLiveBatchRefreshing = batchRefreshing,
            profitConnectionState = profitState,
            priceTargetAlerts = alerts,
            isCheckingPriceAlerts = checkingAlerts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InvestDashboardState()
    )

    init {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.ensureInitialDefaults()
                    repository.initDefaultCryptoWalletIfEmpty(dashboardState.value.cryptoVirtualPositions)
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                Log.e("InvestViewModel", "Falha ao inicializar dados locais padrão", err)
                _statusBannerMessage.value = "Aviso de banco local: ${err.localizedMessage ?: "falha na inicialização"}"
            }
        }
        viewModelScope.launch {
            repository.preferencesFlow
                .map { Triple(it.profitAutoSyncEnabled, it.profitEndpointUrl, it.profitApplyToPositions) }
                .distinctUntilChanged()
                .collect { (autoSync, endpointUrl, applyPositions) ->
                    profitPollingJob?.cancel()
                    if (autoSync && endpointUrl.isNotBlank()) {
                        profitPollingJob = viewModelScope.launch {
                            while (isActive) {
                                delay(20_000L)
                                val token = dashboardState.value.preferences.profitAuthToken
                                executeProfitEndpointSync(endpointUrl, token, applyPositions, notifyBanner = false)
                            }
                        }
                    }
                }
        }
        viewModelScope.launch {
            repository.priceTargetAlertsFlow
                .map { alerts ->
                    alerts.any { (it.isEnabled && !it.hasTriggered) || (it.hasTriggered && it.pendingNotification) }
                }
                .distinctUntilChanged()
                .collect { hasActiveOrPending ->
                    PriceAlertWorkScheduler.syncSchedule(getApplication(), hasActiveOrPending)
                }
        }
        viewModelScope.launch {
            flushPendingPriceNotifications()
        }
        viewModelScope.launch {
            combine(
                repository.preferencesFlow.map {
                    it.dividendAlertsEnabled && (it.dividendAlertDataCom || it.dividendAlertDataEx)
                },
                repository.positionsFlow.map { list ->
                    list.any { it.portfolioType == PortfolioType.REAL && it.quantity > 0.0 }
                }
            ) { enabled, hasReal -> enabled && hasReal }
                .distinctUntilChanged()
                .collect { shouldSchedule ->
                    DividendDateAlertWorkScheduler.syncSchedule(getApplication(), shouldSchedule)
                }
        }
        viewModelScope.launch {
            combine(
                repository.swingAlertsFlow.map { list -> list.any { it.isEnabled } },
                repository.investmentGoalsFlow.map { list -> list.any { it.isEnabled && !it.hasTriggered } }
            ) { hasSwings, hasGoals -> hasSwings || hasGoals }
                .distinctUntilChanged()
                .collect { hasActive ->
                    SmartAlertWorkScheduler.syncSchedule(getApplication(), hasActive)
                }
        }
    }

    fun selectPortfolioType(type: PortfolioType) {
        _selectedPortfolioType.value = type
    }

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    fun clearImportFeedback() {
        _importFeedback.value = null
    }

    fun previewSpreadsheetImport(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.parseSpreadsheetPreview(uri)
                }
                if (result.positions.isNotEmpty() || result.dividends.isNotEmpty() || result.quickUpdates.isNotEmpty()) {
                    _pendingImportPreview.value = result
                } else {
                    _importFeedback.value = result
                    _statusBannerMessage.value = result.warnings.firstOrNull()
                        ?: "Nenhum ativo, preço ou dividendo válido encontrado na planilha."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBannerMessage.value = "Erro ao ler planilha: ${e.localizedMessage ?: "formato inválido"}"
            }
        }
    }

    fun confirmPendingImport() {
        val pending = _pendingImportPreview.value ?: return
        viewModelScope.launch {
            try {
                val updatedCount = withContext(Dispatchers.IO) {
                    repository.commitRealPortfolioImport(pending)
                }
                _pendingImportPreview.value = null
                _importFeedback.value = pending
                val divSuffix = if (pending.dividends.isNotEmpty()) " e ${pending.dividends.size} provento(s)" else ""
                _statusBannerMessage.value = if (pending.positions.isEmpty() && pending.quickUpdates.isNotEmpty()) {
                    "Atualização '${pending.fileName}' aplicada: $updatedCount ativo(s) atualizado(s)$divSuffix."
                } else {
                    "Importação '${pending.fileName}' (${pending.positions.size} ativos$divSuffix) salva na Carteira Real."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBannerMessage.value = "Erro ao salvar importação: ${e.localizedMessage ?: "falha de gravação"}"
            }
        }
    }

    fun dismissPendingImport() {
        _pendingImportPreview.value = null
    }

    fun buildStandardCsvTemplate(): String = SpreadsheetImporter.generateStandardCsvTemplate()

    fun buildQuickUpdateCsvTemplate(): String =
        SpreadsheetImporter.generateQuickUpdateCsvTemplate(dashboardState.value.realPositions)

    fun buildPortfolioCsvExport(type: PortfolioType): String {
        val st = dashboardState.value
        val positions = if (type == PortfolioType.REAL) st.realPositions else st.simPositions
        val cash = if (type == PortfolioType.REAL) st.preferences.cashRealBrl else st.preferences.cashSimBrl
        val latestDividends = st.operations
            .filter { it.portfolioType == type.name && it.operationType == OperationType.PROVENTO.name && it.totalValue > 0.0 }
            .groupBy { it.ticker.trim().uppercase() }
            .mapValues { (_, ops) ->
                val latest = ops.maxByOrNull { it.createdAt } ?: ops.first()
                val event = latest.notes.substringBefore(" •").ifBlank { "Provento" }
                Triple(latest.totalValue, latest.dateIso, event)
            }
        return SpreadsheetImporter.exportPositionsToCsv(positions, cash, latestDividends)
    }

    fun notifyMessage(message: String) {
        _statusBannerMessage.value = message
    }

    fun importSpreadsheet(uri: Uri) {
        previewSpreadsheetImport(uri)
    }

    fun loadDemoRealPortfolio() {
        viewModelScope.launch {
            repository.loadDemoRealPortfolio()
            _statusBannerMessage.value =
                "Carteira demonstrativa (DADOS FICTÍCIOS) carregada localmente na aba Carteira Real."
        }
    }

    fun clearPortfolio(type: PortfolioType) {
        viewModelScope.launch {
            repository.clearPortfolio(type)
            _statusBannerMessage.value = "${type.label} limpa localmente."
        }
    }

    fun addManualPosition(
        portfolioType: PortfolioType,
        ticker: String,
        name: String,
        assetClass: AssetClass,
        quantity: Double,
        averagePrice: Double,
        currentPrice: Double?,
        sector: String,
        dataComDay: Int?,
        isCyclical: Boolean = true
    ) {
        viewModelScope.launch {
            repository.addManualPosition(
                portfolioType = portfolioType,
                ticker = ticker,
                name = name,
                assetClass = assetClass,
                quantity = quantity,
                averagePrice = averagePrice,
                currentPrice = currentPrice,
                sector = sector,
                dataComDay = dataComDay,
                isCyclical = isCyclical
            )
            _statusBannerMessage.value = "Posição ${ticker.uppercase()} registrada em ${portfolioType.label}."
        }
    }

    fun recordOperation(
        portfolioType: PortfolioType,
        ticker: String,
        operationType: OperationType,
        quantity: Double,
        unitPrice: Double,
        notes: String,
        dateIso: String? = null
    ) {
        viewModelScope.launch {
            repository.recordOperation(portfolioType, ticker, operationType, quantity, unitPrice, notes, dateIso)
            val dateSuffix = dateIso?.trim()?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            _statusBannerMessage.value = "Operação ${operationType.label} de ${ticker.trim().uppercase()} registrada$dateSuffix."
        }
    }

    fun deleteOperation(id: Long) {
        viewModelScope.launch {
            repository.deleteOperation(id)
            _statusBannerMessage.value = "Lançamento removido localmente."
        }
    }

    fun updatePosition(
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
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.updatePosition(
                        id = id,
                        ticker = ticker,
                        name = name,
                        assetClass = assetClass,
                        quantity = quantity,
                        averagePrice = averagePrice,
                        currentPrice = currentPrice,
                        sector = sector,
                        dataComDay = dataComDay,
                        existingSourceLabel = existingSourceLabel,
                        isCyclical = isCyclical
                    )
                }
                _statusBannerMessage.value = "Cadastro e posição de ${ticker.trim().uppercase()} atualizados."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBannerMessage.value = "Erro ao atualizar posição: ${e.localizedMessage ?: "falha local"}"
            }
        }
    }

    fun deletePosition(id: Long) {
        viewModelScope.launch { repository.deletePosition(id) }
    }

    fun updateCashBalance(type: PortfolioType, amount: Double) {
        viewModelScope.launch {
            repository.updateCashBalance(type, amount)
            _statusBannerMessage.value = "Saldo em caixa atualizado em ${type.label}."
        }
    }

    fun updateStrategyCriteria(newCriteria: StrategyCriteriaEntity, reason: String) {
        viewModelScope.launch {
            val current = dashboardState.value.strategyCriteria
            repository.updateStrategyCriteria(current, newCriteria, reason)
            _statusBannerMessage.value = "Critérios de estratégia atualizados e registrados no histórico."
        }
    }

    fun updateCryptoAllocations(btcPct: Double, ethPct: Double, solPct: Double) {
        viewModelScope.launch {
            repository.updateCryptoVirtualAllocations(btcPct, ethPct, solPct)
            _statusBannerMessage.value = "Carteira virtual cripto de R$ 500 rebalanceada (sem alavancagem/sem ordens reais)."
        }
    }

    fun resetCryptoWallet() {
        viewModelScope.launch {
            repository.resetCryptoVirtualWallet()
            _statusBannerMessage.value = "Carteira virtual de R$ 500 restaurada para alocação padrão."
        }
    }

    fun setAiPortfolioConsent(granted: Boolean) {
        viewModelScope.launch { repository.setAiConsent(granted) }
    }

    fun setHideInfoBanners(hide: Boolean) {
        viewModelScope.launch { prefsManager.setHideInfoBanners(hide) }
    }

    fun saveRebalanceTargets(
        acoes: Double,
        fii: Double,
        fiagro: Double,
        etf: Double,
        rendaFixa: Double,
        cripto: Double
    ) {
        viewModelScope.launch {
            prefsManager.saveRebalanceTargets(acoes, fii, fiagro, etf, rendaFixa, cripto)
            _statusBannerMessage.value = "Metas de alocação por classe salvas."
        }
    }

    fun saveTaxCarryLoss(commonBrl: Double, fiiBrl: Double) {
        viewModelScope.launch {
            prefsManager.saveTaxCarryLoss(commonBrl, fiiBrl)
            _statusBannerMessage.value = "Prejuízos acumulados atualizados na apuração de IR."
        }
    }

    fun setFutureIntegrations(supabaseUrl: String, githubRepo: String) {
        viewModelScope.launch {
            repository.setFutureIntegrationMeta(supabaseUrl, githubRepo)
            _statusBannerMessage.value = "Configuração futura salva localmente (nenhum serviço pago ativado)."
        }
    }

    fun simulatePortfolioScenario(
        initialCapitalBrl: Double,
        monthlyContributionBrl: Double,
        horizonMonths: Int,
        annualReturnPercent: Double,
        annualDyPercent: Double,
        reinvestDividends: Boolean,
        macroShockPercent: Double
    ): SimulationScenarioResult {
        val monthlyRate = (annualReturnPercent / 100.0) / 12.0
        val monthlyDyRate = (annualDyPercent / 100.0) / 12.0
        var balance = initialCapitalBrl
        var contributed = initialCapitalBrl
        val curve = mutableListOf(balance)

        for (m in 1..horizonMonths.coerceIn(1, 240)) {
            val growth = balance * monthlyRate
            val dividends = balance * monthlyDyRate
            balance += growth + monthlyContributionBrl + if (reinvestDividends) dividends else 0.0
            contributed += monthlyContributionBrl
            curve.add(balance)
        }

        val finalDividendsMonthly = balance * monthlyDyRate
        val stressed = balance * (1.0 + (macroShockPercent / 100.0))

        return SimulationScenarioResult(
            finalEquityBrl = balance,
            totalContributedBrl = contributed,
            projectedDividendsMonthlyBrl = finalDividendsMonthly,
            estimatedYieldAnnualPercent = annualDyPercent,
            stressedEquityPessimisticBrl = stressed.coerceAtLeast(0.0),
            projectedCurveMonths = curve
        )
    }

    fun buildReportText(): String {
        val st = dashboardState.value
        return ReportExporter.buildExecutiveReportText(
            realPositions = st.realPositions,
            simPositions = st.simPositions,
            realHhi = st.realHhi,
            simHhi = st.simHhi,
            latestImport = st.latestRealImport,
            savedInsights = st.savedAiRecommendations
        )
    }

    fun queryLiveAssetQuote(rawTicker: String) {
        val ticker = LiveQuoteParsers.normalizeTicker(rawTicker)
        if (ticker.length < 4) {
            _liveLookupState.value = LiveQuoteLookupState.Error(
                ticker = rawTicker.trim(),
                message = "Digite um código B3 válido (ex.: VALE3, ITUB4, HGLG11, VULC3)."
            )
            return
        }
        viewModelScope.launch {
            _liveLookupState.value = LiveQuoteLookupState.Loading(ticker)
            val result = liveMarketRepository.fetchLiveQuote(ticker)
            result.onSuccess { quote ->
                _liveQuotesByTicker.value = _liveQuotesByTicker.value + (quote.ticker to quote)
                _liveLookupState.value = LiveQuoteLookupState.Success(quote)
                withContext(Dispatchers.IO) { repository.saveCachedLiveQuote(quote) }
                evaluateLiveQuotesAgainstAlerts(mapOf(quote.ticker.uppercase() to quote), notifyIfNoneTriggered = false)
            }.onFailure { err ->
                _liveLookupState.value = LiveQuoteLookupState.Error(
                    ticker = ticker,
                    message = err.localizedMessage ?: "Não foi possível consultar $ticker em tempo real."
                )
            }
        }
    }

    fun refreshLiveQuotesForTickers(tickers: List<String>) {
        val clean = tickers.map(LiveQuoteParsers::normalizeTicker)
            .filter { it.length >= 4 && !it.startsWith("SELIC") && !it.startsWith("CDB") && !it.startsWith("LCI") && !it.startsWith("IPCA") }
            .distinct()
        if (clean.isEmpty() || _isLiveBatchRefreshing.value) return
        viewModelScope.launch {
            _isLiveBatchRefreshing.value = true
            try {
                val fetched = liveMarketRepository.fetchBatchQuotes(clean)
                if (fetched.isNotEmpty()) {
                    _liveQuotesByTicker.value = _liveQuotesByTicker.value + fetched
                    withContext(Dispatchers.IO) { repository.saveCachedLiveQuotes(fetched.values) }
                    evaluateLiveQuotesAgainstAlerts(fetched, notifyIfNoneTriggered = false)
                    _statusBannerMessage.value = "${fetched.size} ativo(s) atualizado(s) em tempo real e salvo(s) no cache local."
                } else {
                    _statusBannerMessage.value = "Sem conexão no momento. Exibindo últimos dados salvos localmente no Room."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBannerMessage.value = "Falha ao atualizar cotações ao vivo: ${e.localizedMessage ?: "erro de rede"}"
            } finally {
                _isLiveBatchRefreshing.value = false
            }
        }
    }

    fun applyLiveQuoteToPortfolio(ticker: String) {
        val clean = LiveQuoteParsers.normalizeTicker(ticker)
        val live = dashboardState.value.liveQuotesByTicker[clean] ?: _liveQuotesByTicker.value[clean] ?: return
        viewModelScope.launch {
            val updatedCount = withContext(Dispatchers.IO) {
                repository.applyLivePriceToPortfolio(
                    ticker = clean,
                    livePrice = live.price,
                    liveDataComDay = live.dataComDay,
                    liveDataExDay = live.dataExDay
                )
            }
            _statusBannerMessage.value = if (updatedCount > 0) {
                "Cotação ao vivo de $clean aplicada na Carteira."
            } else {
                "$clean não encontrado na Carteira salva."
            }
        }
    }

    fun refreshAndApplyLiveQuotesForPortfolio(type: PortfolioType) {
        val positions = if (type == PortfolioType.REAL) {
            dashboardState.value.realPositions
        } else {
            dashboardState.value.simPositions
        }
        val tickers = positions
            .filter { it.assetClass != AssetClass.RENDA_FIXA && it.assetClass != AssetClass.CRIPTO }
            .map { LiveQuoteParsers.normalizeTicker(it.ticker) }
            .filter { it.length >= 4 }
            .distinct()
        if (tickers.isEmpty() || _isLiveBatchRefreshing.value) return
        viewModelScope.launch {
            _isLiveBatchRefreshing.value = true
            try {
                val fetched = liveMarketRepository.fetchBatchQuotes(tickers)
                if (fetched.isNotEmpty()) {
                    _liveQuotesByTicker.value = _liveQuotesByTicker.value + fetched
                    var appliedPositions = 0
                    withContext(Dispatchers.IO) {
                        repository.saveCachedLiveQuotes(fetched.values)
                        fetched.forEach { (ticker, quote) ->
                            appliedPositions += repository.applyLivePriceToPortfolio(
                                ticker = ticker,
                                livePrice = quote.price,
                                liveDataComDay = quote.dataComDay,
                                liveDataExDay = quote.dataExDay
                            )
                        }
                    }
                    evaluateLiveQuotesAgainstAlerts(fetched, notifyIfNoneTriggered = false)
                    _statusBannerMessage.value = "${fetched.size} ativo(s) consultado(s) ao vivo e $appliedPositions posição(ões) atualizada(s)."
                } else {
                    _statusBannerMessage.value = "Nenhuma cotação ao vivo pôde ser obtida. Dados locais preservados no Room."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBannerMessage.value = "Falha ao atualizar carteira ao vivo: ${e.localizedMessage ?: "erro de rede"}"
            } finally {
                _isLiveBatchRefreshing.value = false
            }
        }
    }

    fun connectAndSyncProfitChart(
        endpointUrl: String,
        authToken: String,
        autoSyncEnabled: Boolean,
        applyToPositions: Boolean
    ) {
        viewModelScope.launch {
            prefsManager.saveProfitChartConfig(
                endpointUrl = endpointUrl,
                authToken = authToken,
                autoSyncEnabled = autoSyncEnabled,
                applyToPositions = applyToPositions
            )
            executeProfitEndpointSync(endpointUrl.trim(), authToken.trim(), applyToPositions, notifyBanner = true)
        }
    }

    fun syncProfitChartNow() {
        val prefs = dashboardState.value.preferences
        if (prefs.profitEndpointUrl.isBlank()) {
            _profitConnectionState.value = ProfitChartConnectionState.Error(
                "Configure a URL do conector ProfitChart (Bridge/Webhook) ou importe o arquivo de ordens do ProfitChart."
            )
            return
        }
        viewModelScope.launch {
            executeProfitEndpointSync(
                endpointUrl = prefs.profitEndpointUrl,
                authToken = prefs.profitAuthToken,
                applyToPositions = prefs.profitApplyToPositions,
                notifyBanner = true
            )
        }
    }

    fun importProfitChartFile(uri: Uri, applyToPositions: Boolean) {
        viewModelScope.launch {
            _profitConnectionState.value = ProfitChartConnectionState.Syncing
            val parseRes = profitConnector.parseExportedUri(uri)
            parseRes.onSuccess { outcome ->
                val summary = withContext(Dispatchers.IO) {
                    repository.syncProfitChartExecutions(
                        executions = outcome.executions,
                        skippedNonExecutedCount = outcome.skippedNonExecutedCount,
                        applyToPositions = applyToPositions
                    )
                }
                _profitConnectionState.value = ProfitChartConnectionState.Connected(summary)
                _statusBannerMessage.value = summary.message
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Erro ao importar arquivo do ProfitChart."
                _profitConnectionState.value = ProfitChartConnectionState.Error(msg)
                _statusBannerMessage.value = msg
            }
        }
    }

    fun disconnectProfitChart() {
        profitPollingJob?.cancel()
        viewModelScope.launch {
            prefsManager.disconnectProfitChart()
            _profitConnectionState.value = ProfitChartConnectionState.Disconnected
            _statusBannerMessage.value = "Conexão automática com ProfitChart encerrada."
        }
    }

    private suspend fun executeProfitEndpointSync(
        endpointUrl: String,
        authToken: String,
        applyToPositions: Boolean,
        notifyBanner: Boolean
    ) {
        _profitConnectionState.value = ProfitChartConnectionState.Syncing
        val res = profitConnector.fetchFromEndpoint(endpointUrl, authToken)
        res.onSuccess { outcome ->
            val summary = withContext(Dispatchers.IO) {
                repository.syncProfitChartExecutions(
                    executions = outcome.executions,
                    skippedNonExecutedCount = outcome.skippedNonExecutedCount,
                    applyToPositions = applyToPositions
                )
            }
            _profitConnectionState.value = ProfitChartConnectionState.Connected(summary)
            if (notifyBanner || summary.newImportedCount > 0) {
                _statusBannerMessage.value = summary.message
            }
        }.onFailure { err ->
            val msg = err.localizedMessage ?: "Falha na sincronização com ProfitChart."
            _profitConnectionState.value = ProfitChartConnectionState.Error(msg)
            prefsManager.updateProfitSyncStatus(System.currentTimeMillis(), msg)
            if (notifyBanner) _statusBannerMessage.value = msg
        }
    }

    fun addPriceTargetAlert(
        rawTicker: String,
        targetPriceBrl: Double,
        condition: PriceAlertCondition,
        notes: String
    ) {
        val ticker = LiveQuoteParsers.normalizeTicker(rawTicker)
        if (ticker.length < 4 || targetPriceBrl <= 0.0) {
            _statusBannerMessage.value = "Informe um ticker válido e uma meta de preço maior que zero."
            return
        }
        viewModelScope.launch {
            val insertedId = withContext(Dispatchers.IO) {
                repository.insertPriceTargetAlert(
                    PriceTargetAlertEntity(
                        ticker = ticker,
                        targetPriceBrl = targetPriceBrl,
                        condition = condition.code,
                        isEnabled = true,
                        hasTriggered = false,
                        notes = notes.trim()
                    )
                )
            }
            PriceAlertWorkScheduler.syncSchedule(getApplication(), hasActiveAlerts = true)
            _statusBannerMessage.value = "Alerta push criado para $ticker (${condition.shortSymbol} R$ ${"%.2f".format(targetPriceBrl)})."

            val existingLive = _liveQuotesByTicker.value[ticker]
            if (existingLive != null) {
                evaluateLiveQuotesAgainstAlerts(mapOf(ticker to existingLive), notifyIfNoneTriggered = false)
            } else {
                val liveFetch = liveMarketRepository.fetchLiveQuote(ticker)
                liveFetch.onSuccess { quote ->
                    _liveQuotesByTicker.value = _liveQuotesByTicker.value + (quote.ticker to quote)
                    withContext(Dispatchers.IO) { repository.saveCachedLiveQuote(quote) }
                    evaluateLiveQuotesAgainstAlerts(mapOf(quote.ticker.uppercase() to quote), notifyIfNoneTriggered = false)
                }.onFailure {
                    // Dispositivo temporariamente offline: registra estado em fila offline no Room e agenda WorkManager
                    if (insertedId > 0L) {
                        withContext(Dispatchers.IO) {
                            repository.markPriceTargetAlertsOfflineQueued(listOf(insertedId))
                        }
                        PriceAlertWorkScheduler.triggerImmediateCheck(getApplication(), requireNetwork = true)
                        _statusBannerMessage.value =
                            "Meta de $ticker salva localmente no Room. Push será disparado assim que a conexão voltar."
                    }
                }
            }
        }
    }

    fun togglePriceTargetAlert(id: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setPriceTargetAlertEnabled(id, enabled)
        }
    }

    fun rearmPriceTargetAlert(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.rearmPriceTargetAlert(id)
            }
            _statusBannerMessage.value = "Alerta reativado no Room para monitoramento contínuo."
        }
    }

    fun deletePriceTargetAlert(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePriceTargetAlert(id)
        }
    }

    fun onNotificationPermissionGranted() {
        viewModelScope.launch {
            flushPendingPriceNotifications()
        }
    }

    fun checkPriceAlertsNow() {
        if (_isCheckingPriceAlerts.value) return
        viewModelScope.launch {
            _isCheckingPriceAlerts.value = true
            try {
                flushPendingPriceNotifications()
                val activeAlerts = withContext(Dispatchers.IO) { repository.getActivePriceTargetAlerts() }
                if (activeAlerts.isEmpty()) {
                    _statusBannerMessage.value = "Nenhum alerta ativo pendente no momento."
                    return@launch
                }
                val tickers = activeAlerts.map { it.ticker.trim().uppercase() }.distinct()
                val fetched = liveMarketRepository.fetchBatchQuotes(tickers)
                if (fetched.isEmpty()) {
                    withContext(Dispatchers.IO) {
                        repository.markPriceTargetAlertsOfflineQueued(activeAlerts.map { it.id })
                    }
                    PriceAlertWorkScheduler.triggerImmediateCheck(getApplication(), requireNetwork = true)
                    _statusBannerMessage.value = "Sem internet agora. Metas persistidas no Room e enfileiradas para disparo automático ao reconectar."
                } else {
                    _liveQuotesByTicker.value = _liveQuotesByTicker.value + fetched
                    withContext(Dispatchers.IO) { repository.saveCachedLiveQuotes(fetched.values) }
                    evaluateLiveQuotesAgainstAlerts(fetched, notifyIfNoneTriggered = true)
                }
            } finally {
                _isCheckingPriceAlerts.value = false
            }
        }
    }

    private suspend fun flushPendingPriceNotifications() {
        val pending = withContext(Dispatchers.IO) { repository.getPendingNotificationAlerts() }
        if (pending.isEmpty()) return
        var deliveredCount = 0
        pending.forEach { alert ->
            val evalPrice = alert.lastEvaluatedPriceBrl ?: alert.targetPriceBrl
            val delivered = PriceAlertNotificationHelper.sendPriceTargetNotification(
                context = getApplication(),
                alert = alert,
                currentPriceBrl = evalPrice,
                sourceLabel = "Persistência Local Room"
            )
            if (delivered) {
                deliveredCount++
                withContext(Dispatchers.IO) {
                    repository.markPriceTargetAlertNotificationDelivered(alert.id)
                }
            }
        }
        if (deliveredCount > 0) {
            _statusBannerMessage.value = "$deliveredCount notificação(ões) de meta pendente(s) entregue(s) do cache Room."
        }
    }

    private suspend fun evaluateLiveQuotesAgainstAlerts(
        quotes: Map<String, LiveMarketQuote>,
        notifyIfNoneTriggered: Boolean
    ) {
        val activeAlerts = withContext(Dispatchers.IO) { repository.getActivePriceTargetAlerts() }
        if (activeAlerts.isEmpty()) {
            val triggeredSwings = evaluateSharpSwingsAgainstQuotes(quotes)
            if (triggeredSwings.isNotEmpty()) {
                _statusBannerMessage.value = "Variação brusca detectada: ${triggeredSwings.joinToString(", ")}."
            }
            return
        }

        val now = System.currentTimeMillis()
        val triggeredList = mutableListOf<String>()
        var pendingPushCount = 0

        activeAlerts.forEach { alert ->
            val key = alert.ticker.trim().uppercase()
            val quote = quotes[key] ?: return@forEach
            if (PriceTargetEvaluator.isTargetReached(alert, quote.price)) {
                val delivered = PriceAlertNotificationHelper.sendPriceTargetNotification(
                    context = getApplication(),
                    alert = alert,
                    currentPriceBrl = quote.price,
                    sourceLabel = quote.primarySourceLabel
                )
                withContext(Dispatchers.IO) {
                    repository.markPriceTargetAlertTriggered(
                        id = alert.id,
                        evaluatedPriceBrl = quote.price,
                        triggeredAt = now,
                        notificationDelivered = delivered
                    )
                }
                if (!delivered) pendingPushCount++
                withContext(Dispatchers.IO) {
                    repository.insertNotificationHistory(
                        AlertNotificationHistoryEntity(
                            category = "PRICE_TARGET",
                            ticker = key,
                            title = "Meta de preço atingida: $key",
                            message = "$key chegou a R$ ${"%.2f".format(quote.price)} (alvo R$ ${"%.2f".format(alert.targetPriceBrl)}).",
                            deliveredAsPush = delivered,
                            createdAt = now
                        )
                    )
                }
                triggeredList.add("$key a R$ ${"%.2f".format(quote.price)}")
            } else {
                withContext(Dispatchers.IO) {
                    repository.updatePriceTargetAlertEvaluated(
                        id = alert.id,
                        evaluatedPriceBrl = quote.price,
                        evaluatedAt = now,
                        status = "Monitorado via ${quote.primarySourceLabel}"
                    )
                }
            }
        }

        val triggeredSwings = evaluateSharpSwingsAgainstQuotes(quotes)
        if (triggeredSwings.isNotEmpty() && triggeredList.isEmpty()) {
            _statusBannerMessage.value = "Variação brusca detectada: ${triggeredSwings.joinToString(", ")}."
        }

        if (triggeredList.isNotEmpty()) {
            _statusBannerMessage.value = if (pendingPushCount > 0) {
                "Meta atingida (${triggeredList.joinToString(", ")}). Salva no Room aguardando permissão de push."
            } else {
                "Meta atingida! Notificação enviada: ${triggeredList.joinToString(", ")}"
            }
        } else if (notifyIfNoneTriggered) {
            _statusBannerMessage.value = "Cotações verificadas ao vivo: nenhuma meta atingida ainda."
        }
    }

    fun updateDividendDateAlertConfig(
        enabled: Boolean,
        leadDays: Int,
        notifyDataCom: Boolean,
        notifyDataEx: Boolean
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.saveDividendDateAlertConfig(
                    enabled = enabled,
                    leadDays = leadDays,
                    notifyDataCom = notifyDataCom,
                    notifyDataEx = notifyDataEx
                )
            }
            val shouldRun = enabled && (notifyDataCom || notifyDataEx) && dashboardState.value.realPositions.isNotEmpty()
            DividendDateAlertWorkScheduler.syncSchedule(getApplication(), shouldRun)
            if (shouldRun) {
                DividendDateAlertWorkScheduler.triggerImmediateCheck(getApplication())
                _statusBannerMessage.value = "Alertas locais de Data-Com/Data-Ex ativados ($leadDays dia(s) antes)."
            } else if (!enabled) {
                _statusBannerMessage.value = "Alertas locais de Data-Com/Data-Ex desativados."
            }
        }
    }

    fun checkDividendDateAlertsNow() {
        viewModelScope.launch {
            val current = dashboardState.value
            val prefs = current.preferences
            if (current.realPositions.isEmpty()) {
                _statusBannerMessage.value = "Adicione ou importe ativos na Carteira Real para monitorar Data-Com e Data-Ex."
                return@launch
            }
            val upcoming = DividendDateAlertEvaluator.findUpcomingAlerts(
                realPositions = current.realPositions,
                marketAssets = current.marketAssets,
                liveQuotesByTicker = current.liveQuotesByTicker,
                leadDays = prefs.dividendAlertLeadDays,
                notifyDataCom = prefs.dividendAlertDataCom,
                notifyDataEx = prefs.dividendAlertDataEx
            )
            if (upcoming.isEmpty()) {
                val nextEvent = DividendDateAlertEvaluator.findNextEventsForPortfolio(
                    realPositions = current.realPositions,
                    marketAssets = current.marketAssets,
                    liveQuotesByTicker = current.liveQuotesByTicker,
                    notifyDataCom = prefs.dividendAlertDataCom,
                    notifyDataEx = prefs.dividendAlertDataEx
                ).firstOrNull()
                _statusBannerMessage.value = if (nextEvent != null) {
                    "Nenhum evento nos próximos ${prefs.dividendAlertLeadDays} dia(s). Próximo: ${nextEvent.ticker} (${nextEvent.eventType.label} em ${nextEvent.eventDateFormatted}, em ${nextEvent.daysUntil} dias)."
                } else {
                    "Nenhum ativo da Carteira Real possui Data-Com cadastrada para monitorar."
                }
                return@launch
            }

            if (!DividendDateNotificationHelper.hasNotificationPermission(getApplication())) {
                _statusBannerMessage.value = "Permita notificações nas configurações do Android para receber os alertas de Data-Com/Data-Ex."
                return@launch
            }

            val deliveredKeys = mutableListOf<String>()
            upcoming.forEach { alert ->
                val ok = DividendDateNotificationHelper.sendDividendDateNotification(
                    context = getApplication(),
                    alert = alert
                )
                if (ok) deliveredKeys.add(alert.dedupeKey)
            }
            if (deliveredKeys.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    repository.recordSentDividendAlertKeys(deliveredKeys)
                }
                _statusBannerMessage.value = "${deliveredKeys.size} notificação(ões) de Data-Com/Data-Ex enviada(s)!"
            } else {
                _statusBannerMessage.value = "Não foi possível entregar as notificações locais no dispositivo."
            }
        }
    }

    fun sendTestDividendDateNotification() {
        viewModelScope.launch {
            val current = dashboardState.value
            val prefs = current.preferences
            val nextEvent = DividendDateAlertEvaluator.findNextEventsForPortfolio(
                realPositions = current.realPositions,
                marketAssets = current.marketAssets,
                liveQuotesByTicker = current.liveQuotesByTicker,
                notifyDataCom = prefs.dividendAlertDataCom || !prefs.dividendAlertDataEx,
                notifyDataEx = prefs.dividendAlertDataEx
            ).firstOrNull()

            if (nextEvent == null) {
                _statusBannerMessage.value = "Cadastre ou importe um ativo com Data-Com na Carteira Real para testar a notificação."
                return@launch
            }
            if (!DividendDateNotificationHelper.hasNotificationPermission(getApplication())) {
                _statusBannerMessage.value = "Permita notificações do Android para receber o alerta de teste."
                return@launch
            }
            val delivered = DividendDateNotificationHelper.sendDividendDateNotification(
                context = getApplication(),
                alert = nextEvent,
                isTestPreview = true
            )
            _statusBannerMessage.value = if (delivered) {
                "Notificação de teste enviada: ${nextEvent.ticker} (${nextEvent.eventType.label} em ${nextEvent.eventDateFormatted})."
            } else {
                "Falha ao enviar notificação de teste. Verifique as permissões do app."
            }
        }
    }

    fun recognizeOrderScreenshot(uri: Uri) {
        val app = getApplication<Application>()
        val firebaseReady = runCatching {
            FirebaseApp.getApps(app).isNotEmpty() || FirebaseApp.initializeApp(app) != null
        }.getOrDefault(false)
        if (!firebaseReady) {
            _orderScreenshotState.value = OrderScreenshotRecognitionState.Error(
                message = "O reconhecimento de prints por IA requer Firebase AI Logic ativo neste aplicativo.",
                lastImageUriString = uri.toString()
            )
            return
        }

        viewModelScope.launch {
            _orderScreenshotState.value = OrderScreenshotRecognitionState.Analyzing(
                statusLabel = "Analisando print de ordens com Gemini..."
            )
            try {
                val result = orderScreenshotRecognizer.recognizeFromUri(uri)
                if (result.orders.isEmpty()) {
                    val hint = result.warnings.firstOrNull()
                        ?: "Nenhuma ordem de compra ou venda foi identificada neste print. Envie uma captura da tela de Ordens Executadas ou Nota de Corretagem."
                    _orderScreenshotState.value = OrderScreenshotRecognitionState.Error(
                        message = hint,
                        lastImageUriString = uri.toString()
                    )
                } else {
                    _orderScreenshotState.value = OrderScreenshotRecognitionState.Review(
                        result = result,
                        imageUriString = uri.toString()
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.w("InvestViewModel", "Timeout ao analisar print de ordens", e)
                _orderScreenshotState.value = OrderScreenshotRecognitionState.Error(
                    message = "Tempo esgotado ao analisar o print (60s). Verifique sua conexão e tente novamente.",
                    lastImageUriString = uri.toString()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("InvestViewModel", "Falha ao reconhecer print de ordens", e)
                _orderScreenshotState.value = OrderScreenshotRecognitionState.Error(
                    message = formatFirebaseAiError(
                        e,
                        "Não foi possível ler as ordens do print selecionado. Tente novamente com uma imagem nítida."
                    ),
                    lastImageUriString = uri.toString()
                )
            }
        }
    }

    fun commitRecognizedScreenshotOrders(
        portfolioType: PortfolioType,
        selectedOrders: List<RecognizedOrderDraft>,
        applyToPositions: Boolean
    ) {
        viewModelScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    repository.commitRecognizedScreenshotOrders(
                        portfolioType = portfolioType,
                        orders = selectedOrders,
                        applyToPositions = applyToPositions
                    )
                }
                _orderScreenshotState.value = OrderScreenshotRecognitionState.Idle
                _statusBannerMessage.value = summary.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("InvestViewModel", "Erro ao lançar ordens do print", e)
                _statusBannerMessage.value =
                    "Erro ao lançar ordens do print: ${e.localizedMessage ?: "falha de gravação"}"
            }
        }
    }

    fun dismissOrderScreenshotReview() {
        _orderScreenshotState.value = OrderScreenshotRecognitionState.Idle
    }

    // --- Sistema de Notificações Locais: Variações Bruscas & Metas de Investimento ---

    fun addSwingAlert(
        rawTicker: String,
        thresholdPercent: Double,
        direction: SwingDirection,
        notes: String
    ) {
        val ticker = LiveQuoteParsers.normalizeTicker(rawTicker)
        if (ticker.length < 4 || thresholdPercent <= 0.0) {
            _statusBannerMessage.value = "Informe um ticker válido (ex: PETR4, HGLG11) e um percentual acima de 0%."
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.insertSwingAlert(
                    AssetSwingAlertEntity(
                        ticker = ticker,
                        thresholdPercent = thresholdPercent,
                        direction = direction.code,
                        isEnabled = true,
                        notes = notes.trim()
                    )
                )
            }
            SmartAlertWorkScheduler.syncSchedule(getApplication(), hasActiveAlertsOrGoals = true)
            _statusBannerMessage.value =
                "Alerta de variação brusca criado para $ticker (${direction.shortSymbol}${"%.1f".format(thresholdPercent)}%)."

            val existingLive = dashboardState.value.liveQuotesByTicker[ticker]
            if (existingLive != null) {
                evaluateSharpSwingsAgainstQuotes(mapOf(ticker to existingLive))
            } else {
                liveMarketRepository.fetchLiveQuote(ticker).onSuccess { quote ->
                    _liveQuotesByTicker.value = _liveQuotesByTicker.value + (quote.ticker to quote)
                    withContext(Dispatchers.IO) { repository.saveCachedLiveQuote(quote) }
                    evaluateSharpSwingsAgainstQuotes(mapOf(quote.ticker.uppercase() to quote))
                }
            }
        }
    }

    fun toggleSwingAlert(id: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setSwingAlertEnabled(id, enabled)
        }
    }

    fun resetSwingAlert(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.resetSwingAlertCooldown(id)
            }
            _statusBannerMessage.value = "Alerta de variação rearmado para disparar novamente hoje."
        }
    }

    fun deleteSwingAlert(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSwingAlert(id)
        }
    }

    fun addInvestmentGoal(
        title: String,
        goalType: InvestmentGoalType,
        rawTicker: String,
        targetValue: Double,
        notes: String
    ) {
        val cleanTicker = if (goalType.requiresTicker) LiveQuoteParsers.normalizeTicker(rawTicker) else ""
        if (goalType.requiresTicker && cleanTicker.length < 4) {
            _statusBannerMessage.value = "Informe um ticker válido para esta meta por ativo."
            return
        }
        if (targetValue <= 0.0) {
            _statusBannerMessage.value = "Informe um valor alvo maior que zero para a meta."
            return
        }
        val resolvedTitle = title.trim().ifBlank {
            if (cleanTicker.isNotBlank()) "${goalType.label} • $cleanTicker" else goalType.label
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.insertInvestmentGoal(
                    InvestmentGoalEntity(
                        title = resolvedTitle,
                        goalType = goalType.code,
                        ticker = cleanTicker,
                        targetValue = targetValue,
                        isEnabled = true,
                        hasTriggered = false,
                        notes = notes.trim()
                    )
                )
            }
            SmartAlertWorkScheduler.syncSchedule(getApplication(), hasActiveAlertsOrGoals = true)
            _statusBannerMessage.value =
                "Meta '$resolvedTitle' criada (${SmartAlertEvaluator.formatGoalValue(goalType, targetValue)})."
            evaluateInvestmentGoalsNow(notifyIfNoneReached = false)
        }
    }

    fun toggleInvestmentGoal(id: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setInvestmentGoalEnabled(id, enabled)
        }
    }

    fun rearmInvestmentGoal(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.rearmInvestmentGoal(id)
            }
            _statusBannerMessage.value = "Meta reativada para monitoramento contínuo."
        }
    }

    fun deleteInvestmentGoal(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteInvestmentGoal(id)
        }
    }

    fun clearAlertHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.clearNotificationHistory()
            }
            _statusBannerMessage.value = "Histórico de alertas locais limpo."
        }
    }

    fun sendTestSmartNotification() {
        viewModelScope.launch {
            val st = dashboardState.value
            val sampleTicker = st.realPositions.firstOrNull()?.ticker ?: "VALE3"
            val sampleEquity = st.realHhi.totalEquityBrl
            if (!SmartAlertNotificationHelper.hasNotificationPermission(getApplication())) {
                _statusBannerMessage.value =
                    "Ative a permissão de notificações do Android para receber alertas locais."
                return@launch
            }
            val delivered = SmartAlertNotificationHelper.sendTestSmartNotification(
                context = getApplication(),
                sampleTicker = sampleTicker,
                sampleEquityBrl = sampleEquity
            )
            if (delivered) {
                withContext(Dispatchers.IO) {
                    repository.insertNotificationHistory(
                        AlertNotificationHistoryEntity(
                            category = "GOAL",
                            ticker = sampleTicker,
                            title = "Notificação de Teste Enviada",
                            message = "Teste local disparado com sucesso para variações bruscas ($sampleTicker) e metas da carteira.",
                            deliveredAsPush = true
                        )
                    )
                }
                _statusBannerMessage.value = "Notificação local de teste enviada com sucesso!"
            } else {
                _statusBannerMessage.value = "Não foi possível enviar a notificação de teste. Verifique as permissões do Android."
            }
        }
    }

    fun checkAllSmartAlertsNow() {
        if (_isCheckingSmartAlerts.value) return
        viewModelScope.launch {
            _isCheckingSmartAlerts.value = true
            try {
                val activeSwings = withContext(Dispatchers.IO) { repository.getActiveSwingAlerts() }
                val activeGoals = withContext(Dispatchers.IO) { repository.getActiveInvestmentGoals() }
                val activePrices = withContext(Dispatchers.IO) { repository.getActivePriceTargetAlerts() }

                if (activeSwings.isEmpty() && activeGoals.isEmpty() && activePrices.isEmpty()) {
                    _statusBannerMessage.value = "Nenhum alerta de variação, meta financeira ou preço alvo ativo."
                    return@launch
                }

                val tickersToFetch = (activeSwings.map { it.ticker } + activePrices.map { it.ticker })
                    .map(LiveQuoteParsers::normalizeTicker)
                    .filter { it.length >= 4 }
                    .distinct()

                val fetchedQuotes = if (tickersToFetch.isNotEmpty()) {
                    val fetched = liveMarketRepository.fetchBatchQuotes(tickersToFetch)
                    if (fetched.isNotEmpty()) {
                        _liveQuotesByTicker.value = _liveQuotesByTicker.value + fetched
                        withContext(Dispatchers.IO) { repository.saveCachedLiveQuotes(fetched.values) }
                    }
                    fetched
                } else {
                    emptyMap()
                }

                val quotesPool = dashboardState.value.liveQuotesByTicker + fetchedQuotes
                val triggeredSwings = if (quotesPool.isNotEmpty()) {
                    evaluateSharpSwingsAgainstQuotes(quotesPool)
                } else {
                    emptyList()
                }

                if (fetchedQuotes.isNotEmpty() && activePrices.isNotEmpty()) {
                    evaluateLiveQuotesAgainstAlerts(fetchedQuotes, notifyIfNoneTriggered = false)
                }

                val reachedGoals = evaluateInvestmentGoalsNow(notifyIfNoneReached = false)

                val summaryParts = buildList {
                    if (triggeredSwings.isNotEmpty()) add("${triggeredSwings.size} variação(ões) brusca(s)")
                    if (reachedGoals.isNotEmpty()) add("${reachedGoals.size} meta(s) alcançada(s)")
                }
                _statusBannerMessage.value = if (summaryParts.isNotEmpty()) {
                    "Alertas disparados: ${summaryParts.joinToString(" e ")}!"
                } else {
                    "Verificação concluída (${activeSwings.size} alerta(s) de variação e ${activeGoals.size} meta(s) monitorada(s)). Nenhum gatilho atingido agora."
                }
            } finally {
                _isCheckingSmartAlerts.value = false
            }
        }
    }

    private suspend fun evaluateSharpSwingsAgainstQuotes(
        quotes: Map<String, LiveMarketQuote>
    ): List<String> {
        val activeSwings = withContext(Dispatchers.IO) { repository.getActiveSwingAlerts() }
        if (activeSwings.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val todayKey = SmartAlertEvaluator.todayDayKey(now)
        val triggeredLabels = mutableListOf<String>()

        activeSwings.forEach { alert ->
            val key = alert.ticker.trim().uppercase()
            val quote = quotes[key] ?: return@forEach
            if (SmartAlertEvaluator.isSharpSwingTriggered(alert, quote.changePercent1d, todayKey)) {
                val delivered = SmartAlertNotificationHelper.sendSharpSwingNotification(
                    context = getApplication(),
                    alert = alert,
                    changePercent1d = quote.changePercent1d,
                    currentPriceBrl = quote.price,
                    sourceLabel = quote.primarySourceLabel
                )
                withContext(Dispatchers.IO) {
                    repository.markSwingAlertTriggered(
                        id = alert.id,
                        changePercent = quote.changePercent1d,
                        priceBrl = quote.price,
                        triggeredAt = now,
                        dayKey = todayKey
                    )
                    val signed = "%+.2f%%".format(quote.changePercent1d)
                    val move = if (quote.changePercent1d >= 0.0) "Alta brusca" else "Queda brusca"
                    repository.insertNotificationHistory(
                        AlertNotificationHistoryEntity(
                            category = "SWING",
                            ticker = key,
                            title = "$move em $key ($signed)",
                            message = "$key variou $signed (cotação R$ ${"%.2f".format(quote.price)}) via ${quote.primarySourceLabel}.",
                            deliveredAsPush = delivered,
                            createdAt = now
                        )
                    )
                }
                triggeredLabels.add("$key (${"%+.2f%%".format(quote.changePercent1d)})")
            } else {
                withContext(Dispatchers.IO) {
                    repository.updateSwingLastEvaluated(
                        id = alert.id,
                        changePercent = quote.changePercent1d,
                        priceBrl = quote.price,
                        evaluatedAt = now
                    )
                }
            }
        }
        return triggeredLabels
    }

    private suspend fun evaluateInvestmentGoalsNow(notifyIfNoneReached: Boolean): List<String> {
        val activeGoals = withContext(Dispatchers.IO) { repository.getActiveInvestmentGoals() }
        if (activeGoals.isEmpty()) return emptyList()

        val st = dashboardState.value
        val totalEquity = st.realHhi.totalEquityBrl
        val divSummary = DividendMonitorCalculator.compute(
            realPositions = st.realPositions,
            marketAssets = st.marketAssets,
            operations = st.operations
        )
        val monthlyDiv = maxOf(divSummary.projectedMonthlyBrl, divSummary.receivedMonthlyBrl)
        val now = System.currentTimeMillis()
        val reachedNames = mutableListOf<String>()

        activeGoals.forEach { goal ->
            val snap = SmartAlertEvaluator.computeGoalProgress(
                goal = goal,
                totalEquityBrl = totalEquity,
                estimatedMonthlyDividendsBrl = monthlyDiv,
                realPositions = st.realPositions
            )
            if (snap.isReached) {
                val delivered = SmartAlertNotificationHelper.sendInvestmentGoalNotification(
                    context = getApplication(),
                    goal = goal,
                    currentValue = snap.currentValue,
                    targetValue = snap.targetValue
                )
                withContext(Dispatchers.IO) {
                    repository.markInvestmentGoalTriggered(goal.id, snap.currentValue, now)
                    val gType = InvestmentGoalType.fromCode(goal.goalType)
                    repository.insertNotificationHistory(
                        AlertNotificationHistoryEntity(
                            category = "GOAL",
                            ticker = goal.ticker,
                            title = "Meta alcançada: ${goal.title}",
                            message = "${gType.label}: ${SmartAlertEvaluator.formatGoalValue(gType, snap.currentValue)} (alvo ${SmartAlertEvaluator.formatGoalValue(gType, snap.targetValue)}).",
                            deliveredAsPush = delivered,
                            createdAt = now
                        )
                    )
                }
                reachedNames.add(goal.title)
            } else {
                withContext(Dispatchers.IO) {
                    repository.updateInvestmentGoalProgress(goal.id, snap.currentValue)
                }
            }
        }

        if (reachedNames.isNotEmpty()) {
            _statusBannerMessage.value = "Meta(s) alcançada(s): ${reachedNames.joinToString(", ")}!"
        } else if (notifyIfNoneReached) {
            _statusBannerMessage.value = "Nenhuma meta de investimento alcançada ainda."
        }
        return reachedNames
    }
}

package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.importexport.ImportParseResult
import com.example.data.importexport.SpreadsheetImporter
import com.example.data.local.AppPreferencesState
import com.example.data.local.CryptoVirtualPositionEntity
import com.example.data.local.ImportHistoryEntity
import com.example.data.local.InvestDatabase
import com.example.data.local.PortfolioOperationEntity
import com.example.data.local.PreferencesManager
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
import com.example.data.repository.PortfolioRepository
import com.example.data.repository.ProfitChartConnector
import com.example.data.repository.ReportExporter
import com.example.data.repository.SupabaseFutureAdapter
import com.example.domain.model.AiAgentRole
import com.example.domain.model.AiRecommendation
import com.example.domain.model.AssetClass
import com.example.domain.model.BenchmarkSeries
import com.example.domain.model.HhiRiskReport
import com.example.domain.model.LiveMarketQuote
import com.example.domain.model.LiveQuoteLookupState
import com.example.domain.model.MarketAsset
import com.example.domain.model.OperationType
import com.example.domain.model.PortfolioItemView
import com.example.domain.model.PortfolioType
import com.example.domain.model.ProfitChartConnectionState
import com.example.domain.model.SimulationScenarioResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val profitConnectionState: ProfitChartConnectionState = ProfitChartConnectionState.Disconnected
)

class InvestViewModel(application: Application) : AndroidViewModel(application) {

    private val db = InvestDatabase.getInstance(application)
    private val prefsManager = PreferencesManager(application)
    private val repository = PortfolioRepository(application, db, prefsManager)
    private val liveMarketRepository = LiveMarketDataRepository()
    private val profitConnector = ProfitChartConnector(application)
    private val supabaseAdapter = SupabaseFutureAdapter()
    private val githubAdapter = GitHubFutureAdapter()

    private val _selectedPortfolioType = MutableStateFlow(PortfolioType.REAL)
    val selectedPortfolioType: StateFlow<PortfolioType> = _selectedPortfolioType.asStateFlow()

    private val _liveQuotesByTicker = MutableStateFlow<Map<String, LiveMarketQuote>>(emptyMap())
    private val _liveLookupState = MutableStateFlow<LiveQuoteLookupState>(LiveQuoteLookupState.Idle)
    private val _isLiveBatchRefreshing = MutableStateFlow(false)
    private val _profitConnectionState = MutableStateFlow<ProfitChartConnectionState>(ProfitChartConnectionState.Disconnected)
    private var profitPollingJob: Job? = null

    private val _importFeedback = MutableStateFlow<ImportParseResult?>(null)
    val importFeedback: StateFlow<ImportParseResult?> = _importFeedback.asStateFlow()

    private val _pendingImportPreview = MutableStateFlow<ImportParseResult?>(null)
    val pendingImportPreview: StateFlow<ImportParseResult?> = _pendingImportPreview.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    val dashboardState: StateFlow<InvestDashboardState> = combine(
        repository.positionsFlow,
        repository.operationsFlow,
        repository.importHistoryFlow,
        repository.preferencesFlow,
        repository.strategyCriteriaFlow,
        repository.strategyChangeLogFlow,
        repository.cryptoVirtualPositionsFlow,
        repository.savedAiRecommendationsFlow,
        _liveQuotesByTicker,
        _liveLookupState,
        _isLiveBatchRefreshing,
        _profitConnectionState
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

        val realPos = positions.filter { it.portfolioType == PortfolioType.REAL }
        val simPos = positions.filter { it.portfolioType == PortfolioType.SIMULATION }
        val latestReal = imports.firstOrNull { it.isActiveRealReference } ?: imports.firstOrNull()

        val updatedCatalog = MarketCatalogRepository.catalogAssets.map { base ->
            val live = liveQuotes[base.ticker.uppercase()]
            if (live != null) LiveMarketDataRepository.mergeLiveQuoteIntoMarketAsset(base, live) else base
        }
        val catalogTickers = updatedCatalog.map { it.ticker.uppercase() }.toSet()
        val extraLiveAssets = liveQuotes.values
            .filter { it.ticker.uppercase() !in catalogTickers }
            .map { LiveMarketDataRepository.mergeLiveQuoteIntoMarketAsset(null, it) }

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
            profitConnectionState = profitState
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
                if (result.positions.isNotEmpty()) {
                    _pendingImportPreview.value = result
                } else {
                    _importFeedback.value = result
                    _statusBannerMessage.value = result.warnings.firstOrNull()
                        ?: "Nenhum ativo válido encontrado na planilha."
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
                withContext(Dispatchers.IO) {
                    repository.commitRealPortfolioImport(pending)
                }
                _pendingImportPreview.value = null
                _importFeedback.value = pending
                _statusBannerMessage.value =
                    "Importação '${pending.fileName}' (${pending.positions.size} ativos) definida como referência da Carteira Real."
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

    fun buildPortfolioCsvExport(type: PortfolioType): String {
        val st = dashboardState.value
        val positions = if (type == PortfolioType.REAL) st.realPositions else st.simPositions
        val cash = if (type == PortfolioType.REAL) st.preferences.cashRealBrl else st.preferences.cashSimBrl
        return SpreadsheetImporter.exportPositionsToCsv(positions, cash)
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
        dataComDay: Int?
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
                dataComDay = dataComDay
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
        notes: String
    ) {
        viewModelScope.launch {
            repository.recordOperation(portfolioType, ticker, operationType, quantity, unitPrice, notes)
            _statusBannerMessage.value = "Operação ${operationType.label} registrada localmente."
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
        existingSourceLabel: String
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
                        existingSourceLabel = existingSourceLabel
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
                    _statusBannerMessage.value = "${fetched.size} ativo(s) atualizado(s) em tempo real (B3 / Mercado)."
                } else {
                    _statusBannerMessage.value = "Nenhuma cotação ao vivo pôde ser obtida. Verifique a conexão com a internet."
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
        val live = _liveQuotesByTicker.value[clean] ?: return
        viewModelScope.launch {
            val updatedCount = withContext(Dispatchers.IO) {
                repository.applyLivePriceToPortfolio(clean, live.price)
            }
            _statusBannerMessage.value = if (updatedCount > 0) {
                "Cotação ao vivo de $clean aplicada na Carteira."
            } else {
                "$clean não encontrado na Carteira salva."
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
}

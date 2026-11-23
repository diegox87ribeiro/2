package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ImportHistoryEntity
import com.example.data.local.SavedAiRecommendationEntity
import com.example.data.repository.CloudConsolidatedReport
import com.example.data.repository.FirestoreSyncRepository
import com.example.data.repository.GeminiFirestoreAssetAuditService
import com.example.domain.model.CloudAsset
import com.example.domain.model.ConsolidatedExecutiveReport
import com.example.domain.model.FirestoreAssetAuditSummary
import com.example.domain.model.HhiRiskReport
import com.example.domain.model.MarketAsset
import com.example.domain.model.PortfolioItemView
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CloudReportsUiState {
    data object Loading : CloudReportsUiState
    data class Success(val reports: List<CloudConsolidatedReport>) : CloudReportsUiState
    data class Error(val message: String) : CloudReportsUiState
}

sealed interface CloudAssetsUiState {
    data object Loading : CloudAssetsUiState
    data class Success(val assets: List<CloudAsset>) : CloudAssetsUiState
    data class Error(val message: String) : CloudAssetsUiState
}

sealed interface CloudAssetAuditsUiState {
    data object Loading : CloudAssetAuditsUiState
    data class Success(val audits: List<FirestoreAssetAuditSummary>) : CloudAssetAuditsUiState
    data class Error(val message: String) : CloudAssetAuditsUiState
}

sealed interface AssetAuditRunState {
    data object Idle : AssetAuditRunState
    data class Loading(val assetId: String) : AssetAuditRunState
    data class Success(val summary: FirestoreAssetAuditSummary) : AssetAuditRunState
    data class Error(val message: String) : AssetAuditRunState
}

class CloudSyncViewModel(
    private val repository: FirestoreSyncRepository?,
    private val currentUserId: String,
    private val initError: String? = null,
    private val assetAuditService: GeminiFirestoreAssetAuditService? = repository?.let {
        GeminiFirestoreAssetAuditService(it)
    }
) : ViewModel() {

    val cloudReports: StateFlow<CloudReportsUiState> = if (repository == null) {
        MutableStateFlow(
            CloudReportsUiState.Error(initError ?: "Firestore indisponível neste dispositivo.")
        ).asStateFlow()
    } else {
        repository.observeConsolidatedReports(currentUserId)
            .map<List<CloudConsolidatedReport>, CloudReportsUiState> { CloudReportsUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(CloudReportsUiState.Error(rootError.message ?: "Falha ao sincronizar relatórios na nuvem"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = CloudReportsUiState.Loading
            )
    }

    val cloudAssets: StateFlow<CloudAssetsUiState> = if (repository == null) {
        MutableStateFlow(
            CloudAssetsUiState.Error(initError ?: "Firestore indisponível neste dispositivo.")
        ).asStateFlow()
    } else {
        repository.observeCloudAssets(currentUserId)
            .map<List<CloudAsset>, CloudAssetsUiState> { CloudAssetsUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(CloudAssetsUiState.Error(rootError.message ?: "Falha ao carregar ativos do Firestore"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = CloudAssetsUiState.Loading
            )
    }

    val assetAudits: StateFlow<CloudAssetAuditsUiState> = if (repository == null) {
        MutableStateFlow(
            CloudAssetAuditsUiState.Error(initError ?: "Firestore indisponível neste dispositivo.")
        ).asStateFlow()
    } else {
        repository.observeAssetAudits(currentUserId)
            .map<List<com.example.domain.model.CloudAssetAudit>, CloudAssetAuditsUiState> { list ->
                CloudAssetAuditsUiState.Success(list.map { it.toDomainSummary() })
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(CloudAssetAuditsUiState.Error(rootError.message ?: "Falha ao carregar auditorias salvas no Firestore"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = CloudAssetAuditsUiState.Loading
            )
    }

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isSyncingAssets = MutableStateFlow(false)
    val isSyncingAssets: StateFlow<Boolean> = _isSyncingAssets.asStateFlow()

    private val _activeAuditRunState = MutableStateFlow<AssetAuditRunState>(AssetAuditRunState.Idle)
    val activeAuditRunState: StateFlow<AssetAuditRunState> = _activeAuditRunState.asStateFlow()

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun clearActiveAuditRunState() {
        _activeAuditRunState.value = AssetAuditRunState.Idle
    }

    fun selectSavedAudit(summary: FirestoreAssetAuditSummary) {
        _activeAuditRunState.value = AssetAuditRunState.Success(summary)
    }

    fun syncAssetsToFirestore(
        positions: List<PortfolioItemView>,
        marketAssets: List<MarketAsset>
    ) {
        val activeRepo = repository
        if (activeRepo == null) {
            _syncMessage.value = initError ?: "Firestore indisponível para sincronizar ativos."
            return
        }
        viewModelScope.launch {
            _isSyncingAssets.value = true
            val res = activeRepo.syncPortfolioAssetsToCloud(positions, marketAssets)
            _isSyncingAssets.value = false
            _syncMessage.value = res.fold(
                onSuccess = { count -> "$count ativo(s) sincronizado(s) no Cloud Firestore para auditoria Gemini." },
                onFailure = { err -> "Erro ao sincronizar ativos no Firestore: ${err.localizedMessage ?: "permissão/rede"}" }
            )
        }
    }

    fun runGeminiAssetAudit(assetId: String) {
        val service = assetAuditService
        if (service == null) {
            _activeAuditRunState.value = AssetAuditRunState.Error(
                initError ?: "Serviço de auditoria Gemini + Firestore indisponível neste dispositivo."
            )
            return
        }
        val cleanId = assetId.trim()
        if (cleanId.isBlank()) {
            _activeAuditRunState.value = AssetAuditRunState.Error("Selecione um ativo válido no Firestore para auditar.")
            return
        }
        viewModelScope.launch {
            _activeAuditRunState.value = AssetAuditRunState.Loading(cleanId)
            val result = service.analyzeFirestoreAsset(
                assetId = cleanId,
                includeRelatedInsights = true,
                persistAuditToFirestore = true
            )
            _activeAuditRunState.value = result.fold(
                onSuccess = { summary ->
                    _syncMessage.value = "Auditoria executiva de ${summary.ticker} gerada pelo Gemini e salva no Firestore."
                    AssetAuditRunState.Success(summary)
                },
                onFailure = { err ->
                    AssetAuditRunState.Error(
                        err.localizedMessage ?: err.message ?: "Falha ao executar auditoria Gemini para $cleanId."
                    )
                }
            )
        }
    }

    fun syncConsolidatedReportToCloud(
        report: ConsolidatedExecutiveReport,
        hhiReport: HhiRiskReport,
        latestImport: ImportHistoryEntity?,
        positionsCount: Int
    ) {
        val activeRepo = repository
        if (activeRepo == null) {
            _syncMessage.value = initError ?: "Firestore indisponível para sincronização."
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            val insights = listOfNotNull(report.analystInsight, report.gestorInsight, report.auditorInsight)
            insights.forEach { entity: SavedAiRecommendationEntity ->
                activeRepo.syncAgentInsight(entity)
            }
            val reportRes = activeRepo.saveConsolidatedReport(report, hhiReport)
            activeRepo.syncPortfolioSnapshot(latestImport, hhiReport, positionsCount)
            _isSyncing.value = false
            _syncMessage.value = reportRes.fold(
                onSuccess = { "Relatório consolidado e pareceres salvos no Firebase Firestore." },
                onFailure = { err -> "Erro ao salvar no Firestore: ${err.localizedMessage ?: "permissão/rede"}" }
            )
        }
    }
}

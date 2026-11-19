package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatMessageEntity
import com.example.data.local.InvestDatabase
import com.example.data.local.SavedAiRecommendationEntity
import com.example.data.repository.AiRepository
import com.example.data.repository.ChatTurn
import com.example.data.repository.FirebaseAiRepository
import com.example.data.repository.GeminiTaskModel
import com.example.data.repository.MarketCatalogRepository
import com.example.domain.model.AiAgentRole
import com.example.domain.model.AiRecommendation
import com.example.domain.model.HhiRiskReport
import com.example.domain.model.MarketAsset
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "AiAgentsViewModel"

enum class AnalysisHorizon(val label: String) {
    CURTO("Curto Prazo (1–6m)"),
    MEDIO("Médio Prazo (6–24m)"),
    LONGO("Longo Prazo (Renda / >2a)")
}

enum class AnalysisFocus(val label: String) {
    COMPLETA("Análise Completa"),
    DIVIDENDOS_DATACOM("Dividendos & Datas-Com G1–G4"),
    RISCO_HHI("Risco & Concentração HHI"),
    AUDITORIA_FONTES("Auditoria de Dados & Fontes")
}

data class StructuredPromptInput(
    val selectedRole: AiAgentRole = AiAgentRole.ANALISTA,
    val selectedTicker: String = MarketCatalogRepository.catalogAssets.first().ticker,
    val horizon: AnalysisHorizon = AnalysisHorizon.LONGO,
    val focus: AnalysisFocus = AnalysisFocus.COMPLETA,
    val customQuestion: String = "",
    val showPromptPreview: Boolean = false
)

sealed interface AiAgentUiState {
    data object Idle : AiAgentUiState
    data object Loading : AiAgentUiState
    data class Success(val recommendation: AiRecommendation) : AiAgentUiState
    data class Error(val message: String) : AiAgentUiState
}

sealed interface ConsolidatedRunState {
    data object Idle : ConsolidatedRunState
    data class Loading(val ticker: String) : ConsolidatedRunState
    data class Completed(val ticker: String, val generatedCount: Int) : ConsolidatedRunState
    data class Error(val message: String) : ConsolidatedRunState
}

sealed interface AiChatUiState {
    data object Idle : AiChatUiState
    data class Loading(val role: AiAgentRole, val modelLabel: String) : AiChatUiState
    data class Streaming(
        val partialText: String,
        val role: AiAgentRole,
        val modelLabel: String
    ) : AiChatUiState
    data class Error(val message: String) : AiChatUiState
}

/**
 * ViewModel dedicado para gerenciar a interação estruturada e o chat multi-turno
 * com os agentes especializados (Analista, Gestor e Auditor) via Firebase AI Logic (Gemini).
 */
class AiAgentsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = InvestDatabase.getInstance(application)
    private val aiDao = db.aiRecommendationDao()
    private val chatDao = db.chatMessageDao()
    private val aiRepository: AiRepository = FirebaseAiRepository()

    private fun isFirebaseReady(): Boolean {
        val app = getApplication<Application>()
        return runCatching {
            FirebaseApp.getApps(app).isNotEmpty() || FirebaseApp.initializeApp(app) != null
        }.getOrDefault(false)
    }

    val isLiveGeminiEnabled: Boolean = isFirebaseReady() && aiRepository.isLiveGeminiEnabled

    private val _promptInput = MutableStateFlow(StructuredPromptInput())
    val promptInput: StateFlow<StructuredPromptInput> = _promptInput.asStateFlow()

    private val _uiState = MutableStateFlow<AiAgentUiState>(AiAgentUiState.Idle)
    val uiState: StateFlow<AiAgentUiState> = _uiState.asStateFlow()

    private val _consolidatedRunState = MutableStateFlow<ConsolidatedRunState>(ConsolidatedRunState.Idle)
    val consolidatedRunState: StateFlow<ConsolidatedRunState> = _consolidatedRunState.asStateFlow()

    private val _chatModelOption = MutableStateFlow(GeminiTaskModel.GENERAL)
    val chatModelOption: StateFlow<GeminiTaskModel> = _chatModelOption.asStateFlow()

    private val _chatDraft = MutableStateFlow("")
    val chatDraft: StateFlow<String> = _chatDraft.asStateFlow()

    private val _chatFocusedTicker = MutableStateFlow<String?>(null)
    val chatFocusedTicker: StateFlow<String?> = _chatFocusedTicker.asStateFlow()

    private val _chatUiState = MutableStateFlow<AiChatUiState>(AiChatUiState.Idle)
    val chatUiState: StateFlow<AiChatUiState> = _chatUiState.asStateFlow()

    val savedHistory: StateFlow<List<SavedAiRecommendationEntity>> = aiDao.observeSaved()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val chatMessages: StateFlow<List<ChatMessageEntity>> = chatDao.observeMessages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun selectRole(role: AiAgentRole) {
        _promptInput.update { it.copy(selectedRole = role) }
    }

    fun selectTicker(ticker: String) {
        _promptInput.update { it.copy(selectedTicker = ticker) }
    }

    fun selectHorizon(horizon: AnalysisHorizon) {
        _promptInput.update { it.copy(horizon = horizon) }
    }

    fun selectFocus(focus: AnalysisFocus) {
        _promptInput.update { it.copy(focus = focus) }
    }

    fun updateCustomQuestion(text: String) {
        _promptInput.update { it.copy(customQuestion = text) }
    }

    fun togglePromptPreview() {
        _promptInput.update { it.copy(showPromptPreview = !it.showPromptPreview) }
    }

    fun resetResult() {
        _uiState.value = AiAgentUiState.Idle
    }

    fun clearConsolidatedStatus() {
        _consolidatedRunState.value = ConsolidatedRunState.Idle
    }

    fun selectChatModel(option: GeminiTaskModel) {
        _chatModelOption.value = option
    }

    fun updateChatDraft(text: String) {
        _chatDraft.value = text
    }

    fun selectChatFocusedTicker(ticker: String?) {
        _chatFocusedTicker.value = ticker
    }

    fun clearChatError() {
        if (_chatUiState.value is AiChatUiState.Error) {
            _chatUiState.value = AiChatUiState.Idle
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            chatDao.clearAll()
            _chatUiState.value = AiChatUiState.Idle
        }
    }

    fun sendChatMessage(
        messageText: String = _chatDraft.value,
        focusedAsset: MarketAsset?,
        hhiReport: HhiRiskReport,
        includePersonalPortfolio: Boolean
    ) {
        val trimmed = messageText.trim()
        if (trimmed.isEmpty()) return
        if (_chatUiState.value is AiChatUiState.Loading || _chatUiState.value is AiChatUiState.Streaming) {
            return
        }

        if (!isFirebaseReady()) {
            _chatUiState.value = AiChatUiState.Error(
                "Gemini ao vivo indisponível no Modo Local (APK compilado sem configuração Firebase)."
            )
            return
        }

        val activeRole = _promptInput.value.selectedRole
        val modelOption = _chatModelOption.value
        val historySnapshot = chatMessages.value.map {
            ChatTurn(senderRole = it.senderRole, text = it.content)
        }

        _chatDraft.value = ""
        viewModelScope.launch {
            _chatUiState.value = AiChatUiState.Loading(activeRole, modelOption.label)
            try {
                chatDao.insert(
                    ChatMessageEntity(
                        senderRole = "USER",
                        agentRole = activeRole.name,
                        modelName = modelOption.modelId,
                        content = trimmed,
                        usedPersonalData = includePersonalPortfolio
                    )
                )

                val accumulated = StringBuilder()
                withTimeout(60_000L) {
                    aiRepository.streamChatReply(
                        role = activeRole,
                        modelOption = modelOption,
                        history = historySnapshot,
                        userMessage = trimmed,
                        focusedAsset = focusedAsset,
                        hhiReport = hhiReport,
                        includePersonalPortfolio = includePersonalPortfolio
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        _chatUiState.value = AiChatUiState.Streaming(
                            partialText = accumulated.toString(),
                            role = activeRole,
                            modelLabel = modelOption.label
                        )
                    }
                }

                val finalReply = accumulated.toString().trim()
                if (finalReply.isEmpty()) {
                    _chatUiState.value = AiChatUiState.Error("O modelo Gemini retornou uma resposta vazia.")
                } else {
                    chatDao.insert(
                        ChatMessageEntity(
                            senderRole = "MODEL",
                            agentRole = activeRole.name,
                            modelName = modelOption.modelId,
                            content = finalReply,
                            usedPersonalData = includePersonalPortfolio
                        )
                    )
                    _chatUiState.value = AiChatUiState.Idle
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini multi-turn chat timed out", e)
                _chatUiState.value = AiChatUiState.Error(
                    "Tempo limite de 60s atingido na resposta do chat. Tente novamente."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini multi-turn chat failed", e)
                _chatUiState.value = AiChatUiState.Error(
                    e.localizedMessage ?: "Falha ao conversar com o Gemini."
                )
            }
        }
    }

    fun buildStructuredPromptPreview(
        asset: MarketAsset,
        hhiReport: HhiRiskReport,
        includePersonalPortfolio: Boolean
    ): String {
        val input = _promptInput.value
        val portfolioLine = if (includePersonalPortfolio && hhiReport.totalInvestedBrl > 0) {
            "Incluído (autorizado): HHI ${hhiReport.hhiScore.toInt()} pts • Patrimônio R$ ${"%.2f".format(hhiReport.totalEquityBrl)}"
        } else {
            "Isolado localmente (planilhas pessoais não enviadas)"
        }
        return """
            [AGENTE]: ${input.selectedRole.title} (${input.selectedRole.subtitle})
            [ATIVO]: ${asset.ticker} — ${asset.name} (${asset.assetClass.label})
            [HORIZONTE]: ${input.horizon.label}
            [FOCO]: ${input.focus.label}
            [FONTES PRIORIZADAS]: B3, CVM, Banco Central, ANBIMA, RI (${asset.sourceLabel})
            [DADOS PESSOAIS]: $portfolioLine
            [PERGUNTA]: ${input.customQuestion.ifBlank { "Gerar tese, indicadores, riscos, cenários e conclusão." }}
        """.trimIndent()
    }

    fun submitStructuredAnalysis(
        asset: MarketAsset,
        hhiReport: HhiRiskReport,
        includePersonalPortfolio: Boolean
    ) {
        if (!isFirebaseReady()) {
            _uiState.value = AiAgentUiState.Error(
                "Gemini ao vivo indisponível no Modo Local (APK compilado sem configuração Firebase)."
            )
            return
        }
        val input = _promptInput.value
        viewModelScope.launch {
            _uiState.value = AiAgentUiState.Loading
            try {
                val recommendation = withTimeout(60_000L) {
                    aiRepository.generateRecommendation(
                        role = input.selectedRole,
                        asset = asset,
                        hhiReport = hhiReport,
                        includePersonalPortfolio = includePersonalPortfolio,
                        customPrompt = input.customQuestion,
                        horizonLabel = input.horizon.label,
                        focusLabel = input.focus.label
                    )
                }
                aiDao.insert(recommendation.toSavedEntity())
                _uiState.value = AiAgentUiState.Success(recommendation)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini AI structured analysis timed out", e)
                _uiState.value = AiAgentUiState.Error(
                    "Tempo limite de 60s atingido ao consultar o Gemini. Tente novamente."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI generation failed", e)
                _uiState.value = AiAgentUiState.Error(
                    e.localizedMessage ?: "Falha ao executar análise estruturada com Gemini."
                )
            }
        }
    }

    fun runConsolidatedThreeAgents(
        asset: MarketAsset,
        hhiReport: HhiRiskReport,
        includePersonalPortfolio: Boolean
    ) {
        if (!isFirebaseReady()) {
            _consolidatedRunState.value = ConsolidatedRunState.Error(
                "Gemini ao vivo indisponível no Modo Local (APK compilado sem configuração Firebase)."
            )
            return
        }
        viewModelScope.launch {
            _consolidatedRunState.value = ConsolidatedRunState.Loading(asset.ticker)
            try {
                val results = withTimeout(90_000L) {
                    coroutineScope {
                        AiAgentRole.entries.map { role ->
                            val focus = when (role) {
                                AiAgentRole.ANALISTA -> AnalysisFocus.COMPLETA.label
                                AiAgentRole.GESTOR -> AnalysisFocus.RISCO_HHI.label
                                AiAgentRole.AUDITOR -> AnalysisFocus.AUDITORIA_FONTES.label
                            }
                            async {
                                aiRepository.generateRecommendation(
                                    role = role,
                                    asset = asset,
                                    hhiReport = hhiReport,
                                    includePersonalPortfolio = includePersonalPortfolio,
                                    customPrompt = "Gerar síntese executiva para o Painel Consolidado de Relatórios Gerenciais.",
                                    horizonLabel = AnalysisHorizon.LONGO.label,
                                    focusLabel = focus
                                )
                            }
                        }.awaitAll()
                    }
                }
                results.forEach { rec -> aiDao.insert(rec.toSavedEntity()) }
                _consolidatedRunState.value = ConsolidatedRunState.Completed(asset.ticker, results.size)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Consolidated 3-agent run timed out", e)
                _consolidatedRunState.value = ConsolidatedRunState.Error(
                    "Tempo limite de 90s atingido ao consolidar os 3 agentes."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Consolidated 3-agent run failed", e)
                _consolidatedRunState.value = ConsolidatedRunState.Error(
                    e.localizedMessage ?: "Erro ao consolidar pareceres dos 3 agentes."
                )
            }
        }
    }

    private fun AiRecommendation.toSavedEntity(): SavedAiRecommendationEntity {
        val indicatorsText = indicadoresRelevantes.joinToString(" • ") { "${it.first}: ${it.second}" }
        val risksText = riscosIdentificados.joinToString(" | ")
        val scenariosText = "Otimista: $cenarioOtimista | Base: $cenarioBase | Pessimista: $cenarioPessimista"
        return SavedAiRecommendationEntity(
            agentRole = agentRole.name,
            ticker = ticker,
            conclusion = conclusao.label,
            confidence = nivelConfianca,
            thesis = tese,
            usedPersonalData = usedPersonalPortfolioData,
            sourceLabel = fonteConsultada,
            dataBase = dataBase,
            indicatorsSummary = indicatorsText,
            risksSummary = risksText,
            scenariosSummary = scenariosText,
            isDemoPlaceholder = isDemoPlaceholder
        )
    }
}

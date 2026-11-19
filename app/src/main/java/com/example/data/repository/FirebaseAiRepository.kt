package com.example.data.repository

import com.example.domain.model.AiAgentRole
import com.example.domain.model.AiConclusion
import com.example.domain.model.AiRecommendation
import com.example.domain.model.HhiRiskReport
import com.example.domain.model.MarketAsset
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import java.util.UUID

/**
 * Implementação oficial usando Firebase AI Logic (`Firebase.ai`) com suporte a:
 * 1. Pareceres estruturados em JSON (`gemini-3.8-flash` + `responseSchema`).
 * 2. Chat multi-turno com streaming (`startChat` + `sendMessageStream`) e seleção de modelo por tarefa.
 * Respeita integralmente o consentimento explícito antes de incluir dados da carteira pessoal.
 */
class FirebaseAiRepository : AiRepository {
    override val isLiveGeminiEnabled: Boolean = true

    private val recommendationSchema = Schema.obj(
        properties = mapOf(
            "tese" to Schema.string(description = "Tese de investimento clara e objetiva em português do Brasil"),
            "dadosUtilizados" to Schema.string(description = "Resumo exato dos dados utilizados na análise"),
            "dataBase" to Schema.string(description = "Data-base dos dados analisados"),
            "fonteConsultada" to Schema.string(description = "Fontes priorizando B3, CVM, Banco Central, ANBIMA e RI"),
            "indicadoresRelevantes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "nome" to Schema.string(description = "Nome do indicador"),
                        "valor" to Schema.string(description = "Valor formatado")
                    )
                ),
                description = "Lista de indicadores relevantes analisados"
            ),
            "riscosIdentificados" to Schema.array(
                items = Schema.string(),
                description = "Lista dos principais riscos identificados"
            ),
            "cenarioOtimista" to Schema.string(description = "Descrição do cenário otimista"),
            "cenarioBase" to Schema.string(description = "Descrição do cenário base"),
            "cenarioPessimista" to Schema.string(description = "Descrição do cenário pessimista"),
            "nivelConfianca" to Schema.integer(description = "Nível de confiança de 0 a 100"),
            "conclusao" to Schema.enumeration(
                values = listOf("COMPRAR", "MANTER", "VENDER", "AGUARDAR"),
                description = "Conclusão final do agente"
            )
        )
    )

    private val baseSystemInstruction = content {
        text(
            "Você é um assistente especialista da plataforma INVEST AI (Brasil), atuando conforme o papel solicitado " +
                "(Analista, Gestor ou Auditor). Priorize B3, CVM, Banco Central, ANBIMA e Relações com Investidores (RI). " +
                "Nunca invente cotações, posições, datas-base ou fontes; quando um ativo usar dados demonstrativos, " +
                "deixe isso explícito. Nunca execute ordens financeiras, nunca peça senhas bancárias e só considere " +
                "métricas pessoais se explicitamente autorizadas na mensagem. Responda exclusivamente em português do Brasil."
        )
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = GeminiTaskModel.GENERAL.modelId,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = recommendationSchema
                temperature = 0.25f
            },
            systemInstruction = baseSystemInstruction
        )
    }

    private val generalChatModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = GeminiTaskModel.GENERAL.modelId,
            generationConfig = generationConfig { temperature = 0.35f },
            systemInstruction = baseSystemInstruction
        )
    }

    private val fastChatModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = GeminiTaskModel.FAST.modelId,
            generationConfig = generationConfig { temperature = 0.3f },
            systemInstruction = baseSystemInstruction
        )
    }

    private val deepChatModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = GeminiTaskModel.DEEP_REASONING.modelId,
            generationConfig = generationConfig { temperature = 0.25f },
            systemInstruction = baseSystemInstruction
        )
    }

    override fun streamChatReply(
        role: AiAgentRole,
        modelOption: GeminiTaskModel,
        history: List<ChatTurn>,
        userMessage: String,
        focusedAsset: MarketAsset?,
        hhiReport: HhiRiskReport,
        includePersonalPortfolio: Boolean
    ): Flow<String> = flow {
        val activeModel = when (modelOption) {
            GeminiTaskModel.GENERAL -> generalChatModel
            GeminiTaskModel.FAST -> fastChatModel
            GeminiTaskModel.DEEP_REASONING -> deepChatModel
        }

        val roleInstruction = when (role) {
            AiAgentRole.ANALISTA ->
                "Papel ativo: ANALISTA (avaliação fundamentalista, técnica, múltiplos P/VP, DY, P/L, ROE e consistência de proventos)."
            AiAgentRole.GESTOR ->
                "Papel ativo: GESTOR (alocação de carteira, diversificação setorial, liquidez, grupo Data-Com G1–G4 e risco HHI)."
            AiAgentRole.AUDITOR ->
                "Papel ativo: AUDITOR (verificação crítica de consistência de dados, cálculos, fontes CVM/B3/BCB/ANBIMA/RI e riscos)."
        }

        val assetContext = focusedAsset?.let { asset ->
            "\nAtivo em foco (${if (asset.isDemoData) "BASE DEMONSTRATIVA" else "BASE IMPORTADA"}): " +
                "${asset.ticker} - ${asset.name} (${asset.assetClass.label}, setor ${asset.sector}), " +
                "Preço R$ ${"%.2f".format(asset.price)}, DY ${"%.2f".format(asset.dy12m)}%, " +
                "P/VP ${"%.2f".format(asset.pvp)}x, Liquidez R$ ${"%.1f".format(asset.liquidezDiariaMilhoes)}M, " +
                "Base ${asset.dataBaseDate}, Fonte ${asset.sourceLabel}."
        }.orEmpty()

        val privacyContext = if (includePersonalPortfolio && hhiReport.totalInvestedBrl > 0) {
            "\nContexto pessoal autorizado: Patrimônio R$ ${"%.2f".format(hhiReport.totalEquityBrl)}, " +
                "Caixa R$ ${"%.2f".format(hhiReport.cashBrl)}, HHI ${hhiReport.hhiScore.toInt()} pts (${hhiReport.classification})."
        } else {
            "\nDados pessoais da carteira: ISOLADOS LOCALMENTE (não inferir posições pessoais)."
        }

        val firebaseHistory = history.takeLast(20).map { turn ->
            val roleKey = if (turn.senderRole.equals("USER", ignoreCase = true)) "user" else "model"
            content(roleKey) { text(turn.text) }
        }

        val chat = activeModel.startChat(history = firebaseHistory)
        val composedPrompt = "$roleInstruction$assetContext$privacyContext\n\nMensagem do investidor: $userMessage"

        chat.sendMessageStream(composedPrompt).collect { chunk ->
            val delta = chunk.text.orEmpty()
            if (delta.isNotEmpty()) {
                emit(delta)
            }
        }
    }

    override suspend fun generateRecommendation(
        role: AiAgentRole,
        asset: MarketAsset,
        hhiReport: HhiRiskReport,
        includePersonalPortfolio: Boolean,
        customPrompt: String,
        horizonLabel: String,
        focusLabel: String
    ): AiRecommendation {
        val roleDirective = when (role) {
            AiAgentRole.ANALISTA ->
                "Atue como ANALISTA: avalie fundamentos (DY, P/VP, P/L, ROE, inadimplência, vacância), leitura técnica (MM9, MM21, MM200, IFR 14, suporte/resistência) e consistência de dividendos."
            AiAgentRole.GESTOR ->
                "Atue como GESTOR: avalie alocação, diversificação setorial, liquidez diária, grupo de Data-Com (G1–G4) e risco de concentração HHI."
            AiAgentRole.AUDITOR ->
                "Atue como AUDITOR: verifique consistência dos dados, cálculos, eventuais lacunas, aderência às fontes (B3/CVM/BCB/ANBIMA/RI) e se o dado é demonstrativo ou importado."
        }

        val portfolioSection = if (includePersonalPortfolio && hhiReport.totalInvestedBrl > 0) {
            val weight = hhiReport.byAsset.firstOrNull { it.label == asset.ticker }?.weightPercent ?: 0.0
            "AUTORIZAÇÃO EXPLÍCITA CONCEDIDA PELO USUÁRIO PARA DADOS DA CARTEIRA:\n" +
                "- Patrimônio Total: R$ ${"%.2f".format(hhiReport.totalEquityBrl)}\n" +
                "- Caixa Disponível: R$ ${"%.2f".format(hhiReport.cashBrl)}\n" +
                "- Índice HHI Real: ${hhiReport.hhiScore.toInt()} pts (${hhiReport.classification})\n" +
                "- Peso atual de ${asset.ticker} na carteira: ${"%.2f".format(weight)}%"
        } else {
            "DADOS PESSOAIS DA CARTEIRA: NÃO AUTORIZADOS / ISOLADOS LOCALMENTE. Não assuma nenhuma posição pessoal do usuário."
        }

        val prompt = """
            $roleDirective
            PARÂMETROS ESTRUTURADOS DO PROMPT:
            - Horizonte de Análise: $horizonLabel
            - Foco Principal: $focusLabel

            ATIVO ANALISADO (Base local identificada como ${if (asset.isDemoData) "Demonstrativa" else "Importada"}):
            - Ticker: ${asset.ticker} | Nome: ${asset.name}
            - Classe: ${asset.assetClass.label} | Setor: ${asset.sector}
            - Preço de referência: R$ ${"%.2f".format(asset.price)} | Data-base: ${asset.dataBaseDate}
            - Fonte local: ${asset.sourceLabel}
            - DY 12m: ${"%.2f".format(asset.dy12m)}% | P/VP: ${"%.2f".format(asset.pvp)}x | P/L: ${"%.2f".format(asset.pl)}x | ROE: ${"%.2f".format(asset.roe)}%
            - Liquidez diária: R$ ${"%.2f".format(asset.liquidezDiariaMilhoes)}M | Inadimplência: ${"%.2f".format(asset.inadimplenciaPercent)}% | Vacância: ${"%.2f".format(asset.vacanciaPercent)}%
            - Data-Com: Dia ${asset.dataComDay ?: "N/A"} (${asset.dataComGroup?.label ?: "Sem grupo"}) | Último provento: R$ ${"%.4f".format(asset.lastDividendBrl)}
            - Técnico: IFR(14)=${"%.1f".format(asset.rsi14)}, MM9=${"%.2f".format(asset.mm9)}, MM21=${"%.2f".format(asset.mm21)}, MM200=${"%.2f".format(asset.mm200)}, Suporte=${"%.2f".format(asset.supportPrice)}, Resistência=${"%.2f".format(asset.resistancePrice)}
            - Riscos conhecidos: ${asset.riskSummary}

            $portfolioSection

            Pergunta/Foco adicional do usuário: ${customPrompt.ifBlank { "Análise completa conforme seu papel." }}
        """.trimIndent()

        val response = model.generateContent(prompt)
        val jsonText = response.text ?: throw IllegalStateException("O modelo Gemini retornou resposta vazia.")
        val json = JSONObject(jsonText)

        val indicatorsArray = json.optJSONArray("indicadoresRelevantes")
        val indicators = buildList {
            if (indicatorsArray != null) {
                for (i in 0 until indicatorsArray.length()) {
                    val obj = indicatorsArray.optJSONObject(i) ?: continue
                    add(obj.optString("nome") to obj.optString("valor"))
                }
            }
        }

        val risksArray = json.optJSONArray("riscosIdentificados")
        val risks = buildList {
            if (risksArray != null) {
                for (i in 0 until risksArray.length()) {
                    val item = risksArray.optString(i)
                    if (item.isNotBlank()) add(item)
                }
            }
        }

        val conclusionStr = json.optString("conclusao", "AGUARDAR").uppercase()
        val conclusion = runCatching { AiConclusion.valueOf(conclusionStr) }.getOrDefault(AiConclusion.AGUARDAR)

        return AiRecommendation(
            id = UUID.randomUUID().toString(),
            agentRole = role,
            ticker = asset.ticker,
            tese = json.optString("tese"),
            dadosUtilizados = json.optString("dadosUtilizados"),
            dataBase = json.optString("dataBase", asset.dataBaseDate),
            fonteConsultada = json.optString("fonteConsultada", asset.sourceLabel),
            indicadoresRelevantes = indicators,
            riscosIdentificados = risks.ifEmpty { listOf(asset.riskSummary) },
            cenarioOtimista = json.optString("cenarioOtimista"),
            cenarioBase = json.optString("cenarioBase"),
            cenarioPessimista = json.optString("cenarioPessimista"),
            nivelConfianca = json.optInt("nivelConfianca", 75).coerceIn(0, 100),
            conclusao = conclusion,
            usedPersonalPortfolioData = includePersonalPortfolio,
            isDemoPlaceholder = false
        )
    }
}

package com.example.data.repository

import android.util.Log
import com.example.domain.model.AssetAuditJsonCodec
import com.example.domain.model.CloudAsset
import com.example.domain.model.FirestoreAssetAuditSummary
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.UUID

/**
 * Serviço dedicado que lê um ativo selecionado diretamente do Cloud Firestore
 * (`/users/{uid}/assets/{assetId}`), consulta o Gemini via Firebase AI Logic (`Firebase.ai`)
 * com saída estruturada JSON para gerar um resumo executivo de auditoria de riscos e oportunidades,
 * e persiste o resultado auditado em `/users/{uid}/asset_audits/{auditId}`.
 */
class GeminiFirestoreAssetAuditService(
    private val firestoreRepository: FirestoreSyncRepository,
    private val modelName: String = DEFAULT_AUDIT_MODEL
) {
    private val auditResponseSchema = Schema.obj(
        properties = mapOf(
            "resumoExecutivo" to Schema.string(
                description = "Resumo executivo de auditoria claro, objetivo e técnico em português do Brasil sobre o ativo selecionado no Firestore."
            ),
            "classificacaoRisco" to Schema.enumeration(
                values = listOf("BAIXO", "MODERADO", "ALTO", "CRITICO"),
                description = "Classificação consolidada de risco do ativo."
            ),
            "scoreAuditoria" to Schema.integer(
                description = "Pontuação executiva de qualidade/robustez de 0 a 100 considerando dados, fundamentos e riscos."
            ),
            "veredito" to Schema.enumeration(
                values = listOf("FAVORAVEL", "NEUTRO", "CAUTELA", "EVITAR"),
                description = "Veredito analítico da auditoria (exclusivamente informacional)."
            ),
            "riscos" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "titulo" to Schema.string(description = "Título curto do risco identificado"),
                        "severidade" to Schema.enumeration(
                            values = listOf("ALTA", "MEDIA", "BAIXA"),
                            description = "Severidade do risco"
                        ),
                        "detalhe" to Schema.string(description = "Explicação objetiva do risco com base nos dados do Firestore"),
                        "mitigacao" to Schema.string(description = "Medida analítica de monitoramento ou mitigação")
                    )
                ),
                description = "Lista estruturada dos principais riscos do ativo"
            ),
            "oportunidades" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "titulo" to Schema.string(description = "Título curto da oportunidade identificada"),
                        "potencial" to Schema.enumeration(
                            values = listOf("ALTO", "MEDIO", "BAIXO"),
                            description = "Potencial da oportunidade"
                        ),
                        "detalhe" to Schema.string(description = "Explicação objetiva da oportunidade"),
                        "catalisador" to Schema.string(description = "Evento ou métrica gatilho a monitorar")
                    )
                ),
                description = "Lista estruturada das principais oportunidades do ativo"
            ),
            "alertasInconsistencia" to Schema.array(
                items = Schema.string(),
                description = "Lista de alertas de dados ausentes, defasados, demonstrativos ou potencialmente inconsistentes no documento do Firestore"
            ),
            "recomendacaoAuditoria" to Schema.string(
                description = "Próximo passo executivo de verificação (sem executar ordens ou prometer rentabilidade)"
            )
        )
    )

    private val auditSystemInstruction = content {
        text(
            "Você é o Auditor Executivo Sênior da plataforma INVEST AI (Brasil). " +
                "Sua missão é auditar rigorosamente os dados de um ativo armazenado no Cloud Firestore e produzir " +
                "um resumo executivo estruturado destacando riscos, oportunidades e eventuais inconsistências de dados. " +
                "Regras obrigatórias: nunca invente números, balanços ou cotações não informados; sinalize explicitamente " +
                "se o documento contiver dados demonstrativos ou lacunas (como cotação/DY/liquidez zerados); priorize " +
                "parâmetros de governança de B3, CVM, ANBIMA e Relações com Investidores (RI); nunca execute ordens " +
                "financeiras nem prometa retornos garantidos. Responda estritamente no schema JSON em português do Brasil."
        )
    }

    private val auditModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = auditResponseSchema
                temperature = 0.2f
            },
            systemInstruction = auditSystemInstruction
        )
    }

    suspend fun analyzeFirestoreAsset(
        assetId: String,
        includeRelatedInsights: Boolean = true,
        persistAuditToFirestore: Boolean = true
    ): Result<FirestoreAssetAuditSummary> {
        val assetResult = firestoreRepository.getCloudAsset(assetId)
        val cloudAsset = assetResult.getOrElse { error ->
            Log.e(TAG, "Falha ao ler ativo '$assetId' no Firestore", error)
            return Result.failure(error)
        } ?: return Result.failure(
            IllegalStateException(
                "Ativo '$assetId' não encontrado no Firestore. Sincronize o ativo na nuvem antes de executar a auditoria."
            )
        )

        val relatedInsights = if (includeRelatedInsights) {
            firestoreRepository.getRecentAgentInsightsForTicker(cloudAsset.ticker)
                .getOrElse { emptyList() }
        } else {
            emptyList()
        }

        val prompt = buildExecutiveAuditPrompt(cloudAsset, relatedInsights)
        return try {
            val response = withTimeout(AUDIT_TIMEOUT_MS) {
                auditModel.generateContent(prompt)
            }
            val rawJson = response.text?.trim()
                ?: return Result.failure(IllegalStateException("Gemini retornou resposta vazia para a auditoria de ${cloudAsset.ticker}."))

            val auditId = "audit_${CloudAsset.normalizeAssetDocId(cloudAsset.ticker)}_${UUID.randomUUID().toString().take(8)}"
            val parsedSummary = AssetAuditJsonCodec.parseGeminiAuditResponse(
                jsonText = rawJson,
                auditId = auditId,
                asset = cloudAsset,
                modelName = modelName
            )

            if (persistAuditToFirestore) {
                val saveRes = firestoreRepository.saveAssetAudit(parsedSummary, auditId)
                if (saveRes.isFailure) {
                    Log.e(TAG, "Auditoria gerada pelo Gemini, mas falha ao persistir no Firestore", saveRes.exceptionOrNull())
                    return Result.failure(
                        saveRes.exceptionOrNull()
                            ?: IllegalStateException("Falha ao salvar resumo de auditoria no Firestore.")
                    )
                }
            }

            Result.success(parsedSummary)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Tempo limite excedido ao auditar ativo '${cloudAsset.ticker}' com Gemini", e)
            Result.failure(
                IllegalStateException(
                    "Tempo limite de ${AUDIT_TIMEOUT_MS / 1000}s excedido na auditoria Gemini para ${cloudAsset.ticker}. Tente novamente.",
                    e
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao auditar ativo '${cloudAsset.ticker}' via Gemini AI Logic", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "GeminiAssetAuditSvc"
        const val DEFAULT_AUDIT_MODEL = "gemini-3.8-flash"
        private const val AUDIT_TIMEOUT_MS = 60_000L

        fun buildExecutiveAuditPrompt(
            asset: CloudAsset,
            relatedInsights: List<CloudAgentInsight> = emptyList()
        ): String {
            val locale = Locale("pt", "BR")
            val totalBrl = asset.totalPositionBrl()
            val pnlPercent = if (asset.averagePrice > 0.0 && asset.currentPrice > 0.0) {
                ((asset.currentPrice - asset.averagePrice) / asset.averagePrice) * 100.0
            } else {
                null
            }
            val dataComText = when {
                !asset.cyclical -> "Ativo não cíclico (sem enquadramento em grupos G1–G4)"
                asset.dataComDay in 1..31 -> "Dia ${asset.dataComDay} (${asset.resolvedDataComGroup()?.label ?: "Sem grupo"})"
                else -> "Não informado"
            }

            return buildString {
                appendLine("Execute uma AUDITORIA EXECUTIVA DE RISCOS E OPORTUNIDADES sobre o documento de ativo abaixo recuperado do Cloud Firestore:")
                appendLine()
                appendLine("=== DADOS DO ATIVO NO FIRESTORE ===")
                appendLine("Document ID: ${asset.id.ifBlank { CloudAsset.normalizeAssetDocId(asset.ticker) }}")
                appendLine("Ticker: ${asset.ticker}")
                appendLine("Nome: ${asset.name}")
                appendLine("Classe: ${asset.resolvedAssetClass().label} (${asset.assetClass})")
                appendLine("Setor/Segmento: ${asset.sector.ifBlank { "Não informado" }}")
                appendLine("Quantidade em posição: ${String.format(locale, "%.4f", asset.quantity)}")
                appendLine("Preço Médio (R$): ${String.format(locale, "%.2f", asset.averagePrice)}")
                appendLine("Preço Atual / Referência (R$): ${String.format(locale, "%.2f", asset.currentPrice)}")
                appendLine("Valor Posição Estimado (R$): ${String.format(locale, "%.2f", totalBrl)}")
                if (pnlPercent != null) {
                    appendLine("Variação vs Preço Médio: ${String.format(locale, "%+.2f%%", pnlPercent)}")
                }
                appendLine("Dividend Yield 12m (%): ${String.format(locale, "%.2f%%", asset.dy12m)}")
                appendLine("P/VP: ${String.format(locale, "%.2f", asset.pvp)}")
                appendLine("P/L: ${String.format(locale, "%.2f", asset.pl)}")
                appendLine("ROE (%): ${String.format(locale, "%.2f%%", asset.roe)}")
                appendLine("Liquidez Diária (R$ milhões): ${String.format(locale, "%.2f", asset.dailyLiquidityMillions)}")
                appendLine("Ciclo Data-Com: $dataComText")
                appendLine("Origem / Fonte registrada: ${asset.sourceLabel.ifBlank { "Não informada" }}")
                appendLine("Data-base registrada: ${asset.dataBaseDate.ifBlank { "Não informada" }}")
                appendLine("Flag de dados demonstrativos: ${if (asset.demoData) "SIM (sinalizar claramente na auditoria)" else "NÃO (posição/fonte real)"}")
                appendLine("Notas de risco armazenadas: ${asset.riskSummary.ifBlank { "Sem observações cadastradas" }}")

                if (relatedInsights.isNotEmpty()) {
                    appendLine()
                    appendLine("=== PARECERES ANTERIORES NO FIRESTORE (CONTEXTO HISTÓRICO) ===")
                    relatedInsights.take(4).forEach { insight ->
                        appendLine(
                            "- [${insight.agentRole}] Conclusão: ${insight.conclusion} (${insight.confidence}%): ${insight.thesis.take(280)}"
                        )
                    }
                }

                appendLine()
                appendLine("=== INSTRUÇÕES DA AUDITORIA ===")
                appendLine("1. Produza um 'resumoExecutivo' direto para tomada de decisão gerencial (3 a 5 frases).")
                appendLine("2. Liste de 2 a 4 'riscos' concretos (mercado, valuation, liquidez, governança ou qualidade dos dados) com severidade e mitigação.")
                appendLine("3. Liste de 2 a 4 'oportunidades' concretas (renda, assimetria de preço, qualidade operacional ou diversificação) com potencial e catalisador.")
                appendLine("4. Em 'alertasInconsistencia', aponte lacunas reais presentes nos dados acima (ex.: dados demonstrativos, indicadores zerados, ausência de posição ou preço médio zero).")
                appendLine("5. Defina 'classificacaoRisco', 'scoreAuditoria' (0-100), 'veredito' e 'recomendacaoAuditoria' mantendo caráter 100% analítico.")
            }
        }
    }
}

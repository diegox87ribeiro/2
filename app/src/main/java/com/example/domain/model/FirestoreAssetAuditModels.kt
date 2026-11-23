package com.example.domain.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject

enum class AuditRiskLevel(val label: String) {
    BAIXO("Risco Baixo"),
    MODERADO("Risco Moderado"),
    ALTO("Risco Alto"),
    CRITICO("Risco Crítico");

    companion object {
        fun fromRaw(raw: String?): AuditRiskLevel {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.name == normalized } ?: MODERADO
        }
    }
}

enum class AuditVerdict(val label: String) {
    FAVORAVEL("Favorável"),
    NEUTRO("Neutro"),
    CAUTELA("Cautela"),
    EVITAR("Evitar");

    companion object {
        fun fromRaw(raw: String?): AuditVerdict {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.name == normalized } ?: NEUTRO
        }
    }
}

enum class AuditSeverity(val label: String) {
    ALTA("Alta"),
    MEDIA("Média"),
    BAIXA("Baixa");

    companion object {
        fun fromRaw(raw: String?): AuditSeverity {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.name == normalized } ?: MEDIA
        }
    }
}

enum class AuditPotential(val label: String) {
    ALTO("Alto"),
    MEDIO("Médio"),
    BAIXO("Baixo");

    companion object {
        fun fromRaw(raw: String?): AuditPotential {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.name == normalized } ?: MEDIO
        }
    }
}

data class AuditRiskItem(
    val title: String,
    val severity: AuditSeverity,
    val detail: String,
    val mitigation: String
)

data class AuditOpportunityItem(
    val title: String,
    val potential: AuditPotential,
    val detail: String,
    val catalyst: String
)

data class FirestoreAssetAuditSummary(
    val id: String,
    val assetId: String,
    val ticker: String,
    val assetName: String,
    val riskLevel: AuditRiskLevel,
    val auditScore: Int,
    val verdict: AuditVerdict,
    val executiveSummary: String,
    val risks: List<AuditRiskItem>,
    val opportunities: List<AuditOpportunityItem>,
    val inconsistencyAlerts: List<String>,
    val auditRecommendation: String,
    val modelName: String,
    val sourceSnapshotLabel: String,
    val createdAtMillis: Long? = null
)

@Keep
@IgnoreExtraProperties
data class CloudAsset(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ticker: String = "",
    val name: String = "",
    val assetClass: String = AssetClass.ACOES_BR.name,
    val sector: String = "",
    val quantity: Double = 0.0,
    val averagePrice: Double = 0.0,
    val currentPrice: Double = 0.0,
    val dy12m: Double = 0.0,
    val pvp: Double = 0.0,
    val pl: Double = 0.0,
    val roe: Double = 0.0,
    val dailyLiquidityMillions: Double = 0.0,
    val dataComDay: Long = 0L,
    val cyclical: Boolean = true,
    val demoData: Boolean = false,
    val sourceLabel: String = "",
    val dataBaseDate: String = "",
    val riskSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedAssetClass(): AssetClass =
        AssetClass.entries.firstOrNull { it.name == assetClass } ?: AssetClass.ACOES_BR

    fun resolvedDataComGroup(): DataComGroup? =
        DataComGroup.fromDay(dataComDay.toInt().takeIf { it in 1..31 }, cyclical)

    fun totalPositionBrl(): Double {
        val unit = if (currentPrice > 0.0) currentPrice else averagePrice
        return (quantity * unit).coerceAtLeast(0.0)
    }

    companion object {
        fun normalizeAssetDocId(rawTicker: String): String {
            val cleaned = rawTicker.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "_")
            return cleaned.take(64).ifBlank { "ATIVO" }
        }

        fun fromLocalData(
            position: PortfolioItemView?,
            marketAsset: MarketAsset?,
            userId: String
        ): CloudAsset {
            val ticker = (position?.ticker ?: marketAsset?.ticker ?: "ATIVO").trim().uppercase().take(24)
            val name = (position?.name?.ifBlank { null } ?: marketAsset?.name ?: ticker).trim().take(120)
            val resolvedClass = (position?.assetClass ?: marketAsset?.assetClass ?: AssetClass.ACOES_BR).name
            val sector = (position?.sector?.ifBlank { null } ?: marketAsset?.sector ?: "Não classificado").trim().take(100)
            val currentPrice = when {
                position != null && position.hasMarketPrice && position.currentPrice > 0.0 -> position.currentPrice
                marketAsset != null && marketAsset.price > 0.0 -> marketAsset.price
                position != null && position.currentPrice > 0.0 -> position.currentPrice
                else -> position?.averagePrice ?: 0.0
            }.coerceAtLeast(0.0)
            val dataComDay = (position?.dataComDay ?: marketAsset?.dataComDay ?: 0).coerceIn(0, 31).toLong()
            val cyclical = position?.isCyclical ?: marketAsset?.isCyclical ?: true
            val demoData = position?.isDemo ?: marketAsset?.isDemoData ?: true
            val sourceLabel = (position?.dataSourceLabel?.ifBlank { null }
                ?: marketAsset?.sourceLabel
                ?: "Carteira INVEST AI").take(300)
            val dataBaseDate = (marketAsset?.dataBaseDate ?: "Atualização local").take(64)
            val riskSummary = (marketAsset?.riskSummary ?: "Ativo sincronizado da carteira local do investidor.").take(1500)

            return CloudAsset(
                id = normalizeAssetDocId(ticker),
                userId = userId,
                ticker = ticker,
                name = name,
                assetClass = resolvedClass,
                sector = sector,
                quantity = (position?.quantity ?: 0.0).coerceAtLeast(0.0),
                averagePrice = (position?.averagePrice ?: 0.0).coerceAtLeast(0.0),
                currentPrice = currentPrice,
                dy12m = (marketAsset?.dy12m ?: 0.0).coerceIn(0.0, 500.0),
                pvp = (marketAsset?.pvp ?: 0.0).coerceIn(0.0, 1000.0),
                pl = (marketAsset?.pl ?: 0.0).coerceIn(-10000.0, 10000.0),
                roe = (marketAsset?.roe ?: 0.0).coerceIn(-500.0, 1000.0),
                dailyLiquidityMillions = (marketAsset?.liquidezDiariaMilhoes ?: 0.0).coerceAtLeast(0.0),
                dataComDay = dataComDay,
                cyclical = cyclical,
                demoData = demoData,
                sourceLabel = sourceLabel,
                dataBaseDate = dataBaseDate,
                riskSummary = riskSummary
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class CloudAssetAudit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val assetId: String = "",
    val ticker: String = "",
    val assetName: String = "",
    val riskLevel: String = AuditRiskLevel.MODERADO.name,
    val auditScore: Long = 0L,
    val verdict: String = AuditVerdict.NEUTRO.name,
    val executiveSummary: String = "",
    val risksJson: String = "[]",
    val opportunitiesJson: String = "[]",
    val inconsistencyAlertsJson: String = "[]",
    val auditRecommendation: String = "",
    val modelName: String = "",
    val sourceSnapshotLabel: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomainSummary(): FirestoreAssetAuditSummary = FirestoreAssetAuditSummary(
        id = id,
        assetId = assetId,
        ticker = ticker,
        assetName = assetName,
        riskLevel = AuditRiskLevel.fromRaw(riskLevel),
        auditScore = auditScore.toInt().coerceIn(0, 100),
        verdict = AuditVerdict.fromRaw(verdict),
        executiveSummary = executiveSummary,
        risks = AssetAuditJsonCodec.decodeRisks(risksJson),
        opportunities = AssetAuditJsonCodec.decodeOpportunities(opportunitiesJson),
        inconsistencyAlerts = AssetAuditJsonCodec.decodeStringList(inconsistencyAlertsJson),
        auditRecommendation = auditRecommendation,
        modelName = modelName,
        sourceSnapshotLabel = sourceSnapshotLabel,
        createdAtMillis = createdAt?.toDate()?.time
    )
}

object AssetAuditJsonCodec {
    fun parseGeminiAuditResponse(
        jsonText: String,
        auditId: String,
        asset: CloudAsset,
        modelName: String
    ): FirestoreAssetAuditSummary {
        val trimmed = jsonText.trim()
        require(trimmed.startsWith("{")) {
            "Resposta do Gemini não retornou um objeto JSON estruturado válido."
        }
        val root = JSONObject(trimmed)

        val executiveSummary = root.optString("resumoExecutivo").trim()
        require(executiveSummary.isNotBlank()) {
            "Resposta do Gemini não trouxe o campo obrigatório 'resumoExecutivo'."
        }

        val riskLevel = AuditRiskLevel.fromRaw(root.optString("classificacaoRisco"))
        val auditScore = root.optInt("scoreAuditoria", 50).coerceIn(0, 100)
        val verdict = AuditVerdict.fromRaw(root.optString("veredito"))

        val risks = buildList {
            val arr = root.optJSONArray("riscos") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val title = obj.optString("titulo").trim()
                val detail = obj.optString("detalhe").trim()
                if (title.isNotBlank() || detail.isNotBlank()) {
                    add(
                        AuditRiskItem(
                            title = title.ifBlank { "Risco #${i + 1}" },
                            severity = AuditSeverity.fromRaw(obj.optString("severidade")),
                            detail = detail.ifBlank { title },
                            mitigacaoOrDefault(obj.optString("mitigacao"))
                        )
                    )
                }
            }
        }

        val opportunities = buildList {
            val arr = root.optJSONArray("oportunidades") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val title = obj.optString("titulo").trim()
                val detail = obj.optString("detalhe").trim()
                if (title.isNotBlank() || detail.isNotBlank()) {
                    add(
                        AuditOpportunityItem(
                            title = title.ifBlank { "Oportunidade #${i + 1}" },
                            potential = AuditPotential.fromRaw(obj.optString("potencial")),
                            detail = detail.ifBlank { title },
                            catalyst = obj.optString("catalisador").trim().ifBlank { "Monitorar balanços e comunicados de RI." }
                        )
                    )
                }
            }
        }

        val alerts = buildList {
            val arr = root.optJSONArray("alertasInconsistencia") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val value = arr.optString(i).trim()
                if (value.isNotBlank()) add(value)
            }
        }

        val recommendation = root.optString("recomendacaoAuditoria").trim()
            .ifBlank { "Revisar indicadores oficiais na B3/CVM antes de qualquer decisão." }

        val snapshotLabel = buildString {
            append(asset.sourceLabel.ifBlank { "Firestore" })
            if (asset.dataBaseDate.isNotBlank()) {
                append(" • ")
                append(asset.dataBaseDate)
            }
        }.take(300)

        return FirestoreAssetAuditSummary(
            id = auditId,
            assetId = asset.id.ifBlank { CloudAsset.normalizeAssetDocId(asset.ticker) },
            ticker = asset.ticker,
            assetName = asset.name,
            riskLevel = riskLevel,
            auditScore = auditScore,
            verdict = verdict,
            executiveSummary = executiveSummary.take(4000),
            risks = risks,
            opportunities = opportunities,
            inconsistencyAlerts = alerts,
            auditRecommendation = recommendation.take(1500),
            modelName = modelName.take(64),
            sourceSnapshotLabel = snapshotLabel,
            createdAtMillis = System.currentTimeMillis()
        )
    }

    private fun mitigacaoOrDefault(raw: String): String =
        raw.trim().ifBlank { "Acompanhar relatórios trimestrais e controlar o peso máximo na carteira." }

    fun encodeRisks(items: List<AuditRiskItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("titulo", item.title)
                    put("severidade", item.severity.name)
                    put("detalhe", item.detail)
                    put("mitigacao", item.mitigation)
                }
            )
        }
        return arr.toString().take(6000)
    }

    fun decodeRisks(rawJson: String): List<AuditRiskItem> = runCatching {
        val arr = JSONArray(rawJson)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(
                    AuditRiskItem(
                        title = obj.optString("titulo"),
                        severity = AuditSeverity.fromRaw(obj.optString("severidade")),
                        detail = obj.optString("detalhe"),
                        mitigation = obj.optString("mitigacao")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun encodeOpportunities(items: List<AuditOpportunityItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("titulo", item.title)
                    put("potencial", item.potential.name)
                    put("detalhe", item.detail)
                    put("catalisador", item.catalyst)
                }
            )
        }
        return arr.toString().take(6000)
    }

    fun decodeOpportunities(rawJson: String): List<AuditOpportunityItem> = runCatching {
        val arr = JSONArray(rawJson)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(
                    AuditOpportunityItem(
                        title = obj.optString("titulo"),
                        potential = AuditPotential.fromRaw(obj.optString("potencial")),
                        detail = obj.optString("detalhe"),
                        catalyst = obj.optString("catalisador")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun encodeStringList(items: List<String>): String {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        return arr.toString().take(3000)
    }

    fun decodeStringList(rawJson: String): List<String> = runCatching {
        val arr = JSONArray(rawJson)
        buildList {
            for (i in 0 until arr.length()) {
                val text = arr.optString(i).trim()
                if (text.isNotBlank()) add(text)
            }
        }
    }.getOrDefault(emptyList())
}

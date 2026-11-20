package com.example.domain.model

import com.example.data.local.PriceTargetAlertEntity

enum class PriceAlertCondition(
    val code: String,
    val label: String,
    val shortSymbol: String
) {
    AT_OR_BELOW("AT_OR_BELOW", "Cair até / Abaixo (≤)", "≤"),
    AT_OR_ABOVE("AT_OR_ABOVE", "Subir até / Acima (≥)", "≥");

    companion object {
        fun fromCode(code: String): PriceAlertCondition {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AT_OR_BELOW
        }
    }
}

data class TriggeredPriceAlert(
    val alert: PriceTargetAlertEntity,
    val currentPriceBrl: Double,
    val sourceLabel: String
)

object PriceTargetEvaluator {

    fun isTargetReached(
        alert: PriceTargetAlertEntity,
        currentPriceBrl: Double
    ): Boolean {
        if (!alert.isEnabled || alert.hasTriggered) return false
        if (currentPriceBrl <= 0.0 || alert.targetPriceBrl <= 0.0) return false
        val eps = 1e-6
        return when (PriceAlertCondition.fromCode(alert.condition)) {
            PriceAlertCondition.AT_OR_BELOW -> currentPriceBrl <= alert.targetPriceBrl + eps
            PriceAlertCondition.AT_OR_ABOVE -> currentPriceBrl + eps >= alert.targetPriceBrl
        }
    }

    fun evaluateAlerts(
        alerts: List<PriceTargetAlertEntity>,
        quotesByTicker: Map<String, Pair<Double, String>>
    ): List<TriggeredPriceAlert> {
        return alerts.mapNotNull { alert ->
            val key = alert.ticker.trim().uppercase()
            val priceAndSource = quotesByTicker[key] ?: return@mapNotNull null
            if (isTargetReached(alert, priceAndSource.first)) {
                TriggeredPriceAlert(
                    alert = alert,
                    currentPriceBrl = priceAndSource.first,
                    sourceLabel = priceAndSource.second
                )
            } else {
                null
            }
        }
    }

    fun suggestCondition(
        targetPriceBrl: Double,
        referenceCurrentPriceBrl: Double?
    ): PriceAlertCondition {
        if (referenceCurrentPriceBrl == null || referenceCurrentPriceBrl <= 0.0 || targetPriceBrl <= 0.0) {
            return PriceAlertCondition.AT_OR_BELOW
        }
        return if (targetPriceBrl < referenceCurrentPriceBrl) {
            PriceAlertCondition.AT_OR_BELOW
        } else {
            PriceAlertCondition.AT_OR_ABOVE
        }
    }
}

package com.example.domain.model

import com.example.data.local.AssetSwingAlertEntity
import com.example.data.local.InvestmentGoalEntity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class SwingDirection(
    val code: String,
    val label: String,
    val shortSymbol: String
) {
    ANY("ANY", "Alta ou Queda (±)", "±"),
    SURGE_UP("UP", "Alta Brusca (+)", "+"),
    DROP_DOWN("DOWN", "Queda Brusca (-)", "-");

    companion object {
        fun fromCode(code: String?): SwingDirection =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ANY
    }
}

enum class InvestmentGoalType(
    val code: String,
    val label: String,
    val unitSuffix: String,
    val requiresTicker: Boolean
) {
    TOTAL_EQUITY("TOTAL_EQUITY", "Patrimônio Total (R$)", "R$", false),
    MONTHLY_DIVIDENDS("MONTHLY_DIVIDENDS", "Renda Mensal Proventos (R$)", "R$/mês", false),
    ASSET_POSITION_VALUE("ASSET_POSITION_VALUE", "Valor no Ativo (R$)", "R$", true),
    ASSET_QUANTITY("ASSET_QUANTITY", "Cotas / Ações do Ativo", "un.", true);

    companion object {
        fun fromCode(code: String?): InvestmentGoalType =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: TOTAL_EQUITY
    }
}

data class GoalProgressSnapshot(
    val currentValue: Double,
    val targetValue: Double,
    val progressFraction: Float,
    val progressPercent: Double,
    val isReached: Boolean
)

object SmartAlertEvaluator {

    fun todayDayKey(epochMillis: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(Date(epochMillis))
    }

    fun isSharpSwingTriggered(
        alert: AssetSwingAlertEntity,
        changePercent1d: Double,
        currentDayKey: String
    ): Boolean {
        if (!alert.isEnabled) return false
        val threshold = alert.thresholdPercent
        if (threshold <= 0.0 || !changePercent1d.isFinite()) return false
        if (alert.lastTriggeredDayKey.isNotBlank() && alert.lastTriggeredDayKey == currentDayKey) {
            return false
        }
        return when (SwingDirection.fromCode(alert.direction)) {
            SwingDirection.ANY -> abs(changePercent1d) >= threshold
            SwingDirection.SURGE_UP -> changePercent1d >= threshold
            SwingDirection.DROP_DOWN -> changePercent1d <= -threshold
        }
    }

    fun computeGoalProgress(
        goal: InvestmentGoalEntity,
        totalEquityBrl: Double,
        estimatedMonthlyDividendsBrl: Double,
        realPositions: List<PortfolioItemView>
    ): GoalProgressSnapshot {
        val type = InvestmentGoalType.fromCode(goal.goalType)
        val cleanTicker = goal.ticker.trim().uppercase(Locale.ROOT)
        val currentValue = when (type) {
            InvestmentGoalType.TOTAL_EQUITY -> totalEquityBrl.coerceAtLeast(0.0)
            InvestmentGoalType.MONTHLY_DIVIDENDS -> estimatedMonthlyDividendsBrl.coerceAtLeast(0.0)
            InvestmentGoalType.ASSET_POSITION_VALUE -> realPositions
                .filter { it.ticker.trim().equals(cleanTicker, ignoreCase = true) }
                .sumOf { it.totalCurrentBrl }
                .coerceAtLeast(0.0)
            InvestmentGoalType.ASSET_QUANTITY -> realPositions
                .filter { it.ticker.trim().equals(cleanTicker, ignoreCase = true) }
                .sumOf { it.quantity }
                .coerceAtLeast(0.0)
        }
        val target = goal.targetValue.coerceAtLeast(0.0)
        val rawRatio = if (target > 0.0) currentValue / target else 0.0
        val clampedFraction = rawRatio.coerceIn(0.0, 1.0).toFloat()
        val progressPct = (rawRatio * 100.0).coerceAtLeast(0.0)
        val reached = target > 0.0 && currentValue >= target
        return GoalProgressSnapshot(
            currentValue = currentValue,
            targetValue = target,
            progressFraction = clampedFraction,
            progressPercent = progressPct,
            isReached = reached
        )
    }

    fun formatGoalValue(goalType: InvestmentGoalType, value: Double): String {
        val ptBr = Locale.forLanguageTag("pt-BR")
        return when (goalType) {
            InvestmentGoalType.TOTAL_EQUITY,
            InvestmentGoalType.ASSET_POSITION_VALUE -> NumberFormat.getCurrencyInstance(ptBr).format(value)
            InvestmentGoalType.MONTHLY_DIVIDENDS -> "${NumberFormat.getCurrencyInstance(ptBr).format(value)}/mês"
            InvestmentGoalType.ASSET_QUANTITY -> {
                val formatted = if (value % 1.0 == 0.0) {
                    String.format(ptBr, "%,.0f", value)
                } else {
                    String.format(ptBr, "%,.2f", value)
                }
                "$formatted cotas/ações"
            }
        }
    }
}

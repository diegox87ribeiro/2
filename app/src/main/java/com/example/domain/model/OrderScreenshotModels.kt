package com.example.domain.model

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Rascunho editável de uma ordem reconhecida a partir de um print de corretora/home broker
 * (ex.: Profit Mobile, Genial, XP, Rico, Clear, BTG, Inter, NuInvest).
 */
data class RecognizedOrderDraft(
    val id: String = UUID.randomUUID().toString(),
    val ticker: String,
    val operationType: OperationType,
    val quantity: Double,
    val unitPrice: Double,
    val totalValue: Double,
    val dateIso: String,
    val timeLabel: String = "",
    val statusLabel: String = "Executada",
    val orderTypeLabel: String = "",
    val brokerLabel: String = "",
    val isExecuted: Boolean = true,
    val isIncomplete: Boolean = false,
    val selected: Boolean = true,
    val notes: String = ""
) {
    val computedTotalValue: Double
        get() = if (quantity > 0.0 && unitPrice > 0.0) quantity * unitPrice else totalValue.coerceAtLeast(0.0)

    val isReadyToLaunch: Boolean
        get() = ticker.trim().length >= 4 && quantity > 0.0 && unitPrice > 0.0

    fun buildExternalRefId(): String {
        val cleanTicker = ticker.trim().uppercase()
        val qtyStr = String.format(Locale.US, "%.4f", quantity)
        val priceStr = String.format(Locale.US, "%.4f", unitPrice)
        val datePart = dateIso.trim().ifBlank { "NODATE" }
        val timePart = timeLabel.trim().ifBlank { "NA" }
        return "PRINT:$cleanTicker:${operationType.name}:$datePart:$timePart:$qtyStr:$priceStr"
    }
}

data class OrderScreenshotRecognitionResult(
    val brokerHeader: String = "",
    val orders: List<RecognizedOrderDraft> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class OrderScreenshotLaunchSummary(
    val launchedCount: Int,
    val skippedDuplicatesCount: Int,
    val updatedPositionsCount: Int,
    val message: String
)

sealed interface OrderScreenshotRecognitionState {
    data object Idle : OrderScreenshotRecognitionState
    data class Analyzing(val statusLabel: String = "Analisando print de ordens com IA...") : OrderScreenshotRecognitionState
    data class Review(
        val result: OrderScreenshotRecognitionResult,
        val imageUriString: String
    ) : OrderScreenshotRecognitionState

    data class Error(
        val message: String,
        val lastImageUriString: String? = null
    ) : OrderScreenshotRecognitionState
}

object OrderScreenshotJsonParser {

    fun parse(
        jsonText: String,
        fallbackDateBr: String = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())
    ): OrderScreenshotRecognitionResult {
        val trimmed = jsonText.trim()
        if (trimmed.isBlank()) {
            throw IllegalStateException("A IA retornou uma resposta vazia ao analisar o print.")
        }

        val root = JSONObject(trimmed)
        val brokerHeader = root.optString("brokerHeader", "").trim()
        val warnings = mutableListOf<String>()

        val warningsArray = root.optJSONArray("warnings")
        if (warningsArray != null) {
            for (i in 0 until warningsArray.length()) {
                val w = warningsArray.optString(i).trim()
                if (w.isNotBlank()) warnings.add(w)
            }
        }

        val ordersArray = root.optJSONArray("orders")
        val orders = mutableListOf<RecognizedOrderDraft>()

        if (ordersArray != null) {
            for (i in 0 until ordersArray.length()) {
                val obj = ordersArray.optJSONObject(i) ?: continue
                val rawTicker = obj.optString("ticker", "")
                val cleanTicker = sanitizeTicker(rawTicker)
                if (cleanTicker.isBlank()) continue

                val sideRaw = obj.optString("side", "COMPRA").trim().uppercase()
                val opType = when {
                    sideRaw.startsWith("V") || sideRaw == "SELL" -> OperationType.VENDA
                    else -> OperationType.COMPRA
                }

                var qty = obj.optDouble("quantity", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                var unitPrice = obj.optDouble("unitPrice", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                var totalVal = obj.optDouble("totalValue", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

                if (unitPrice <= 0.0 && qty > 0.0 && totalVal > 0.0) {
                    unitPrice = totalVal / qty
                }
                if (totalVal <= 0.0 && qty > 0.0 && unitPrice > 0.0) {
                    totalVal = qty * unitPrice
                }
                if (qty <= 0.0 && unitPrice > 0.0 && totalVal > 0.0) {
                    qty = totalVal / unitPrice
                }

                val rawDate = obj.optString("dateBr", "").trim()
                val normalizedDate = normalizeBrazilianDate(rawDate).ifBlank { fallbackDateBr }
                val timeLabel = obj.optString("timeLabel", "").trim()
                val statusLabel = obj.optString("status", "Executada").trim().ifBlank { "Executada" }
                val orderTypeLabel = obj.optString("orderType", "").trim()
                val modelMarkedIncomplete = obj.optBoolean("isIncomplete", false)
                val notes = obj.optString("notes", "").trim()

                val isExecuted = isExecutedStatus(statusLabel)
                val isIncomplete = modelMarkedIncomplete || cleanTicker.length < 4 || qty <= 0.0 || unitPrice <= 0.0
                val autoSelected = isExecuted && !isIncomplete

                val finalNotes = buildString {
                    if (notes.isNotBlank()) append(notes)
                    if (isIncomplete && !notes.contains("incomplet", ignoreCase = true)) {
                        if (isNotEmpty()) append(" • ")
                        append("Ordem parcialmente visível ou sem preço/quantidade completos — revise antes de lançar.")
                    } else if (!isExecuted) {
                        if (isNotEmpty()) append(" • ")
                        append("Status '$statusLabel' não confirmado como executada — desmarcada por segurança.")
                    }
                }

                orders.add(
                    RecognizedOrderDraft(
                        ticker = cleanTicker,
                        operationType = opType,
                        quantity = qty,
                        unitPrice = unitPrice,
                        totalValue = totalVal,
                        dateIso = normalizedDate,
                        timeLabel = timeLabel,
                        statusLabel = statusLabel,
                        orderTypeLabel = orderTypeLabel,
                        brokerLabel = brokerHeader,
                        isExecuted = isExecuted,
                        isIncomplete = isIncomplete,
                        selected = autoSelected,
                        notes = finalNotes
                    )
                )
            }
        }

        val incompleteCount = orders.count { it.isIncomplete }
        if (incompleteCount > 0 && warnings.none { it.contains("incomplet", ignoreCase = true) || it.contains("cortad", ignoreCase = true) }) {
            warnings.add(
                "$incompleteCount ordem(ns) parcialmente cortada(s) ou sem preço visível no print foram desmarcadas automaticamente para sua revisão."
            )
        }
        val nonExecCount = orders.count { !it.isExecuted }
        if (nonExecCount > 0) {
            warnings.add(
                "$nonExecCount ordem(ns) com status diferente de Executada foram desmarcadas por padrão."
            )
        }

        return OrderScreenshotRecognitionResult(
            brokerHeader = brokerHeader,
            orders = orders,
            warnings = warnings.distinct()
        )
    }

    internal fun sanitizeTicker(raw: String): String {
        val cleaned = raw.trim()
            .uppercase()
            .replace("$", "S") // Corrige artefato comum de OCR quando 'S' fica atrás de ícones (ex.: $MRE11 -> SMRE11)
            .replace(Regex("[^A-Z0-9]"), "")
        return if (cleaned.length > 5 && cleaned.endsWith("F") && cleaned.dropLast(1).lastOrNull()?.isDigit() == true) {
            cleaned.dropLast(1)
        } else {
            cleaned
        }
    }

    internal fun isExecutedStatus(status: String): Boolean {
        val lower = status.trim().lowercase()
        if (lower.isBlank()) return true
        if (lower.contains("cancel") || lower.contains("rejeit") || lower.contains("expir") || lower.contains("abert") || lower.contains("pendent")) {
            return false
        }
        return lower.contains("execut") ||
            lower.contains("preench") ||
            lower.contains("filled") ||
            lower.contains("liquidad") ||
            lower.contains("parcial") ||
            lower.contains("conclu")
    }

    internal fun normalizeBrazilianDate(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val brRegex = Regex("""(\d{1,2})/(\d{1,2})/(\d{2,4})""")
        brRegex.find(trimmed)?.let { m ->
            val d = m.groupValues[1].padStart(2, '0')
            val mo = m.groupValues[2].padStart(2, '0')
            val yRaw = m.groupValues[3]
            val y = if (yRaw.length == 2) "20$yRaw" else yRaw
            return "$d/$mo/$y"
        }
        val isoRegex = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
        isoRegex.find(trimmed)?.let { m ->
            val y = m.groupValues[1]
            val mo = m.groupValues[2].padStart(2, '0')
            val d = m.groupValues[3].padStart(2, '0')
            return "$d/$mo/$y"
        }
        return trimmed
    }
}

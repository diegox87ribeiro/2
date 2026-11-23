package com.example.domain.model

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Rascunho editável de uma ordem reconhecida a partir de um print de corretora/home broker
 * ou de uma Nota de Corretagem em PDF (padrão SINACOR B3 RV ou BM&F Derivativos Futuros).
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
    val notes: String = "",
    val specificationTitle: String = "",
    val noteNumber: String = "",
    val marketSegment: String = "VISTA",
    val isDayTrade: Boolean = false,
    val feesBrl: Double = 0.0
) {
    val isFuturesContract: Boolean
        get() = marketSegment.equals("BMF_FUTUROS", ignoreCase = true) ||
            B3BrokerageSpecMapper.isFuturesTicker(ticker)

    val computedTotalValue: Double
        get() = if (isFuturesContract) {
            totalValue.coerceAtLeast(0.0)
        } else if (quantity > 0.0 && unitPrice > 0.0) {
            quantity * unitPrice
        } else {
            totalValue.coerceAtLeast(0.0)
        }

    val isReadyToLaunch: Boolean
        get() = if (isFuturesContract) {
            ticker.trim().length >= 4 && quantity > 0.0 && (unitPrice > 0.0 || totalValue > 0.0)
        } else {
            ticker.trim().length >= 4 && quantity > 0.0 && unitPrice > 0.0
        }

    fun buildExternalRefId(): String {
        val cleanTicker = ticker.trim().uppercase()
        val qtyStr = String.format(Locale.US, "%.4f", quantity)
        val priceStr = String.format(Locale.US, "%.4f", unitPrice)
        val datePart = dateIso.trim().ifBlank { "NODATE" }
        val cleanNote = noteNumber.trim()
        if (cleanNote.isNotBlank()) {
            val mode = if (isDayTrade) "DT" else "SW"
            val totalStr = if (isFuturesContract) ":${String.format(Locale.US, "%.2f", computedTotalValue)}" else ""
            return "NOTA:$cleanNote:$cleanTicker:${operationType.name}:$datePart:$mode:$qtyStr:$priceStr$totalStr"
        }
        val timePart = timeLabel.trim().ifBlank { "NA" }
        return "PRINT:$cleanTicker:${operationType.name}:$datePart:$timePart:$qtyStr:$priceStr"
    }
}

data class OrderScreenshotRecognitionResult(
    val brokerHeader: String = "",
    val orders: List<RecognizedOrderDraft> = emptyList(),
    val warnings: List<String> = emptyList(),
    val documentKind: String = "PRINT",
    val totalFeesBrl: Double = 0.0,
    val totalNetSettlementBrl: Double? = null,
    val notesCount: Int = 0
) {
    val isBrokerageNote: Boolean
        get() = !documentKind.equals("PRINT", ignoreCase = true)
}

data class OrderScreenshotLaunchSummary(
    val launchedCount: Int,
    val skippedDuplicatesCount: Int,
    val updatedPositionsCount: Int,
    val message: String
)

sealed interface OrderScreenshotRecognitionState {
    data object Idle : OrderScreenshotRecognitionState
    data class Analyzing(
        val statusLabel: String = "Analisando nota de corretagem ou print com IA..."
    ) : OrderScreenshotRecognitionState

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
        fallbackDateBr: String = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date()),
        defaultDocumentKind: String = "PRINT"
    ): OrderScreenshotRecognitionResult {
        val trimmed = jsonText.trim()
        if (trimmed.isBlank()) {
            throw IllegalStateException("A IA retornou uma resposta vazia ao analisar o documento.")
        }

        val root = JSONObject(trimmed)
        val brokerHeader = root.optString("brokerHeader", "").trim()
        val rawDocKind = root.optString("documentKind", "").trim().uppercase()
        val documentKind = when (rawDocKind) {
            "NOTA_B3", "NOTA_BMF", "PRINT" -> rawDocKind
            else -> defaultDocumentKind
        }
        val totalFeesBrl = root.optDouble("totalFeesBrl", 0.0)
            .takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val totalNetSettlementBrl = if (root.has("totalNetSettlementBrl") && !root.isNull("totalNetSettlementBrl")) {
            root.optDouble("totalNetSettlementBrl").takeIf { it.isFinite() }
        } else {
            null
        }
        val reportedNotesCount = root.optInt("notesCount", 0).coerceAtLeast(0)

        val warnings = mutableListOf<String>()
        val warningsArray = root.optJSONArray("warnings")
        if (warningsArray != null) {
            for (i in 0 until warningsArray.length()) {
                val w = warningsArray.optString(i).trim()
                if (w.isNotBlank()) warnings.add(w)
            }
        }

        val ordersArray = root.optJSONArray("orders")
        val rawOrders = mutableListOf<RecognizedOrderDraft>()

        if (ordersArray != null) {
            for (i in 0 until ordersArray.length()) {
                val obj = ordersArray.optJSONObject(i) ?: continue
                val rawTicker = obj.optString("ticker", "").trim()
                val specificationTitle = obj.optString("specificationTitle", "").trim()
                val cleanTicker = B3BrokerageSpecMapper.resolveTicker(rawTicker, specificationTitle)
                if (cleanTicker.isBlank()) continue

                val sideRaw = obj.optString("side", "COMPRA").trim().uppercase()
                val opType = when {
                    sideRaw.startsWith("V") || sideRaw == "SELL" -> OperationType.VENDA
                    else -> OperationType.COMPRA
                }

                val rawSegment = obj.optString("marketSegment", "").trim().uppercase()
                val isFutures = rawSegment == "BMF_FUTUROS" ||
                    documentKind == "NOTA_BMF" ||
                    B3BrokerageSpecMapper.isFuturesTicker(cleanTicker)
                val marketSegment = when {
                    isFutures -> "BMF_FUTUROS"
                    rawSegment == "FRACIONARIO" -> "FRACIONARIO"
                    rawSegment == "OPCOES" -> "OPCOES"
                    else -> "VISTA"
                }

                var qty = obj.optDouble("quantity", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                var unitPrice = obj.optDouble("unitPrice", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                var totalVal = obj.optDouble("totalValue", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                val feesBrl = obj.optDouble("feesBrl", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

                if (!isFutures) {
                    if (unitPrice <= 0.0 && qty > 0.0 && totalVal > 0.0) {
                        unitPrice = totalVal / qty
                    }
                    if (totalVal <= 0.0 && qty > 0.0 && unitPrice > 0.0) {
                        totalVal = qty * unitPrice
                    }
                    if (qty <= 0.0 && unitPrice > 0.0 && totalVal > 0.0) {
                        qty = totalVal / unitPrice
                    }
                }

                val rawDate = obj.optString("dateBr", "").trim()
                val normalizedDate = normalizeBrazilianDate(rawDate).ifBlank { fallbackDateBr }
                val timeLabel = obj.optString("timeLabel", "").trim()
                val statusLabel = obj.optString("status", "Executada").trim().ifBlank { "Executada" }
                val orderTypeLabel = obj.optString("orderType", "").trim()
                val noteNumber = obj.optString("noteNumber", "").trim()
                val isDayTrade = obj.optBoolean("isDayTrade", false) ||
                    orderTypeLabel.contains("DAY", ignoreCase = true)
                val modelMarkedIncomplete = obj.optBoolean("isIncomplete", false)
                val notes = obj.optString("notes", "").trim()

                val isExecuted = isExecutedStatus(statusLabel)
                val isIncomplete = if (isFutures) {
                    modelMarkedIncomplete || cleanTicker.length < 4 || qty <= 0.0 || (unitPrice <= 0.0 && totalVal <= 0.0)
                } else {
                    modelMarkedIncomplete || cleanTicker.length < 4 || qty <= 0.0 || unitPrice <= 0.0
                }
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

                rawOrders.add(
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
                        notes = finalNotes,
                        specificationTitle = specificationTitle,
                        noteNumber = noteNumber,
                        marketSegment = marketSegment,
                        isDayTrade = isDayTrade,
                        feesBrl = feesBrl
                    )
                )
            }
        }

        val resolvedDocKind = when {
            documentKind != "PRINT" -> documentKind
            rawOrders.any { it.isFuturesContract && it.noteNumber.isNotBlank() } -> "NOTA_BMF"
            rawOrders.any { it.noteNumber.isNotBlank() || it.specificationTitle.isNotBlank() } -> "NOTA_B3"
            else -> "PRINT"
        }

        val orders = if (resolvedDocKind != "PRINT") {
            consolidatePartialFills(rawOrders)
        } else {
            rawOrders
        }

        val incompleteCount = orders.count { it.isIncomplete }
        if (incompleteCount > 0 && warnings.none { it.contains("incomplet", ignoreCase = true) || it.contains("cortad", ignoreCase = true) }) {
            warnings.add(
                "$incompleteCount ordem(ns) parcialmente cortada(s) ou sem preço visível foram desmarcadas automaticamente para sua revisão."
            )
        }
        val nonExecCount = orders.count { !it.isExecuted }
        if (nonExecCount > 0) {
            warnings.add(
                "$nonExecCount ordem(ns) com status diferente de Executada foram desmarcadas por padrão."
            )
        }

        val derivedNotesCount = if (reportedNotesCount > 0) {
            reportedNotesCount
        } else if (resolvedDocKind != "PRINT") {
            val distinctNotes = orders.map { it.noteNumber }.filter { it.isNotBlank() }.distinct().size
            if (distinctNotes > 0) distinctNotes else orders.map { it.dateIso }.filter { it.isNotBlank() }.distinct().size
        } else {
            0
        }

        val effectiveTotalFees = if (totalFeesBrl > 0.0) {
            totalFeesBrl
        } else {
            orders.sumOf { it.feesBrl }
        }

        return OrderScreenshotRecognitionResult(
            brokerHeader = brokerHeader,
            orders = orders,
            warnings = warnings.distinct(),
            documentKind = resolvedDocKind,
            totalFeesBrl = effectiveTotalFees,
            totalNetSettlementBrl = totalNetSettlementBrl,
            notesCount = derivedNotesCount
        )
    }

    /**
     * Consolida execuções parciais fragmentadas da mesma nota, data, ativo, lado e modalidade
     * (muito comum em notas SINACOR da B3 e BM&F) preservando quantidade total e preço médio ponderado.
     */
    internal fun consolidatePartialFills(orders: List<RecognizedOrderDraft>): List<RecognizedOrderDraft> {
        if (orders.size <= 1) return orders

        val result = mutableListOf<RecognizedOrderDraft>()
        val groupedIndices = linkedMapOf<String, MutableList<RecognizedOrderDraft>>()

        for (order in orders) {
            if (order.isIncomplete || !order.isExecuted) {
                groupedIndices["UNIQUE:${order.id}"] = mutableListOf(order)
            } else {
                val key = listOf(
                    order.noteNumber.trim(),
                    order.dateIso.trim(),
                    order.ticker.trim().uppercase(),
                    order.operationType.name,
                    order.marketSegment,
                    order.isDayTrade.toString()
                ).joinToString("|")
                groupedIndices.getOrPut(key) { mutableListOf() }.add(order)
            }
        }

        for ((_, group) in groupedIndices) {
            if (group.size == 1) {
                result.add(group.first())
                continue
            }
            val first = group.first()
            val totalQty = group.sumOf { it.quantity }
            val weightedPrice = if (totalQty > 0.0) {
                group.sumOf { it.quantity * it.unitPrice } / totalQty
            } else {
                first.unitPrice
            }
            val summedTotal = if (first.isFuturesContract) {
                group.sumOf { it.totalValue }
            } else {
                group.sumOf { it.computedTotalValue }
            }
            val summedFees = group.sumOf { it.feesBrl }
            val mergedSpec = group.firstOrNull { it.specificationTitle.isNotBlank() }?.specificationTitle.orEmpty()
            val existingNote = group.map { it.notes }.firstOrNull { it.isNotBlank() }.orEmpty()
            val consolidatedLabel = "Consolidado de ${group.size} execuções parciais"
            val mergedNotes = when {
                existingNote.isBlank() -> consolidatedLabel
                existingNote.contains("execuç", ignoreCase = true) -> existingNote
                else -> "$existingNote • $consolidatedLabel"
            }

            result.add(
                first.copy(
                    quantity = totalQty,
                    unitPrice = weightedPrice,
                    totalValue = summedTotal,
                    feesBrl = summedFees,
                    specificationTitle = mergedSpec,
                    notes = mergedNotes
                )
            )
        }
        return result
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

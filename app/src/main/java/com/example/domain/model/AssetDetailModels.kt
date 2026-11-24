package com.example.domain.model

import com.example.data.local.DividendEventEntity
import com.example.data.local.PortfolioOperationEntity
import java.util.Locale
import kotlin.math.abs

enum class AssetDetailTab(val label: String) {
    ORDERS("Ordens"),
    QUOTES("Cotações"),
    DIVIDENDS("Dividendos"),
    DIVIDEND_CALENDAR("Calendário"),
    POSITION_MOVEMENTS("Movimentações"),
    AVERAGE_PRICE("Preço Médio"),
    EQUITY_EVOLUTION("Patrimônio");

    companion object {
        fun fromCode(code: String?): AssetDetailTab {
            return entries.firstOrNull { it.name.equals(code?.trim(), ignoreCase = true) } ?: ORDERS
        }
    }
}

enum class OrderHistoryFilter(val label: String) {
    ALL("Todas"),
    BUY("Compras"),
    SELL("Vendas"),
    DIVIDEND("Proventos")
}

enum class OrderPeriodFilter(val label: String) {
    ALL("Todo período"),
    LAST_30D("30 dias"),
    LAST_90D("90 dias"),
    LAST_12M("12 meses")
}

data class AssetCardIndicators(
    val dayChangePercent: Double?,
    val dividendValueBrl: Double?,
    val dividendIsPerUnit: Boolean,
    val dividendStatusLabel: String, // "Pago", "Confirmado", "Previsto", "Sem registro"
    val dividendStatus: DividendStatus?,
    val dividendDateLabel: String, // "15/11/2026" ou "Dia 15 (est.)" ou "—"
    val dividendDateIsEffective: Boolean,
    val sparklinePoints: List<Double>
)

data class AveragePriceStep(
    val operationId: Long,
    val dateIso: String,
    val operationType: OperationType,
    val quantityDelta: Double,
    val executionPrice: Double,
    val financialValue: Double,
    val accumulatedQuantity: Double,
    val accumulatedCostBrl: Double,
    val resultingAveragePrice: Double,
    val notes: String
)

data class AssetEquityPoint(
    val label: String,
    val investedCostBrl: Double,
    val marketValueBrl: Double
)

object AssetDetailCalculator {

    fun computeCardIndicators(
        pos: PortfolioItemView,
        catalogAsset: MarketAsset?,
        liveQuote: LiveMarketQuote?,
        dividendEvents: List<DividendEventEntity>,
        referenceYmd: Triple<Int, Int, Int> = DividendHistoryEngine.currentYmd()
    ): AssetCardIndicators {
        val ticker = pos.ticker.trim().uppercase(Locale.ROOT)
        val refYmdInt = DividendHistoryEngine.ymdToInt(referenceYmd.first, referenceYmd.second, referenceYmd.third)

        val dayChange = liveQuote?.changePercent1d
            ?: catalogAsset?.changePercent1d?.takeIf { pos.hasMarketPrice }

        val activeEvents = dividendEvents
            .filter {
                !it.isDeleted &&
                    it.assetTicker.equals(ticker, ignoreCase = true) &&
                    !it.status.equals(DividendStatus.CANCELLED.name, ignoreCase = true)
            }
            .map { ev ->
                val ymd = DividendHistoryEngine.parseDateToYmd(ev.paymentDate)
                    ?: DividendHistoryEngine.parseDateToYmd(ev.exDate)
                    ?: referenceYmd
                ev to DividendHistoryEngine.ymdToInt(ymd.first, ymd.second, ymd.third)
            }

        // Prioriza próximo CONFIRMADO, depois último RECEBIDO, depois PROJETADO persistido, depois estimativa ao vivo/catálogo
        val nextConfirmed = activeEvents
            .filter { it.first.status.equals(DividendStatus.CONFIRMED.name, ignoreCase = true) }
            .minByOrNull { abs(it.second - refYmdInt) }
            ?.first

        val lastReceived = activeEvents
            .filter { it.first.status.equals(DividendStatus.RECEIVED.name, ignoreCase = true) }
            .maxByOrNull { it.second }
            ?.first

        val nextProjected = activeEvents
            .filter { it.first.status.equals(DividendStatus.PROJECTED.name, ignoreCase = true) }
            .minByOrNull { it.second }
            ?.first

        val primaryEvent = nextConfirmed ?: lastReceived ?: nextProjected

        val divValue: Double?
        val isPerUnit: Boolean
        val statusLabel: String
        val statusEnum: DividendStatus?
        val dateLabel: String
        val dateEffective: Boolean

        if (primaryEvent != null) {
            val st = DividendStatus.fromCode(primaryEvent.status)
            statusEnum = st
            statusLabel = when (st) {
                DividendStatus.RECEIVED -> "Pago"
                DividendStatus.CONFIRMED -> "Confirmado"
                DividendStatus.PROJECTED -> "Previsto"
                DividendStatus.CANCELLED -> "Cancelado"
            }
            if (primaryEvent.unitAmount > 0.0 && primaryEvent.quantityReference > 1.0) {
                divValue = primaryEvent.unitAmount
                isPerUnit = true
            } else {
                divValue = primaryEvent.totalAmount.takeIf { it > 0.0 }
                isPerUnit = false
            }
            dateLabel = primaryEvent.paymentDate.ifBlank { primaryEvent.exDate.ifBlank { "—" } }
            dateEffective = st == DividendStatus.RECEIVED
        } else {
            val fallbackUnit = liveQuote?.lastDividendBrl?.takeIf { it > 0.0 }
                ?: catalogAsset?.lastDividendBrl?.takeIf { it > 0.0 }
            divValue = fallbackUnit
            isPerUnit = true
            statusEnum = if (fallbackUnit != null && pos.isCyclical) DividendStatus.PROJECTED else null
            statusLabel = if (fallbackUnit != null) "Previsto" else "Sem provento"

            val payDay = liveQuote?.paymentDay?.takeIf { it in 1..31 }
                ?: catalogAsset?.paymentDay?.takeIf { it in 1..31 }
                ?: pos.dataComDay?.takeIf { it in 1..31 }?.let { (it + 7).coerceAtMost(28) }
            if (payDay != null && pos.isCyclical) {
                val (y, m, d) = referenceYmd
                val (targetY, targetM) = if (payDay >= d) y to m else DividendHistoryEngine.addMonthsToYm(y, m, 1)
                val maxDay = DividendHistoryEngine.daysInMonth(targetY, targetM)
                dateLabel = DividendHistoryEngine.formatDateBr(payDay.coerceAtMost(maxDay), targetM, targetY)
            } else {
                dateLabel = "—"
            }
            dateEffective = false
        }

        // Sparkline: usa série real de 30d do catálogo quando disponível; caso contrário constrói curva de PM -> Cotação Atual
        val catalogSeries = catalogAsset?.priceSeries30d?.filter { it > 0.0 }.orEmpty()
        val sparkline = when {
            catalogSeries.size >= 4 -> {
                if (pos.hasMarketPrice && pos.currentPrice > 0.0) {
                    catalogSeries.dropLast(1) + pos.currentPrice
                } else {
                    catalogSeries
                }
            }
            pos.averagePrice > 0.0 && pos.hasMarketPrice && pos.currentPrice > 0.0 -> {
                val start = pos.averagePrice
                val end = pos.currentPrice
                val delta = end - start
                listOf(
                    start,
                    start + delta * 0.18,
                    start + delta * 0.10,
                    start + delta * 0.42,
                    start + delta * 0.36,
                    start + delta * 0.70,
                    start + delta * 0.82,
                    end
                )
            }
            else -> listOf(1.0, 1.0, 1.0, 1.0)
        }

        return AssetCardIndicators(
            dayChangePercent = dayChange,
            dividendValueBrl = divValue,
            dividendIsPerUnit = isPerUnit,
            dividendStatusLabel = statusLabel,
            dividendStatus = statusEnum,
            dividendDateLabel = dateLabel,
            dividendDateIsEffective = dateEffective,
            sparklinePoints = sparkline
        )
    }

    fun buildAveragePriceTimeline(
        operations: List<PortfolioOperationEntity>
    ): List<AveragePriceStep> {
        val tradeOps = operations
            .filter {
                it.operationType.equals(OperationType.COMPRA.name, ignoreCase = true) ||
                    it.operationType.equals(OperationType.VENDA.name, ignoreCase = true)
            }
            .sortedWith(
                compareBy<PortfolioOperationEntity> { op ->
                    DividendHistoryEngine.parseDateToYmd(op.dateIso)?.let { (y, m, d) ->
                        DividendHistoryEngine.ymdToInt(y, m, d)
                    } ?: Int.MAX_VALUE
                }.thenBy { it.createdAt }.thenBy { it.id }
            )

        val steps = mutableListOf<AveragePriceStep>()
        var qty = 0.0
        var avgPrice = 0.0

        tradeOps.forEach { op ->
            val type = runCatching { OperationType.valueOf(op.operationType) }.getOrDefault(OperationType.COMPRA)
            val opQty = op.quantity.coerceAtLeast(0.0)
            val opPrice = op.unitPrice.coerceAtLeast(0.0)
            val finValue = if (op.totalValue > 0.0) op.totalValue else opQty * opPrice

            if (type == OperationType.COMPRA) {
                val totalCost = (qty * avgPrice) + (opQty * opPrice)
                val nextQty = qty + opQty
                avgPrice = if (nextQty > 0.0) totalCost / nextQty else 0.0
                qty = nextQty
            } else if (type == OperationType.VENDA) {
                qty = (qty - opQty).coerceAtLeast(0.0)
                if (qty <= 0.00001) {
                    qty = 0.0
                    avgPrice = 0.0
                }
            }

            steps.add(
                AveragePriceStep(
                    operationId = op.id,
                    dateIso = op.dateIso,
                    operationType = type,
                    quantityDelta = if (type == OperationType.VENDA) -opQty else opQty,
                    executionPrice = opPrice,
                    financialValue = finValue,
                    accumulatedQuantity = qty,
                    accumulatedCostBrl = qty * avgPrice,
                    resultingAveragePrice = avgPrice,
                    notes = op.notes
                )
            )
        }
        return steps
    }

    fun filterOperations(
        operations: List<PortfolioOperationEntity>,
        ticker: String,
        typeFilter: OrderHistoryFilter,
        periodFilter: OrderPeriodFilter,
        referenceYmd: Triple<Int, Int, Int> = DividendHistoryEngine.currentYmd()
    ): List<PortfolioOperationEntity> {
        val cleanTicker = ticker.trim().uppercase(Locale.ROOT)
        val (ry, rm, rd) = referenceYmd
        val minYmd: Int? = when (periodFilter) {
            OrderPeriodFilter.ALL -> null
            OrderPeriodFilter.LAST_30D -> {
                val (sy, sm, sd) = DividendHistoryEngine.addDaysToYmd(ry, rm, rd, -30)
                DividendHistoryEngine.ymdToInt(sy, sm, sd)
            }
            OrderPeriodFilter.LAST_90D -> {
                val (sy, sm, sd) = DividendHistoryEngine.addDaysToYmd(ry, rm, rd, -90)
                DividendHistoryEngine.ymdToInt(sy, sm, sd)
            }
            OrderPeriodFilter.LAST_12M -> {
                val (sy, sm) = DividendHistoryEngine.addMonthsToYm(ry, rm, -11)
                DividendHistoryEngine.ymdToInt(sy, sm, 1)
            }
        }

        return operations
            .filter { op ->
                if (!op.ticker.trim().equals(cleanTicker, ignoreCase = true)) return@filter false
                val matchesType = when (typeFilter) {
                    OrderHistoryFilter.ALL -> true
                    OrderHistoryFilter.BUY -> op.operationType.equals(OperationType.COMPRA.name, ignoreCase = true)
                    OrderHistoryFilter.SELL -> op.operationType.equals(OperationType.VENDA.name, ignoreCase = true)
                    OrderHistoryFilter.DIVIDEND -> op.operationType.equals(OperationType.PROVENTO.name, ignoreCase = true)
                }
                if (!matchesType) return@filter false
                if (minYmd != null) {
                    val parsed = DividendHistoryEngine.parseDateToYmd(op.dateIso)
                    if (parsed != null) {
                        val opYmd = DividendHistoryEngine.ymdToInt(parsed.first, parsed.second, parsed.third)
                        if (opYmd < minYmd) return@filter false
                    }
                }
                true
            }
            .sortedWith(
                compareByDescending<PortfolioOperationEntity> { op ->
                    DividendHistoryEngine.parseDateToYmd(op.dateIso)?.let { (y, m, d) ->
                        DividendHistoryEngine.ymdToInt(y, m, d)
                    } ?: 0
                }.thenByDescending { it.createdAt }.thenByDescending { it.id }
            )
    }

    fun buildEquityEvolution(
        position: PortfolioItemView?,
        steps: List<AveragePriceStep>,
        catalogAsset: MarketAsset?
    ): List<AssetEquityPoint> {
        if (position == null) return emptyList()
        if (steps.isNotEmpty()) {
            val pts = steps.map { step ->
                AssetEquityPoint(
                    label = step.dateIso.take(5).ifBlank { "Op" },
                    investedCostBrl = step.accumulatedCostBrl,
                    marketValueBrl = step.accumulatedQuantity * step.executionPrice
                )
            }.toMutableList()
            val currentMarket = if (position.hasMarketPrice && position.currentPrice > 0.0) {
                position.quantity * position.currentPrice
            } else {
                position.totalCostBrl
            }
            pts.add(
                AssetEquityPoint(
                    label = "Atual",
                    investedCostBrl = position.totalCostBrl,
                    marketValueBrl = currentMarket
                )
            )
            return pts
        }

        val priceSeries = catalogAsset?.priceSeries30d?.filter { it > 0.0 }.orEmpty()
        if (priceSeries.size >= 3 && position.quantity > 0.0) {
            return priceSeries.takeLast(6).mapIndexed { idx, price ->
                AssetEquityPoint(
                    label = if (idx == 5) "Atual" else "T-${5 - idx}",
                    investedCostBrl = position.totalCostBrl,
                    marketValueBrl = position.quantity * price
                )
            }
        }

        return listOf(
            AssetEquityPoint("Custo", position.totalCostBrl, position.totalCostBrl),
            AssetEquityPoint("Atual", position.totalCostBrl, position.totalCurrentBrl)
        )
    }
}

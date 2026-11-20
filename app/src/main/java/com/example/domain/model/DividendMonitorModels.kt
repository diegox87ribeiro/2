package com.example.domain.model

import com.example.data.local.PortfolioOperationEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AssetDividendRow(
    val ticker: String,
    val name: String,
    val assetClass: AssetClass,
    val quantity: Double,
    val receivedMonthBrl: Double,
    val receivedAnnualBrl: Double,
    val projectedMonthBrl: Double,
    val projectedAnnualBrl: Double,
    val estimatedDyPercent: Double,
    val yieldOnCostPercent: Double,
    val dataComDay: Int?,
    val paymentDay: Int?,
    val hasEstimateData: Boolean
)

data class MonthlyDividendPoint(
    val monthIndex: Int,
    val monthLabel: String,
    val receivedBrl: Double,
    val projectedBrl: Double
)

data class DividendMonitorSummary(
    val receivedMonthlyBrl: Double,
    val receivedAnnualBrl: Double,
    val projectedMonthlyBrl: Double,
    val projectedAnnualBrl: Double,
    val totalInvestedBrl: Double,
    val totalCostBrl: Double,
    val projectedAnnualDyPercent: Double,
    val projectedYieldOnCostPercent: Double,
    val perAssetRows: List<AssetDividendRow>,
    val receivedOperationsCount: Int,
    val monthlyJanToDec: List<MonthlyDividendPoint> = emptyList()
)

object DividendMonitorCalculator {

    private val dateFormats = listOf("dd/MM/yyyy", "yyyy-MM-dd", "dd/MM/yy")
    private val monthLabelsPt = listOf(
        "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
        "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    )

    fun compute(
        realPositions: List<PortfolioItemView>,
        marketAssets: List<MarketAsset>,
        operations: List<PortfolioOperationEntity>,
        referenceDateMillis: Long = System.currentTimeMillis()
    ): DividendMonitorSummary {
        val refCal = Calendar.getInstance().apply { timeInMillis = referenceDateMillis }
        val currentMonth = refCal.get(Calendar.MONTH)
        val currentYear = refCal.get(Calendar.YEAR)
        val oneYearAgoMillis = Calendar.getInstance().apply {
            timeInMillis = referenceDateMillis
            add(Calendar.YEAR, -1)
        }.timeInMillis

        val proventoOps = operations.filter {
            it.operationType == OperationType.PROVENTO.name &&
                it.portfolioType == PortfolioType.REAL.name &&
                it.totalValue > 0.0
        }

        val receivedMonthByTicker = mutableMapOf<String, Double>()
        val receivedAnnualByTicker = mutableMapOf<String, Double>()
        val receivedByCalendarMonth = DoubleArray(12)

        for (op in proventoOps) {
            val ticker = op.ticker.trim().uppercase()
            if (ticker.isBlank() || ticker == "CAIXA") continue

            val parsedMillis = parseOperationDateMillis(op.dateIso) ?: op.createdAt
            val opCal = Calendar.getInstance().apply { timeInMillis = parsedMillis }
            val opYear = opCal.get(Calendar.YEAR)
            val opMonth = opCal.get(Calendar.MONTH)

            if (opYear == currentYear && opMonth == currentMonth) {
                receivedMonthByTicker[ticker] = (receivedMonthByTicker[ticker] ?: 0.0) + op.totalValue
            }
            if (opYear == currentYear && opMonth in 0..11) {
                receivedByCalendarMonth[opMonth] += op.totalValue
            }
            if (parsedMillis >= oneYearAgoMillis) {
                receivedAnnualByTicker[ticker] = (receivedAnnualByTicker[ticker] ?: 0.0) + op.totalValue
            }
        }

        val catalogByTicker = marketAssets.associateBy { it.ticker.trim().uppercase() }
        val groupedPositions = realPositions.groupBy { it.ticker.trim().uppercase() }
        val allTickers = (groupedPositions.keys + receivedAnnualByTicker.keys + receivedMonthByTicker.keys).distinct()

        val projectedByCalendarMonth = DoubleArray(12)

        val rows = allTickers.map { ticker ->
            val lots = groupedPositions[ticker].orEmpty()
            val firstLot = lots.firstOrNull()
            val qty = lots.sumOf { it.quantity }
            val currentVal = lots.sumOf { it.totalCurrentBrl }
            val costVal = lots.sumOf { it.totalCostBrl }
            val catalog = catalogByTicker[ticker]

            val recMonth = receivedMonthByTicker[ticker] ?: 0.0
            val recAnnual = receivedAnnualByTicker[ticker] ?: 0.0

            var projMonth = 0.0
            var projAnnual = 0.0
            var estDy = 0.0
            var hasEstimate = false

            if (qty > 0.0) {
                if (catalog != null) {
                    val isMonthlyIncomeClass =
                        catalog.assetClass == AssetClass.FII || catalog.assetClass == AssetClass.FIAGRO
                    when {
                        isMonthlyIncomeClass && catalog.lastDividendBrl > 0.0 -> {
                            projMonth = qty * catalog.lastDividendBrl
                            projAnnual = projMonth * 12.0
                            estDy = if (currentVal > 0.0) (projAnnual / currentVal) * 100.0 else catalog.dy12m
                            hasEstimate = true
                        }
                        catalog.dy12m > 0.0 && currentVal > 0.0 -> {
                            projAnnual = currentVal * (catalog.dy12m / 100.0)
                            projMonth = projAnnual / 12.0
                            estDy = catalog.dy12m
                            hasEstimate = true
                        }
                        catalog.lastDividendBrl > 0.0 -> {
                            val paymentsIn6m = catalog.dividendHistory6m.count { it > 0.0 }.coerceAtLeast(1)
                            val annualEvents = (paymentsIn6m * 2).coerceIn(1, 12)
                            projAnnual = qty * catalog.lastDividendBrl * annualEvents
                            projMonth = projAnnual / 12.0
                            estDy = if (currentVal > 0.0) (projAnnual / currentVal) * 100.0 else 0.0
                            hasEstimate = true
                        }
                    }
                } else if (recAnnual > 0.0) {
                    projAnnual = recAnnual
                    projMonth = recAnnual / 12.0
                    estDy = if (currentVal > 0.0) (projAnnual / currentVal) * 100.0 else 0.0
                    hasEstimate = true
                }
            }

            if (projAnnual > 0.0) {
                val assetCls = firstLot?.assetClass ?: catalog?.assetClass ?: AssetClass.ACOES_BR
                val isMonthly = assetCls == AssetClass.FII ||
                    assetCls == AssetClass.FIAGRO ||
                    (catalog != null && catalog.dividendHistory6m.count { it > 0.0 } >= 5)
                if (isMonthly) {
                    val perMonth = projAnnual / 12.0
                    for (m in 0..11) projectedByCalendarMonth[m] += perMonth
                } else {
                    val anchor = ((firstLot?.dataComDay ?: catalog?.dataComDay ?: 3) % 3)
                    val quarterlyMonths = listOf(anchor, anchor + 3, anchor + 6, anchor + 9)
                    val perQuarter = projAnnual / quarterlyMonths.size
                    quarterlyMonths.forEach { m ->
                        if (m in 0..11) projectedByCalendarMonth[m] += perQuarter
                    }
                }
            }

            val yoc = if (costVal > 0.0 && projAnnual > 0.0) (projAnnual / costVal) * 100.0 else 0.0

            AssetDividendRow(
                ticker = ticker,
                name = firstLot?.name ?: catalog?.name ?: ticker,
                assetClass = firstLot?.assetClass ?: catalog?.assetClass ?: AssetClass.ACOES_BR,
                quantity = qty,
                receivedMonthBrl = recMonth,
                receivedAnnualBrl = recAnnual,
                projectedMonthBrl = projMonth,
                projectedAnnualBrl = projAnnual,
                estimatedDyPercent = estDy,
                yieldOnCostPercent = yoc,
                dataComDay = firstLot?.dataComDay ?: catalog?.dataComDay,
                paymentDay = catalog?.paymentDay,
                hasEstimateData = hasEstimate
            )
        }.sortedWith(
            compareByDescending<AssetDividendRow> { it.projectedAnnualBrl + it.receivedAnnualBrl }
                .thenBy { it.ticker }
        )

        val monthlySeries = (0..11).map { m ->
            MonthlyDividendPoint(
                monthIndex = m,
                monthLabel = monthLabelsPt[m],
                receivedBrl = receivedByCalendarMonth[m],
                projectedBrl = projectedByCalendarMonth[m]
            )
        }

        val totalInvested = realPositions.sumOf { it.totalCurrentBrl }.coerceAtLeast(0.0)
        val totalCost = realPositions.sumOf { it.totalCostBrl }.coerceAtLeast(0.0)
        val receivedMonthly = rows.sumOf { it.receivedMonthBrl }
        val receivedAnnual = rows.sumOf { it.receivedAnnualBrl }
        val projectedMonthly = rows.sumOf { it.projectedMonthBrl }
        val projectedAnnual = rows.sumOf { it.projectedAnnualBrl }

        val projectedDy = if (totalInvested > 0.0) (projectedAnnual / totalInvested) * 100.0 else 0.0
        val projectedYoc = if (totalCost > 0.0) (projectedAnnual / totalCost) * 100.0 else 0.0

        return DividendMonitorSummary(
            receivedMonthlyBrl = receivedMonthly,
            receivedAnnualBrl = receivedAnnual,
            projectedMonthlyBrl = projectedMonthly,
            projectedAnnualBrl = projectedAnnual,
            totalInvestedBrl = totalInvested,
            totalCostBrl = totalCost,
            projectedAnnualDyPercent = projectedDy,
            projectedYieldOnCostPercent = projectedYoc,
            perAssetRows = rows,
            receivedOperationsCount = proventoOps.size,
            monthlyJanToDec = monthlySeries
        )
    }

    internal fun parseOperationDateMillis(raw: String): Long? {
        val clean = raw.trim().substringBefore(" ")
        if (clean.isBlank()) return null
        for (pattern in dateFormats) {
            runCatching {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val date: Date? = fmt.parse(clean)
                if (date != null) return date.time
            }
        }
        return null
    }
}

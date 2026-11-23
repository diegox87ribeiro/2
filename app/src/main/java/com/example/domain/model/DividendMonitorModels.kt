package com.example.domain.model

import com.example.data.local.PortfolioOperationEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AssetDividendOperationRecord(
    val operationId: Long,
    val ticker: String,
    val monthIndex: Int, // 0..11
    val year: Int,
    val dateIso: String,
    val quantity: Double,
    val unitValueBrl: Double,
    val totalValueBrl: Double,
    val notes: String
)

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
    val hasEstimateData: Boolean,
    val receivedYearBrl: Double = 0.0,
    val projectedPerUnitMonthBrl: Double = 0.0,
    val monthlyPoints: List<MonthlyDividendPoint> = emptyList(),
    val operationsInYear: List<AssetDividendOperationRecord> = emptyList()
) {
    fun receivedForMonth(monthIndex: Int): Double {
        return monthlyPoints.getOrNull(monthIndex)?.receivedBrl ?: 0.0
    }

    fun projectedForMonth(monthIndex: Int): Double {
        return monthlyPoints.getOrNull(monthIndex)?.projectedBrl ?: 0.0
    }

    fun operationsForMonth(monthIndex: Int): List<AssetDividendOperationRecord> {
        return operationsInYear.filter { it.monthIndex == monthIndex }
    }
}

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
    val monthlyJanToDec: List<MonthlyDividendPoint> = emptyList(),
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val availableYears: List<Int> = emptyList(),
    val receivedYearTotalBrl: Double = 0.0
)

object DividendMonitorCalculator {

    private val dateFormats = listOf("dd/MM/yyyy", "yyyy-MM-dd", "dd/MM/yy")
    val monthLabelsPt = listOf(
        "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
        "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    )
    val monthFullNamesPt = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )

    fun formatDateForMonth(monthIndex: Int, year: Int, preferredDay: Int? = null): String {
        val safeMonth = monthIndex.coerceIn(0, 11)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, safeMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val day = (preferredDay ?: 15).coerceIn(1, maxDay)
        return String.format(Locale.US, "%02d/%02d/%04d", day, safeMonth + 1, year)
    }

    fun compute(
        realPositions: List<PortfolioItemView>,
        marketAssets: List<MarketAsset>,
        operations: List<PortfolioOperationEntity>,
        referenceDateMillis: Long = System.currentTimeMillis(),
        selectedYear: Int? = null
    ): DividendMonitorSummary {
        val refCal = Calendar.getInstance().apply { timeInMillis = referenceDateMillis }
        val currentMonth = refCal.get(Calendar.MONTH)
        val currentYear = refCal.get(Calendar.YEAR)
        val targetYear = selectedYear ?: currentYear
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
        val distinctAnnualMonthsByTicker = mutableMapOf<String, MutableSet<String>>()
        val receivedByCalendarMonth = DoubleArray(12)
        val receivedByMonthPerTicker = mutableMapOf<String, DoubleArray>()
        val opsInYearByTicker = mutableMapOf<String, MutableList<AssetDividendOperationRecord>>()
        val observedYears = mutableSetOf(currentYear - 1, currentYear, currentYear + 1, targetYear)

        for (op in proventoOps) {
            val ticker = op.ticker.trim().uppercase()
            if (ticker.isBlank() || ticker == "CAIXA") continue

            val parsedMillis = parseOperationDateMillis(op.dateIso) ?: op.createdAt
            val opCal = Calendar.getInstance().apply { timeInMillis = parsedMillis }
            val opYear = opCal.get(Calendar.YEAR)
            val opMonth = opCal.get(Calendar.MONTH)
            observedYears.add(opYear)

            if (opYear == currentYear && opMonth == currentMonth) {
                receivedMonthByTicker[ticker] = (receivedMonthByTicker[ticker] ?: 0.0) + op.totalValue
            }
            if (opYear == targetYear && opMonth in 0..11) {
                receivedByCalendarMonth[opMonth] += op.totalValue
                val tickerMonths = receivedByMonthPerTicker.getOrPut(ticker) { DoubleArray(12) }
                tickerMonths[opMonth] += op.totalValue
                opsInYearByTicker.getOrPut(ticker) { mutableListOf() }.add(
                    AssetDividendOperationRecord(
                        operationId = op.id,
                        ticker = ticker,
                        monthIndex = opMonth,
                        year = opYear,
                        dateIso = op.dateIso.ifBlank { formatDateForMonth(opMonth, opYear) },
                        quantity = op.quantity,
                        unitValueBrl = op.unitPrice,
                        totalValueBrl = op.totalValue,
                        notes = op.notes
                    )
                )
            }
            if (parsedMillis >= oneYearAgoMillis) {
                receivedAnnualByTicker[ticker] = (receivedAnnualByTicker[ticker] ?: 0.0) + op.totalValue
                distinctAnnualMonthsByTicker.getOrPut(ticker) { mutableSetOf() }.add("$opYear-$opMonth")
            }
        }

        val catalogByTicker = marketAssets.associateBy { it.ticker.trim().uppercase() }
        val groupedPositions = realPositions.groupBy { it.ticker.trim().uppercase() }
        val allTickers = (
            groupedPositions.keys +
                receivedAnnualByTicker.keys +
                receivedMonthByTicker.keys +
                receivedByMonthPerTicker.keys
            ).distinct()

        val projectedByCalendarMonth = DoubleArray(12)

        val rows = allTickers.map { ticker ->
            val lots = groupedPositions[ticker].orEmpty()
            val firstLot = lots.firstOrNull()
            val qty = lots.sumOf { it.quantity }
            val currentVal = lots.sumOf { it.totalCurrentBrl }
            val costVal = lots.sumOf { it.totalCostBrl }
            val catalog = catalogByTicker[ticker]
            val assetCls = firstLot?.assetClass ?: catalog?.assetClass ?: AssetClass.ACOES_BR

            val recMonth = receivedMonthByTicker[ticker] ?: 0.0
            val recAnnual = receivedAnnualByTicker[ticker] ?: 0.0
            val tickerReceivedByMonth = receivedByMonthPerTicker[ticker] ?: DoubleArray(12)
            val recYearTotal = tickerReceivedByMonth.sum()

            var projMonth = 0.0
            var projAnnual = 0.0
            var estDy = 0.0
            var hasEstimate = false

            if (qty > 0.0) {
                val isMonthlyIncomeClass =
                    assetCls == AssetClass.FII || assetCls == AssetClass.FIAGRO
                if (catalog != null) {
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
                        recAnnual > 0.0 -> {
                            val activeMonths = distinctAnnualMonthsByTicker[ticker]?.size?.coerceAtLeast(1) ?: 1
                            if (isMonthlyIncomeClass) {
                                projMonth = recAnnual / activeMonths
                                projAnnual = projMonth * 12.0
                            } else {
                                projAnnual = recAnnual
                                projMonth = recAnnual / 12.0
                            }
                            estDy = if (currentVal > 0.0) (projAnnual / currentVal) * 100.0 else 0.0
                            hasEstimate = true
                        }
                    }
                } else if (recAnnual > 0.0) {
                    val activeMonths = distinctAnnualMonthsByTicker[ticker]?.size?.coerceAtLeast(1) ?: 1
                    if (isMonthlyIncomeClass) {
                        projMonth = recAnnual / activeMonths
                        projAnnual = projMonth * 12.0
                    } else {
                        projAnnual = recAnnual
                        projMonth = recAnnual / 12.0
                    }
                    estDy = if (currentVal > 0.0) (projAnnual / currentVal) * 100.0 else 0.0
                    hasEstimate = true
                }
            }

            val tickerProjectedByMonth = DoubleArray(12)
            if (projAnnual > 0.0) {
                val isMonthly = assetCls == AssetClass.FII ||
                    assetCls == AssetClass.FIAGRO ||
                    (catalog != null && catalog.dividendHistory6m.count { it > 0.0 } >= 5)
                if (isMonthly) {
                    val perMonth = projAnnual / 12.0
                    for (m in 0..11) {
                        tickerProjectedByMonth[m] = perMonth
                        projectedByCalendarMonth[m] += perMonth
                    }
                } else {
                    val anchor = ((firstLot?.dataComDay ?: catalog?.dataComDay ?: 3) % 3)
                    val quarterlyMonths = listOf(anchor, anchor + 3, anchor + 6, anchor + 9)
                    val perQuarter = projAnnual / quarterlyMonths.size
                    quarterlyMonths.forEach { m ->
                        if (m in 0..11) {
                            tickerProjectedByMonth[m] = perQuarter
                            projectedByCalendarMonth[m] += perQuarter
                        }
                    }
                }
            }

            val perAssetMonthlyPoints = (0..11).map { m ->
                MonthlyDividendPoint(
                    monthIndex = m,
                    monthLabel = monthLabelsPt[m],
                    receivedBrl = tickerReceivedByMonth[m],
                    projectedBrl = tickerProjectedByMonth[m]
                )
            }

            val yoc = if (costVal > 0.0 && projAnnual > 0.0) (projAnnual / costVal) * 100.0 else 0.0
            val perUnitMonthly = if (qty > 0.0 && projMonth > 0.0) projMonth / qty else (catalog?.lastDividendBrl ?: 0.0)

            AssetDividendRow(
                ticker = ticker,
                name = firstLot?.name ?: catalog?.name ?: ticker,
                assetClass = assetCls,
                quantity = qty,
                receivedMonthBrl = recMonth,
                receivedAnnualBrl = recAnnual,
                projectedMonthBrl = projMonth,
                projectedAnnualBrl = projAnnual,
                estimatedDyPercent = estDy,
                yieldOnCostPercent = yoc,
                dataComDay = firstLot?.dataComDay ?: catalog?.dataComDay,
                paymentDay = catalog?.paymentDay,
                hasEstimateData = hasEstimate,
                receivedYearBrl = recYearTotal,
                projectedPerUnitMonthBrl = perUnitMonthly,
                monthlyPoints = perAssetMonthlyPoints,
                operationsInYear = opsInYearByTicker[ticker].orEmpty().sortedByDescending { it.monthIndex }
            )
        }.sortedWith(
            compareByDescending<AssetDividendRow> { it.projectedAnnualBrl + it.receivedAnnualBrl + it.receivedYearBrl }
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
        val receivedYearTotal = receivedByCalendarMonth.sum()

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
            monthlyJanToDec = monthlySeries,
            selectedYear = targetYear,
            availableYears = observedYears.sorted(),
            receivedYearTotalBrl = receivedYearTotal
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

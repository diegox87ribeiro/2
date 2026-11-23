package com.example.domain.model

import com.example.data.local.PortfolioOperationEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

enum class ProfitabilityRange(val label: String, val months: Int) {
    M3("3M", 3),
    M6("6M", 6),
    Y1("1A", 12),
    ALL("Tudo", 12)
}

data class ProfitabilityEvolutionPoint(
    val index: Int,
    val label: String,
    val fullLabel: String,
    val portfolioReturnPct: Double,
    val portfolioWithDividendsPct: Double,
    val cdiReturnPct: Double,
    val ibovReturnPct: Double,
    val equityBrl: Double,
    val accumulatedDividendsBrl: Double
)

data class PortfolioProfitabilitySeries(
    val range: ProfitabilityRange,
    val points: List<ProfitabilityEvolutionPoint>,
    val currentReturnPct: Double,
    val totalReturnWithDividendsPct: Double,
    val cdiReturnPct: Double,
    val ibovReturnPct: Double,
    val alphaVsCdiPct: Double,
    val totalProfitBrl: Double,
    val totalDividendsBrl: Double,
    val maxDrawdownPct: Double,
    val isDemoData: Boolean
)

object PortfolioProfitabilityCalculator {

    private val shortMonthsPt = listOf(
        "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
        "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    )

    private val fullMonthsPt = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )

    fun compute(
        realPositions: List<PortfolioItemView>,
        marketAssets: List<MarketAsset>,
        operations: List<PortfolioOperationEntity>,
        benchmarks: List<BenchmarkSeries>,
        range: ProfitabilityRange = ProfitabilityRange.Y1,
        referenceDateMillis: Long = System.currentTimeMillis()
    ): PortfolioProfitabilitySeries {
        if (realPositions.isEmpty()) {
            return PortfolioProfitabilitySeries(
                range = range,
                points = emptyList(),
                currentReturnPct = 0.0,
                totalReturnWithDividendsPct = 0.0,
                cdiReturnPct = 0.0,
                ibovReturnPct = 0.0,
                alphaVsCdiPct = 0.0,
                totalProfitBrl = 0.0,
                totalDividendsBrl = 0.0,
                maxDrawdownPct = 0.0,
                isDemoData = false
            )
        }

        val totalPoints = 12
        val monthSlots = buildMonthSlots(referenceDateMillis, totalPoints)
        val marketByTicker = marketAssets.associateBy { it.ticker.trim().uppercase() }

        val totalCostBrl = realPositions.sumOf { it.totalCostBrl }.coerceAtLeast(1.0)
        val currentEquityBrl = realPositions.sumOf { it.totalCurrentBrl }

        // Constrói curva normalizada (base 1.0 no início da janela de 12m, terminando exatamente no valor atual)
        val weightedValues12m = DoubleArray(totalPoints) { 0.0 }

        realPositions.forEach { pos ->
            val cost = pos.totalCostBrl.coerceAtLeast(0.0)
            val currentVal = pos.totalCurrentBrl.coerceAtLeast(0.0)
            if (cost <= 0.0 && currentVal <= 0.0) return@forEach

            val effectiveStart = if (cost > 0.0) cost else currentVal
            val catalog = marketByTicker[pos.ticker.trim().uppercase()]
            val rawSeries = catalog?.priceSeries30d?.filter { it > 0.0 }.orEmpty()

            if (rawSeries.size >= 2) {
                val sampled = resample(rawSeries, totalPoints)
                val firstRaw = sampled.first().coerceAtLeast(0.0001)
                val lastRaw = sampled.last().coerceAtLeast(0.0001)
                val rawTrendRatio = lastRaw / firstRaw
                val targetRatio = (currentVal / effectiveStart).coerceAtLeast(0.05)

                for (i in 0 until totalPoints) {
                    val progress = i.toDouble() / (totalPoints - 1).toDouble()
                    val normalizedRaw = sampled[i] / firstRaw
                    // Preserva a forma/volatilidade do ativo e ancora início no custo e fim na cotação real atual
                    val correction = if (rawTrendRatio > 0.01) {
                        val endAdjust = targetRatio / rawTrendRatio
                        1.0 + (endAdjust - 1.0) * progress
                    } else {
                        1.0
                    }
                    val adjustedRatio = if (i == 0) {
                        1.0
                    } else if (i == totalPoints - 1) {
                        targetRatio
                    } else {
                        (normalizedRaw * correction).coerceAtLeast(0.05)
                    }
                    weightedValues12m[i] += effectiveStart * adjustedRatio
                }
            } else {
                for (i in 0 until totalPoints) {
                    val progress = i.toDouble() / (totalPoints - 1).toDouble()
                    val interpolated = effectiveStart + (currentVal - effectiveStart) * progress
                    weightedValues12m[i] += interpolated
                }
            }
        }

        // Mapeia proventos reais recebidos por mês dentro dos 12 meses
        val dividendsByMonthKey = mutableMapOf<String, Double>()
        operations
            .filter {
                it.portfolioType == PortfolioType.REAL.name &&
                    it.operationType == OperationType.PROVENTO.name &&
                    it.totalValue > 0.0
            }
            .forEach { op ->
                val ym = parseYearMonthKey(op.dateIso)
                if (ym != null) {
                    dividendsByMonthKey[ym] = (dividendsByMonthKey[ym] ?: 0.0) + op.totalValue
                }
            }

        val cdiSeries12m = extractBenchmarkPoints12m(benchmarks, "CDI", fallbackAnnualPct = 11.2)
        val ibovSeries12m = extractBenchmarkPoints12m(benchmarks, "Ibovespa", fallbackAnnualPct = 8.4)

        val windowSize = range.months.coerceIn(2, totalPoints)
        val startIdx = (totalPoints - windowSize).coerceAtLeast(0)

        val baseEquity = weightedValues12m[startIdx].coerceAtLeast(1.0)
        val baseCdi = cdiSeries12m[startIdx].coerceAtLeast(1.0)
        val baseIbov = ibovSeries12m[startIdx].coerceAtLeast(1.0)

        var runningDividends = 0.0
        val points = mutableListOf<ProfitabilityEvolutionPoint>()

        for (i in startIdx until totalPoints) {
            val slot = monthSlots[i]
            val monthDiv = dividendsByMonthKey[slot.yearMonthKey] ?: 0.0
            runningDividends += monthDiv

            val eq = weightedValues12m[i]
            val priceReturnPct = ((eq / baseEquity) - 1.0) * 100.0
            val totalReturnPct = (((eq + runningDividends) / baseEquity) - 1.0) * 100.0
            val cdiPct = ((cdiSeries12m[i] / baseCdi) - 1.0) * 100.0
            val ibovPct = ((ibovSeries12m[i] / baseIbov) - 1.0) * 100.0

            points.add(
                ProfitabilityEvolutionPoint(
                    index = i - startIdx,
                    label = slot.shortLabel,
                    fullLabel = slot.fullLabel,
                    portfolioReturnPct = priceReturnPct,
                    portfolioWithDividendsPct = totalReturnPct,
                    cdiReturnPct = cdiPct,
                    ibovReturnPct = ibovPct,
                    equityBrl = eq,
                    accumulatedDividendsBrl = runningDividends
                )
            )
        }

        // Calcula Max Drawdown sobre o retorno da carteira na janela
        var peakIndex = 100.0
        var maxDrawdown = 0.0
        points.forEach { pt ->
            val idxVal = 100.0 + pt.portfolioReturnPct
            peakIndex = max(peakIndex, idxVal)
            if (peakIndex > 0.0) {
                val dd = ((idxVal - peakIndex) / peakIndex) * 100.0
                if (dd < maxDrawdown) maxDrawdown = dd
            }
        }

        val last = points.last()
        val pnlBrl = (currentEquityBrl - totalCostBrl) + runningDividends

        return PortfolioProfitabilitySeries(
            range = range,
            points = points,
            currentReturnPct = last.portfolioReturnPct,
            totalReturnWithDividendsPct = last.portfolioWithDividendsPct,
            cdiReturnPct = last.cdiReturnPct,
            ibovReturnPct = last.ibovReturnPct,
            alphaVsCdiPct = last.portfolioWithDividendsPct - last.cdiReturnPct,
            totalProfitBrl = pnlBrl,
            totalDividendsBrl = runningDividends,
            maxDrawdownPct = maxDrawdown,
            isDemoData = realPositions.any { it.isDemo }
        )
    }

    private data class MonthSlot(
        val yearMonthKey: String,
        val shortLabel: String,
        val fullLabel: String
    )

    private fun buildMonthSlots(referenceMillis: Long, count: Int): List<MonthSlot> {
        val result = mutableListOf<MonthSlot>()
        for (offset in (count - 1) downTo 0) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = referenceMillis
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -offset)
            }
            val year = cal.get(Calendar.YEAR)
            val monthIdx = cal.get(Calendar.MONTH).coerceIn(0, 11)
            val shortYr = (year % 100).toString().padStart(2, '0')
            val key = String.format(Locale.US, "%04d-%02d", year, monthIdx + 1)
            result.add(
                MonthSlot(
                    yearMonthKey = key,
                    shortLabel = "${shortMonthsPt[monthIdx]}/$shortYr",
                    fullLabel = "${fullMonthsPt[monthIdx]} / $year"
                )
            )
        }
        return result
    }

    private fun extractBenchmarkPoints12m(
        benchmarks: List<BenchmarkSeries>,
        keyword: String,
        fallbackAnnualPct: Double
    ): List<Double> {
        val match = benchmarks.firstOrNull { it.name.contains(keyword, ignoreCase = true) }
        val pts = match?.normalizedPoints12m.orEmpty()
        if (pts.size >= 2) {
            return resample(pts, 12)
        }
        return (0..11).map { step ->
            val ratio = step / 11.0
            100.0 + fallbackAnnualPct * ratio
        }
    }

    private fun resample(points: List<Double>, targetSize: Int): List<Double> {
        if (points.size == targetSize) return points
        if (points.isEmpty()) return List(targetSize) { 100.0 }
        if (points.size == 1) return List(targetSize) { points.first() }
        return (0 until targetSize).map { idx ->
            val pos = (idx.toDouble() / (targetSize - 1).toDouble()) * (points.size - 1)
            val low = pos.toInt().coerceIn(0, points.lastIndex)
            val high = (low + 1).coerceAtMost(points.lastIndex)
            val frac = pos - low
            points[low] * (1.0 - frac) + points[high] * frac
        }
    }

    internal fun parseYearMonthKey(dateStr: String): String? {
        val clean = dateStr.trim().substringBefore(" ")
        if (clean.isBlank()) return null
        val patterns = listOf("dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yy")
        for (p in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(p, Locale.US).apply { isLenient = false }.parse(clean)
            }.getOrNull()
            if (parsed != null) {
                val cal = Calendar.getInstance().apply { time = parsed }
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH) + 1
                return String.format(Locale.US, "%04d-%02d", year, month)
            }
        }
        return null
    }
}

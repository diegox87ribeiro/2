package com.example.domain.model

import java.util.Locale

data class ComparisonCandidate(
    val id: String,
    val label: String,
    val shortLabel: String,
    val groupLabel: String,
    val series: BenchmarkSeries
)

object ComparisonSeriesBuilder {

    private val ptBr = Locale("pt", "BR")

    fun buildCandidates(
        benchmarks: List<BenchmarkSeries>,
        realPositions: List<PortfolioItemView>,
        marketAssets: List<MarketAsset>
    ): List<ComparisonCandidate> {
        val marketByTicker = marketAssets.associateBy { it.ticker.trim().uppercase() }
        val candidates = mutableListOf<ComparisonCandidate>()

        benchmarks.forEach { b ->
            val short = when {
                b.name.contains("CDI", ignoreCase = true) -> "CDI"
                b.name.contains("Ibovespa", ignoreCase = true) -> "IBOV"
                b.name.contains("Dólar", ignoreCase = true) -> "USD"
                b.name.contains("Bitcoin", ignoreCase = true) -> "BTC"
                b.name.contains("Ethereum", ignoreCase = true) -> "ETH"
                b.name.contains("Cripto", ignoreCase = true) -> "Cripto Sim"
                else -> b.name.take(10)
            }
            candidates.add(
                ComparisonCandidate(
                    id = "BENCH:${short.uppercase()}",
                    label = b.name,
                    shortLabel = short,
                    groupLabel = "Benchmarks",
                    series = b
                )
            )
        }

        val addedTickers = mutableSetOf<String>()

        realPositions
            .groupBy { it.ticker.trim().uppercase() }
            .forEach { (ticker, lots) ->
                if (ticker.isBlank() || ticker in addedTickers) return@forEach
                addedTickers.add(ticker)
                val catalogMatch = marketByTicker[ticker]
                val series = if (catalogMatch != null && catalogMatch.priceSeries30d.size >= 2) {
                    normalizePricesToBase100(
                        label = "$ticker (Carteira)",
                        rawPrices = catalogMatch.priceSeries30d,
                        source = "Carteira Real • ${catalogMatch.sourceLabel}"
                    )
                } else {
                    val first = lots.first()
                    val avg = first.averagePrice
                    val cur = if (first.hasMarketPrice && first.currentPrice > 0.0) first.currentPrice else avg
                    val returnPct = if (avg > 0.0) ((cur - avg) / avg) * 100.0 else 0.0
                    val points = (0..11).map { step ->
                        val ratio = step / 11.0
                        100.0 + returnPct * ratio
                    }
                    val sign = if (returnPct >= 0) "+" else ""
                    BenchmarkSeries(
                        name = "$ticker (Carteira)",
                        currentLabel = "$sign${String.format(ptBr, "%.1f", returnPct)}%",
                        return12mPercent = returnPct,
                        normalizedPoints12m = points,
                        source = "Carteira Real (${first.dataSourceLabel})"
                    )
                }
                candidates.add(
                    ComparisonCandidate(
                        id = "ASSET:$ticker",
                        label = "$ticker (Carteira Real)",
                        shortLabel = ticker,
                        groupLabel = "Carteira Real",
                        series = series
                    )
                )
            }

        marketAssets.forEach { asset ->
            val ticker = asset.ticker.trim().uppercase()
            if (ticker.isBlank() || ticker in addedTickers || asset.priceSeries30d.size < 2) return@forEach
            addedTickers.add(ticker)
            candidates.add(
                ComparisonCandidate(
                    id = "ASSET:$ticker",
                    label = "$ticker • ${asset.name}",
                    shortLabel = ticker,
                    groupLabel = "Mercado",
                    series = normalizePricesToBase100(
                        label = ticker,
                        rawPrices = asset.priceSeries30d,
                        source = asset.sourceLabel
                    )
                )
            )
        }

        return candidates
    }

    fun normalizePricesToBase100(
        label: String,
        rawPrices: List<Double>,
        source: String
    ): BenchmarkSeries {
        val valid = rawPrices.filter { it > 0.0 }
        if (valid.size < 2) {
            return BenchmarkSeries(
                name = label,
                currentLabel = "0,0%",
                return12mPercent = 0.0,
                normalizedPoints12m = List(12) { 100.0 },
                source = source
            )
        }
        val sampled = resampleTo12Points(valid)
        val base = sampled.first().coerceAtLeast(0.0001)
        val normalized = sampled.map { (it / base) * 100.0 }
        val retPct = normalized.last() - 100.0
        val sign = if (retPct >= 0) "+" else ""
        return BenchmarkSeries(
            name = label,
            currentLabel = "$sign${String.format(ptBr, "%.1f", retPct)}%",
            return12mPercent = retPct,
            normalizedPoints12m = normalized,
            source = source
        )
    }

    private fun resampleTo12Points(points: List<Double>): List<Double> {
        if (points.size == 12) return points
        return (0..11).map { idx ->
            val pos = (idx.toDouble() / 11.0) * (points.size - 1)
            val low = pos.toInt().coerceIn(0, points.lastIndex)
            val high = (low + 1).coerceAtMost(points.lastIndex)
            val frac = pos - low
            points[low] * (1.0 - frac) + points[high] * frac
        }
    }
}

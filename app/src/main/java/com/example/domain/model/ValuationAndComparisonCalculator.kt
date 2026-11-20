package com.example.domain.model

import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AssetPillarScores(
    val dividendos: Double,
    val valuation: Double,
    val rentabilidade: Double,
    val solidez: Double,
    val liquidez: Double
) {
    fun asList(): List<Double> = listOf(dividendos, valuation, rentabilidade, solidez, liquidez)
    val average: Double get() = asList().average()
}

data class AssetValuationSummary(
    val ticker: String,
    val bazinCeilingPriceBrl: Double?,
    val bazinMarginPercent: Double?,
    val bazinTargetDyPercent: Double,
    val grahamFairPriceBrl: Double?,
    val grahamMarginPercent: Double?,
    val fairPriceMethodLabel: String,
    val bazinStatusLabel: String,
    val pillarScores: AssetPillarScores,
    val overallScore: Double
)

object ValuationAndComparisonCalculator {

    val PILLAR_LABELS = listOf("Dividendos", "Valuation", "Rentab.", "Solidez", "Liquidez")

    fun computeValuation(
        asset: MarketAsset,
        targetBazinDyPercent: Double = 6.0
    ): AssetValuationSummary {
        val safeTargetDy = targetBazinDyPercent.coerceAtLeast(1.0)
        val annualDividendPerShare = when {
            asset.assetClass == AssetClass.RENDA_FIXA || asset.assetClass == AssetClass.ETF -> 0.0
            asset.dy12m > 0.0 && asset.price > 0.0 -> asset.price * (asset.dy12m / 100.0)
            (asset.assetClass == AssetClass.FII || asset.assetClass == AssetClass.FIAGRO) && asset.lastDividendBrl > 0.0 ->
                asset.lastDividendBrl * 12.0
            asset.lastDividendBrl > 0.0 -> asset.lastDividendBrl * 4.0
            else -> 0.0
        }

        val bazinCeiling = if (annualDividendPerShare > 0.0) {
            annualDividendPerShare / (safeTargetDy / 100.0)
        } else {
            null
        }
        val bazinMargin = if (bazinCeiling != null && asset.price > 0.0) {
            ((bazinCeiling - asset.price) / asset.price) * 100.0
        } else {
            null
        }

        val bazinStatus = when {
            bazinMargin == null -> "Sem fluxo de dividendos p/ Bazin"
            bazinMargin >= 15.0 -> "Abaixo do Teto (+${"%.1f".format(bazinMargin)}%)"
            bazinMargin >= 0.0 -> "Na Faixa de Bazin (+${"%.1f".format(bazinMargin)}%)"
            else -> "Acima do Teto (${"%.1f".format(bazinMargin)}%)"
        }

        val (fairPrice, fairLabel) = when {
            asset.pl > 0.0 && asset.pvp > 0.0 && asset.price > 0.0 -> {
                val multiplier = sqrt(22.5 / (asset.pl * asset.pvp))
                (asset.price * multiplier) to "Graham (22,5)"
            }
            (asset.assetClass == AssetClass.FII || asset.assetClass == AssetClass.FIAGRO) &&
                asset.pvp > 0.0 && asset.price > 0.0 -> {
                (asset.price / asset.pvp) to "Valor Patrimonial (VP)"
            }
            else -> null to "N/A"
        }

        val fairMargin = if (fairPrice != null && asset.price > 0.0) {
            ((fairPrice - asset.price) / asset.price) * 100.0
        } else {
            null
        }

        val pillars = computePillars(asset)
        return AssetValuationSummary(
            ticker = asset.ticker,
            bazinCeilingPriceBrl = bazinCeiling,
            bazinMarginPercent = bazinMargin,
            bazinTargetDyPercent = safeTargetDy,
            grahamFairPriceBrl = fairPrice,
            grahamMarginPercent = fairMargin,
            fairPriceMethodLabel = fairLabel,
            bazinStatusLabel = bazinStatus,
            pillarScores = pillars,
            overallScore = pillars.average
        )
    }

    fun computePillars(asset: MarketAsset): AssetPillarScores {
        val divScore = if (asset.assetClass == AssetClass.ETF || asset.assetClass == AssetClass.CRIPTO) {
            15.0
        } else {
            val dyPart = (asset.dy12m / 12.0).coerceIn(0.0, 1.0) * 75.0
            val consistencyPart = (asset.dividendConsistencyMonths.coerceIn(0, 12) / 12.0) * 25.0
            (dyPart + consistencyPart).coerceIn(5.0, 100.0)
        }

        val pvpScore = when {
            asset.pvp <= 0.0 -> 50.0
            asset.pvp <= 0.90 -> 96.0
            asset.pvp <= 1.05 -> 84.0
            asset.pvp <= 1.60 -> 68.0
            asset.pvp <= 2.50 -> 50.0
            else -> 28.0
        }
        val plScore = when {
            asset.pl <= 0.0 -> pvpScore
            asset.pl <= 7.0 -> 95.0
            asset.pl <= 11.0 -> 82.0
            asset.pl <= 16.0 -> 65.0
            asset.pl <= 25.0 -> 45.0
            else -> 25.0
        }
        val valScore = ((pvpScore * 0.55) + (plScore * 0.45)).coerceIn(10.0, 100.0)

        val rentScore = when (asset.assetClass) {
            AssetClass.ACOES_BR -> (asset.roe / 25.0 * 100.0).coerceIn(15.0, 100.0)
            AssetClass.FII, AssetClass.FIAGRO ->
                ((asset.dy12m * 6.5) + (25.0 - asset.vacanciaPercent * 1.5)).coerceIn(15.0, 100.0)
            AssetClass.RENDA_FIXA -> (asset.dy12m * 7.5).coerceIn(25.0, 100.0)
            else -> {
                val firstP = asset.priceSeries30d.firstOrNull() ?: asset.price
                val lastP = asset.priceSeries30d.lastOrNull() ?: asset.price
                val mom = if (firstP > 0.0) ((lastP - firstP) / firstP) * 100.0 else 0.0
                (55.0 + mom * 4.0).coerceIn(20.0, 100.0)
            }
        }

        val solidezScore = (
            95.0 -
                (asset.inadimplenciaPercent * 8.0) -
                (asset.vacanciaPercent * 2.0) -
                (asset.dividaLiquidaEbitda.coerceAtLeast(0.0) * 6.5) +
                (if (asset.fgcCovered) 10.0 else 0.0)
            ).coerceIn(15.0, 100.0)

        val liqScore = if (asset.liquidezDiariaMilhoes <= 0.0) {
            if (asset.assetClass == AssetClass.RENDA_FIXA && asset.fgcCovered) 35.0 else 20.0
        } else {
            ((log10(asset.liquidezDiariaMilhoes + 1.0) / log10(500.0)) * 100.0).coerceIn(15.0, 100.0)
        }

        return AssetPillarScores(
            dividendos = divScore,
            valuation = valScore,
            rentabilidade = rentScore,
            solidez = solidezScore,
            liquidez = liqScore
        )
    }

    fun matchesFundamentalFilter(
        asset: MarketAsset,
        criteria: FundamentalFilterCriteria
    ): Boolean {
        if (asset.assetClass == AssetClass.RENDA_FIXA) return false
        val eps = 1e-6
        val dyOk = asset.dy12m + eps >= criteria.minDyPercent
        val pvpOk = asset.pvp > 0.0 && asset.pvp <= criteria.maxPvp + eps
        val liqOk = asset.liquidezDiariaMilhoes + eps >= criteria.minDailyLiquidityMillions
        val inadOk = asset.inadimplenciaPercent <= criteria.maxInadimplenciaPercent + eps
        val roeOk = asset.assetClass != AssetClass.ACOES_BR || asset.roe + eps >= criteria.minRoePercent
        return dyOk && pvpOk && liqOk && inadOk && roeOk
    }

    fun filterFundamentalRanking(
        assets: List<MarketAsset>,
        criteria: FundamentalFilterCriteria
    ): List<FundamentalRankedAsset> {
        return assets
            .filter { matchesFundamentalFilter(it, criteria) }
            .map { asset ->
                val valuation = computeValuation(asset)
                val labels = buildList {
                    add("DY ${"%.1f".format(asset.dy12m)}%")
                    add("P/VP ${"%.2f".format(asset.pvp)}x")
                    add("Liq R$${"%.1f".format(asset.liquidezDiariaMilhoes)}M")
                    add("Inad ${"%.1f".format(asset.inadimplenciaPercent)}%")
                    if (asset.assetClass == AssetClass.ACOES_BR) {
                        add("ROE ${"%.1f".format(asset.roe)}%")
                    }
                }
                FundamentalRankedAsset(
                    asset = asset,
                    valuation = valuation,
                    score = valuation.overallScore.roundToInt().coerceIn(0, 100),
                    matchedLabels = labels
                )
            }
            .sortedWith(
                compareByDescending<FundamentalRankedAsset> { it.score }
                    .thenByDescending { it.asset.dy12m }
            )
    }
}

data class FundamentalFilterCriteria(
    val minDyPercent: Double,
    val maxPvp: Double,
    val minDailyLiquidityMillions: Double,
    val maxInadimplenciaPercent: Double,
    val minRoePercent: Double
)

data class FundamentalRankedAsset(
    val asset: MarketAsset,
    val valuation: AssetValuationSummary,
    val score: Int,
    val matchedLabels: List<String>
)


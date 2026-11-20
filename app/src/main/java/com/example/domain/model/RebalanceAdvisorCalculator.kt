package com.example.domain.model

import kotlin.math.pow

data class ClassAllocationGap(
    val assetClass: AssetClass,
    val currentValueBrl: Double,
    val currentWeightPct: Double,
    val targetWeightPct: Double,
    val deficitBrl: Double,
    val suggestedContributionBrl: Double
)

data class RebalanceSuggestion(
    val ticker: String,
    val name: String,
    val assetClass: AssetClass,
    val unitPrice: Double,
    val suggestedAmountBrl: Double,
    val estimatedUnits: Double,
    val currentWeightPct: Double,
    val postWeightPct: Double,
    val isNewPosition: Boolean,
    val reason: String
)

data class RebalancePlanResult(
    val currentInvestedBrl: Double,
    val contributionBrl: Double,
    val postInvestedBrl: Double,
    val hhiBefore: Double,
    val hhiAfter: Double,
    val dyBeforePct: Double,
    val dyAfterPct: Double,
    val classGaps: List<ClassAllocationGap>,
    val suggestions: List<RebalanceSuggestion>
)

object RebalanceAdvisorCalculator {

    fun defaultTargets(): Map<AssetClass, Double> = mapOf(
        AssetClass.ACOES_BR to 35.0,
        AssetClass.FII to 25.0,
        AssetClass.FIAGRO to 10.0,
        AssetClass.ETF to 15.0,
        AssetClass.RENDA_FIXA to 10.0,
        AssetClass.CRIPTO to 5.0
    )

    fun normalizeTargets(raw: Map<AssetClass, Double>): Map<AssetClass, Double> {
        val total = AssetClass.entries.sumOf { (raw[it] ?: 0.0).coerceAtLeast(0.0) }
        if (total <= 0.0) return defaultTargets()
        return AssetClass.entries.associateWith { ((raw[it] ?: 0.0).coerceAtLeast(0.0) / total) * 100.0 }
    }

    fun calculatePlan(
        realPositions: List<PortfolioItemView>,
        marketAssets: List<MarketAsset>,
        rawTargets: Map<AssetClass, Double>,
        contributionBrl: Double,
        respectHhiCap: Boolean = true,
        maxSingleAssetPct: Double = 20.0
    ): RebalancePlanResult {
        val targets = normalizeTargets(rawTargets)
        val safeContribution = contributionBrl.coerceAtLeast(0.0)

        val groupedByTicker = realPositions.groupBy { it.ticker.trim().uppercase() }
        val currentByTickerVal = groupedByTicker.mapValues { (_, list) -> list.sumOf { it.totalCurrentBrl } }.toMutableMap()
        val currentInvested = currentByTickerVal.values.sum().coerceAtLeast(0.0)
        val postInvested = currentInvested + safeContribution

        val catalogByTicker = marketAssets.associateBy { it.ticker.trim().uppercase() }
        val hhiBefore = computeHhiFromValues(currentByTickerVal.values.toList(), currentInvested)
        val dyBefore = computeWeightedDy(currentByTickerVal, realPositions, catalogByTicker, currentInvested)

        val currentByClass = AssetClass.entries.associateWith { cls ->
            realPositions.filter { it.assetClass == cls }.sumOf { it.totalCurrentBrl }
        }

        val rawDeficits = AssetClass.entries.associateWith { cls ->
            val targetVal = postInvested * ((targets[cls] ?: 0.0) / 100.0)
            (targetVal - (currentByClass[cls] ?: 0.0)).coerceAtLeast(0.0)
        }
        val totalDeficit = rawDeficits.values.sum()

        val classAllocations = AssetClass.entries.associateWith { cls ->
            when {
                safeContribution <= 0.0 -> 0.0
                totalDeficit > 0.0 -> safeContribution * ((rawDeficits[cls] ?: 0.0) / totalDeficit)
                else -> safeContribution * ((targets[cls] ?: 0.0) / 100.0)
            }
        }

        val suggestions = mutableListOf<RebalanceSuggestion>()
        val postByTickerVal = currentByTickerVal.toMutableMap()

        for (cls in AssetClass.entries) {
            val budgetForClass = classAllocations[cls] ?: 0.0
            if (budgetForClass < 1.0) continue

            val ownedInClass = realPositions
                .filter { it.assetClass == cls }
                .groupBy { it.ticker.trim().uppercase() }
                .map { (ticker, items) ->
                    val first = items.first()
                    val curVal = items.sumOf { it.totalCurrentBrl }
                    val price = if (first.hasMarketPrice && first.currentPrice > 0.0) first.currentPrice else first.averagePrice
                    Triple(ticker, first.name, curVal to price)
                }

            val candidates = if (ownedInClass.isNotEmpty()) {
                // Sort underweight assets first, adding top catalog asset if all owned are above HHI cap
                val underCap = ownedInClass.filter { (_, _, valAndPrice) ->
                    !respectHhiCap || postInvested <= 0.0 || ((valAndPrice.first / postInvested) * 100.0) < maxSingleAssetPct
                }.sortedBy { it.third.first }

                if (underCap.isNotEmpty()) {
                    underCap.map { (t, n, vp) -> RebalanceCandidate(t, n, vp.first, vp.second, isNew = false) }
                } else {
                    val altCatalog = marketAssets.filter { it.assetClass == cls && it.ticker.uppercase() !in currentByTickerVal.keys }
                        .sortedByDescending { ValuationAndComparisonCalculator.computeValuation(it).overallScore }
                        .take(2)
                    if (altCatalog.isNotEmpty()) {
                        altCatalog.map { RebalanceCandidate(it.ticker, it.name, 0.0, it.price, isNew = true) }
                    } else {
                        ownedInClass.sortedBy { it.third.first }
                            .map { (t, n, vp) -> RebalanceCandidate(t, n, vp.first, vp.second, isNew = false) }
                    }
                }
            } else {
                marketAssets
                    .filter { it.assetClass == cls && it.price > 0.0 }
                    .sortedByDescending { ValuationAndComparisonCalculator.computeValuation(it).overallScore }
                    .take(2)
                    .map { RebalanceCandidate(it.ticker, it.name, 0.0, it.price, isNew = true) }
            }

            if (candidates.isEmpty()) continue

            var remainingBudget = budgetForClass
            val picks = candidates.take(2)
            picks.forEachIndexed { idx, cand ->
                if (remainingBudget < 1.0) return@forEachIndexed
                val rawShare = if (idx == picks.lastIndex) remainingBudget else remainingBudget * 0.60
                val maxAllowedForAsset = if (respectHhiCap && postInvested > 0.0) {
                    ((maxSingleAssetPct / 100.0) * postInvested - cand.currentValBrl).coerceAtLeast(0.0)
                } else {
                    rawShare
                }
                val allocated = if (respectHhiCap && picks.size > 1) {
                    minOf(rawShare, maxAllowedForAsset).coerceAtLeast(0.0)
                } else {
                    rawShare
                }
                if (allocated < 1.0) return@forEachIndexed

                remainingBudget -= allocated
                val safePrice = cand.unitPrice.coerceAtLeast(0.01)
                val units = if (cls == AssetClass.CRIPTO || cls == AssetClass.RENDA_FIXA) {
                    allocated / safePrice
                } else {
                    (allocated / safePrice).coerceAtLeast(0.0)
                }
                val curWeight = if (currentInvested > 0.0) (cand.currentValBrl / currentInvested) * 100.0 else 0.0
                val newVal = cand.currentValBrl + allocated
                val postWeight = if (postInvested > 0.0) (newVal / postInvested) * 100.0 else 0.0
                postByTickerVal[cand.ticker] = newVal

                val reason = when {
                    cand.isNew -> "Novo ativo p/ cobrir meta de ${cls.shortLabel} (${"%.0f".format(targets[cls] ?: 0.0)}%)"
                    respectHhiCap && curWeight < maxSingleAssetPct -> "Rebalanceia ${cls.shortLabel} mantendo peso abaixo de ${"%.0f".format(maxSingleAssetPct)}%"
                    else -> "Reduz déficit da classe ${cls.shortLabel}"
                }

                suggestions.add(
                    RebalanceSuggestion(
                        ticker = cand.ticker,
                        name = cand.name,
                        assetClass = cls,
                        unitPrice = cand.unitPrice,
                        suggestedAmountBrl = allocated,
                        estimatedUnits = units,
                        currentWeightPct = curWeight,
                        postWeightPct = postWeight,
                        isNewPosition = cand.isNew,
                        reason = reason
                    )
                )
            }
        }

        val hhiAfter = computeHhiFromValues(postByTickerVal.values.toList(), postInvested)
        val dyAfter = computeWeightedDy(postByTickerVal, realPositions, catalogByTicker, postInvested)

        val gaps = AssetClass.entries.map { cls ->
            val curVal = currentByClass[cls] ?: 0.0
            val curPct = if (currentInvested > 0.0) (curVal / currentInvested) * 100.0 else 0.0
            ClassAllocationGap(
                assetClass = cls,
                currentValueBrl = curVal,
                currentWeightPct = curPct,
                targetWeightPct = targets[cls] ?: 0.0,
                deficitBrl = rawDeficits[cls] ?: 0.0,
                suggestedContributionBrl = classAllocations[cls] ?: 0.0
            )
        }.sortedByDescending { it.suggestedContributionBrl }

        return RebalancePlanResult(
            currentInvestedBrl = currentInvested,
            contributionBrl = safeContribution,
            postInvestedBrl = postInvested,
            hhiBefore = hhiBefore,
            hhiAfter = hhiAfter,
            dyBeforePct = dyBefore,
            dyAfterPct = dyAfter,
            classGaps = gaps,
            suggestions = suggestions.sortedByDescending { it.suggestedAmountBrl }
        )
    }

    private data class RebalanceCandidate(
        val ticker: String,
        val name: String,
        val currentValBrl: Double,
        val unitPrice: Double,
        val isNew: Boolean
    )

    private fun computeHhiFromValues(values: List<Double>, total: Double): Double {
        if (total <= 0.0) return 0.0
        return values.filter { it > 0.0 }.sumOf { ((it / total) * 100.0).pow(2.0) }
    }

    private fun computeWeightedDy(
        valuesByTicker: Map<String, Double>,
        realPositions: List<PortfolioItemView>,
        catalogByTicker: Map<String, MarketAsset>,
        total: Double
    ): Double {
        if (total <= 0.0) return 0.0
        val dySum = valuesByTicker.entries.sumOf { (ticker, valBrl) ->
            val dy = catalogByTicker[ticker]?.dy12m ?: 0.0
            valBrl * dy
        }
        return dySum / total
    }
}

package com.example.domain.model

import com.example.data.local.PortfolioOperationEntity
import com.example.data.repository.LiveQuoteParsers
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class TaxAssetCategory(val label: String, val taxRate: Double) {
    ACOES_SWING("Ações (Swing 15%)", 0.15),
    FII_FIAGRO("FIIs / FIAGROs (20%)", 0.20),
    ETF("ETFs (15%)", 0.15),
    CRIPTO("Cripto (15%)", 0.15)
}

data class RealizedSaleDetail(
    val dateLabel: String,
    val ticker: String,
    val category: TaxAssetCategory,
    val quantity: Double,
    val sellUnitPrice: Double,
    val avgCostPrice: Double,
    val totalSalesBrl: Double,
    val realizedProfitBrl: Double
)

data class MonthlyTaxSummary(
    val monthKey: String, // YYYY-MM
    val monthYearLabel: String, // MM/yyyy
    val stockSalesBrl: Double,
    val isStockExempt20k: Boolean,
    val stockNetProfitBrl: Double,
    val etfSalesBrl: Double,
    val etfNetProfitBrl: Double,
    val fiiSalesBrl: Double,
    val fiiNetProfitBrl: Double,
    val cryptoSalesBrl: Double,
    val isCryptoExempt35k: Boolean,
    val cryptoNetProfitBrl: Double,
    val taxableCommonBaseBrl: Double,
    val taxableFiiBaseBrl: Double,
    val taxableCryptoBaseBrl: Double,
    val darfCommonBrl: Double,
    val darfFiiBrl: Double,
    val darfCryptoBrl: Double,
    val totalDarfBrl: Double,
    val isBelowDarfMinimum10Brl: Boolean,
    val endingCarryLossCommonBrl: Double,
    val endingCarryLossFiiBrl: Double,
    val realizedSales: List<RealizedSaleDetail>
)

object TaxReportCalculator {

    private val dateFormats = listOf("dd/MM/yyyy", "yyyy-MM-dd", "dd/MM/yy")

    fun computeMonthlyReports(
        operations: List<PortfolioOperationEntity>,
        realPositions: List<PortfolioItemView>,
        marketAssets: List<MarketAsset>,
        initialCarryLossCommonBrl: Double = 0.0,
        initialCarryLossFiiBrl: Double = 0.0
    ): List<MonthlyTaxSummary> {
        val tradeOps = operations.filter {
            it.portfolioType == PortfolioType.REAL.name &&
                (it.operationType == OperationType.COMPRA.name || it.operationType == OperationType.VENDA.name) &&
                it.quantity > 0.0 &&
                it.totalValue > 0.0
        }.sortedBy { parseMillis(it.dateIso) ?: it.createdAt }

        val posByTicker = realPositions.associateBy { it.ticker.trim().uppercase() }
        val catalogByTicker = marketAssets.associateBy { it.ticker.trim().uppercase() }

        val trackedQty = mutableMapOf<String, Double>()
        val trackedAvgCost = mutableMapOf<String, Double>()

        val realizedByMonth = sortedMapOf<String, MutableList<RealizedSaleDetail>>()

        for (op in tradeOps) {
            val ticker = op.ticker.trim().uppercase()
            if (ticker.isBlank() || ticker == "CAIXA") continue
            val category = resolveTaxCategory(ticker, posByTicker, catalogByTicker) ?: continue

            val qty = op.quantity
            val unitPrice = if (op.unitPrice > 0.0) op.unitPrice else op.totalValue / qty

            if (op.operationType == OperationType.COMPRA.name) {
                val curQty = trackedQty[ticker] ?: 0.0
                val curPm = trackedAvgCost[ticker] ?: 0.0
                val newQty = curQty + qty
                val newPm = if (newQty > 0.0) ((curQty * curPm) + (qty * unitPrice)) / newQty else unitPrice
                trackedQty[ticker] = newQty
                trackedAvgCost[ticker] = newPm
            } else {
                val pm = trackedAvgCost[ticker] ?: posByTicker[ticker]?.averagePrice ?: unitPrice
                val totalSale = op.totalValue
                val realizedPnl = totalSale - (qty * pm)
                trackedQty[ticker] = ((trackedQty[ticker] ?: 0.0) - qty).coerceAtLeast(0.0)

                val millis = parseMillis(op.dateIso) ?: op.createdAt
                val cal = Calendar.getInstance().apply { timeInMillis = millis }
                val monthKey = String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
                val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

                realizedByMonth.getOrPut(monthKey) { mutableListOf() }.add(
                    RealizedSaleDetail(
                        dateLabel = dateFmt.format(java.util.Date(millis)),
                        ticker = ticker,
                        category = category,
                        quantity = qty,
                        sellUnitPrice = unitPrice,
                        avgCostPrice = pm,
                        totalSalesBrl = totalSale,
                        realizedProfitBrl = realizedPnl
                    )
                )
            }
        }

        if (realizedByMonth.isEmpty()) return emptyList()

        var carryCommon = initialCarryLossCommonBrl.coerceAtLeast(0.0)
        var carryFii = initialCarryLossFiiBrl.coerceAtLeast(0.0)

        val summaries = mutableListOf<MonthlyTaxSummary>()
        for ((monthKey, details) in realizedByMonth) {
            val parts = monthKey.split("-")
            val monthYearLabel = "${parts[1]}/${parts[0]}"

            val stockDetails = details.filter { it.category == TaxAssetCategory.ACOES_SWING }
            val etfDetails = details.filter { it.category == TaxAssetCategory.ETF }
            val fiiDetails = details.filter { it.category == TaxAssetCategory.FII_FIAGRO }
            val cryptoDetails = details.filter { it.category == TaxAssetCategory.CRIPTO }

            val stockSales = stockDetails.sumOf { it.totalSalesBrl }
            val stockNet = stockDetails.sumOf { it.realizedProfitBrl }
            val isStockExempt = stockSales <= 20_000.0 && stockNet > 0.0

            val etfSales = etfDetails.sumOf { it.totalSalesBrl }
            val etfNet = etfDetails.sumOf { it.realizedProfitBrl }

            // Regra B3: ganho isento até R$ 20k em Ações não consome prejuízo acumulado; prejuízo em Ações acumula normalmente
            val commonProfitToOffset = (if (isStockExempt) 0.0 else stockNet) + etfNet
            val taxableCommonBase: Double
            if (commonProfitToOffset > 0.0) {
                val absorbed = minOf(carryCommon, commonProfitToOffset)
                carryCommon -= absorbed
                taxableCommonBase = (commonProfitToOffset - absorbed).coerceAtLeast(0.0)
            } else {
                carryCommon += -commonProfitToOffset
                taxableCommonBase = 0.0
            }

            // FIIs & FIAGROs: 20%, sem isenção de R$ 20k, compensação separada
            val fiiSales = fiiDetails.sumOf { it.totalSalesBrl }
            val fiiNet = fiiDetails.sumOf { it.realizedProfitBrl }
            val taxableFiiBase: Double
            if (fiiNet > 0.0) {
                val absorbedFii = minOf(carryFii, fiiNet)
                carryFii -= absorbedFii
                taxableFiiBase = (fiiNet - absorbedFii).coerceAtLeast(0.0)
            } else {
                carryFii += -fiiNet
                taxableFiiBase = 0.0
            }

            // Cripto: isenção para vendas mensais até R$ 35.000,00
            val cryptoSales = cryptoDetails.sumOf { it.totalSalesBrl }
            val cryptoNet = cryptoDetails.sumOf { it.realizedProfitBrl }
            val isCryptoExempt = cryptoSales <= 35_000.0 && cryptoNet > 0.0
            val taxableCryptoBase = if (!isCryptoExempt && cryptoNet > 0.0) cryptoNet else 0.0

            val darfCommon = taxableCommonBase * 0.15
            val darfFii = taxableFiiBase * 0.20
            val darfCrypto = taxableCryptoBase * 0.15
            val totalDarf = darfCommon + darfFii + darfCrypto

            summaries.add(
                MonthlyTaxSummary(
                    monthKey = monthKey,
                    monthYearLabel = monthYearLabel,
                    stockSalesBrl = stockSales,
                    isStockExempt20k = isStockExempt,
                    stockNetProfitBrl = stockNet,
                    etfSalesBrl = etfSales,
                    etfNetProfitBrl = etfNet,
                    fiiSalesBrl = fiiSales,
                    fiiNetProfitBrl = fiiNet,
                    cryptoSalesBrl = cryptoSales,
                    isCryptoExempt35k = isCryptoExempt,
                    cryptoNetProfitBrl = cryptoNet,
                    taxableCommonBaseBrl = taxableCommonBase,
                    taxableFiiBaseBrl = taxableFiiBase,
                    taxableCryptoBaseBrl = taxableCryptoBase,
                    darfCommonBrl = darfCommon,
                    darfFiiBrl = darfFii,
                    darfCryptoBrl = darfCrypto,
                    totalDarfBrl = totalDarf,
                    isBelowDarfMinimum10Brl = totalDarf in 0.01..9.99,
                    endingCarryLossCommonBrl = carryCommon,
                    endingCarryLossFiiBrl = carryFii,
                    realizedSales = details.sortedByDescending { it.dateLabel }
                )
            )
        }

        return summaries.sortedByDescending { it.monthKey }
    }

    private fun resolveTaxCategory(
        ticker: String,
        posByTicker: Map<String, PortfolioItemView>,
        catalogByTicker: Map<String, MarketAsset>
    ): TaxAssetCategory? {
        val assetClass = posByTicker[ticker]?.assetClass
            ?: catalogByTicker[ticker]?.assetClass
            ?: LiveQuoteParsers.inferAssetClassFromTicker(ticker)
        return when (assetClass) {
            AssetClass.ACOES_BR -> TaxAssetCategory.ACOES_SWING
            AssetClass.FII, AssetClass.FIAGRO -> TaxAssetCategory.FII_FIAGRO
            AssetClass.ETF -> TaxAssetCategory.ETF
            AssetClass.CRIPTO -> TaxAssetCategory.CRIPTO
            AssetClass.RENDA_FIXA -> null
        }
    }

    private fun parseMillis(raw: String): Long? {
        val clean = raw.trim().substringBefore(" ")
        if (clean.isBlank()) return null
        for (pattern in dateFormats) {
            runCatching {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val date = fmt.parse(clean)
                if (date != null) return date.time
            }
        }
        return null
    }
}

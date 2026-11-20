package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.StrategyCriteriaEntity
import com.example.domain.model.AssetClass
import com.example.domain.model.FundamentalFilterCriteria
import com.example.domain.model.LiveQuoteLookupState
import com.example.domain.model.MarketAsset
import com.example.domain.model.PriceAlertCondition
import com.example.domain.model.ValuationAndComparisonCalculator
import kotlin.math.roundToInt
import com.example.ui.components.AssetDetailSheetContent
import com.example.ui.components.DataDisclaimerBanner
import com.example.ui.components.FilterChipBar
import com.example.ui.components.FintechCard
import com.example.ui.components.LiveMarketLookupPanel
import com.example.ui.components.PriceTargetAlertsCard
import com.example.ui.components.TechnicalPriceChart
import com.example.ui.components.formatBrl
import com.example.ui.components.formatPct
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.InvestDashboardState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundamentalAnalysisScreen(
    state: InvestDashboardState,
    onUpdateCriteria: (StrategyCriteriaEntity, String) -> Unit,
    onQueryLiveQuote: (String) -> Unit = {},
    onRefreshLiveBatch: (List<String>) -> Unit = {},
    onApplyLiveQuoteToPortfolio: (String) -> Unit = {},
    onCreatePriceAlert: (String, Double, PriceAlertCondition, String) -> Unit = { _, _, _, _ -> },
    onTogglePriceAlert: (Long, Boolean) -> Unit = { _, _ -> },
    onRearmPriceAlert: (Long) -> Unit = {},
    onDeletePriceAlert: (Long) -> Unit = {},
    onCheckPriceAlertsNow: () -> Unit = {},
    onNotificationPermissionGranted: () -> Unit = {}
) {
    val c = state.strategyCriteria
    var minDy by remember(c) { mutableFloatStateOf(c.minDyPercent.toFloat()) }
    var maxPvp by remember(c) { mutableFloatStateOf(c.maxPvp.toFloat()) }
    var minLiq by remember(c) { mutableFloatStateOf(c.minDailyLiquidityMillions.toFloat()) }
    var maxInad by remember(c) { mutableFloatStateOf(c.maxInadimplenciaPercent.toFloat()) }
    var minRoe by remember(c) { mutableFloatStateOf(c.minRoePercent.toFloat()) }
    var changeReason by remember { mutableStateOf("") }
    var inspectedTicker by remember { mutableStateOf<String?>(null) }

    val ownedTickers = remember(state.realPositions) {
        state.realPositions.map { it.ticker.uppercase() }.toSet()
    }
    val quickTickers = remember(state.realPositions, state.marketAssets) {
        (state.realPositions.map { it.ticker } + listOf("ITUB4", "VALE3", "PETR4", "BBAS3", "HGLG11", "MXRF11"))
            .distinct()
            .take(14)
    }

    val filterCriteria = remember(minDy, maxPvp, minLiq, maxInad, minRoe) {
        FundamentalFilterCriteria(
            minDyPercent = minDy.toDouble(),
            maxPvp = maxPvp.toDouble(),
            minDailyLiquidityMillions = minLiq.toDouble(),
            maxInadimplenciaPercent = maxInad.toDouble(),
            minRoePercent = minRoe.toDouble()
        )
    }

    val totalEligibleCount = remember(state.marketAssets) {
        state.marketAssets.count { it.assetClass != AssetClass.RENDA_FIXA }
    }

    val rankedAssets = remember(state.marketAssets, filterCriteria) {
        ValuationAndComparisonCalculator.filterFundamentalRanking(
            assets = state.marketAssets,
            criteria = filterCriteria
        )
    }

    val inspectedAsset = inspectedTicker?.let { ticker ->
        state.marketAssets.firstOrNull { it.ticker.equals(ticker, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("fundamental_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DataDisclaimerBanner(
                text = "Análise de Ativos com consulta ao vivo na B3, ADVFN e Yahoo Finance (.SA). Digite qualquer código B3 ou toque em um ativo para inspecionar e atualizar em tempo real."
            )
        }

        item {
            LiveMarketLookupPanel(
                lookupState = state.liveLookupState,
                quickTickers = quickTickers,
                ownedTickers = ownedTickers,
                onQueryTicker = onQueryLiveQuote,
                onApplyPriceToPortfolio = onApplyLiveQuoteToPortfolio,
                initialTicker = quickTickers.firstOrNull() ?: "VALE3"
            )
        }

        item {
            val activeLookupTicker = (state.liveLookupState as? LiveQuoteLookupState.Success)?.quote?.ticker
                ?: (state.liveLookupState as? LiveQuoteLookupState.Loading)?.ticker
                ?: inspectedTicker
                ?: quickTickers.firstOrNull()
                ?: "VALE3"
            PriceTargetAlertsCard(
                alerts = state.priceTargetAlerts,
                marketAssets = state.marketAssets,
                suggestedTicker = activeLookupTicker,
                isCheckingAlerts = state.isCheckingPriceAlerts,
                onCreateAlert = onCreatePriceAlert,
                onToggleAlert = onTogglePriceAlert,
                onRearmAlert = onRearmPriceAlert,
                onDeleteAlert = onDeletePriceAlert,
                onCheckAlertsNow = onCheckPriceAlertsNow,
                onNotificationPermissionGranted = onNotificationPermissionGranted
            )
        }

        item {
            FintechCard {
                Text("Critérios Configuráveis de Seleção", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Text("DY Mínimo: ${"%.1f".format(minDy)}%", style = MonoValueStyle, color = EmeraldPrimary)
                Slider(
                    value = minDy,
                    onValueChange = { minDy = (it * 10f).roundToInt() / 10f },
                    valueRange = 0f..16f
                )

                Text("P/VP Máximo: ${"%.2f".format(maxPvp)}x", style = MonoValueStyle, color = ElectricCyan)
                Slider(
                    value = maxPvp,
                    onValueChange = { maxPvp = (it * 100f).roundToInt() / 100f },
                    valueRange = 0.6f..4.0f
                )

                Text("Liquidez Diária Mínima: R$ ${"%.1f".format(minLiq)}M", style = MonoValueStyle, color = AmberGold)
                Slider(
                    value = minLiq,
                    onValueChange = { minLiq = (it * 10f).roundToInt() / 10f },
                    valueRange = 0.5f..50f
                )

                Text("Inadimplência Máxima: ${"%.1f".format(maxInad)}%", style = MonoValueStyle, color = CoralRed)
                Slider(
                    value = maxInad,
                    onValueChange = { maxInad = (it * 10f).roundToInt() / 10f },
                    valueRange = 0f..10f
                )

                Text("ROE Mínimo (Ações): ${"%.1f".format(minRoe)}%", style = MonoValueStyle)
                Slider(
                    value = minRoe,
                    onValueChange = { minRoe = (it * 10f).roundToInt() / 10f },
                    valueRange = 0f..30f
                )

                OutlinedTextField(
                    value = changeReason,
                    onValueChange = { changeReason = it },
                    label = { Text("Motivo da alteração de estratégia (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        onUpdateCriteria(
                            StrategyCriteriaEntity(
                                minDyPercent = minDy.toDouble(),
                                maxPvp = maxPvp.toDouble(),
                                minDailyLiquidityMillions = minLiq.toDouble(),
                                maxInadimplenciaPercent = maxInad.toDouble(),
                                minRoePercent = minRoe.toDouble(),
                                minDividendConsistencyMonths = c.minDividendConsistencyMonths
                            ),
                            changeReason
                        )
                        changeReason = ""
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_strategy_criteria_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = EmeraldOnPrimary)
                ) {
                    Text("Salvar Critérios & Registrar Estratégia")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        "Ranking Fundamentalista (${rankedAssets.size}/$totalEligibleCount)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("fundamental_ranking_header")
                    )
                    Text(
                        "Visíveis apenas ativos que atendem a todos os filtros",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                OutlinedButton(
                    onClick = {
                        val tickersToRefresh = if (rankedAssets.isNotEmpty()) {
                            rankedAssets.take(12).map { it.asset.ticker }
                        } else {
                            state.marketAssets
                                .filter { it.assetClass != AssetClass.RENDA_FIXA }
                                .take(12)
                                .map { it.ticker }
                        }
                        onRefreshLiveBatch(tickersToRefresh)
                    },
                    enabled = !state.isLiveBatchRefreshing,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("refresh_live_catalog_button")
                ) {
                    if (state.isLiveBatchRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Atualizando...")
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Atualizar Ao Vivo")
                    }
                }
            }
        }

        if (rankedAssets.isEmpty()) {
            item {
                FintechCard(modifier = Modifier.testTag("fundamental_ranking_empty_card")) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FilterAltOff,
                            contentDescription = "Nenhum ativo compatível com o filtro",
                            tint = AmberGold,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            "Nenhum ativo atende a todos os filtros ativos",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Ajuste DY mínimo, P/VP máximo, liquidez, inadimplência ou ROE para exibir os ativos compatíveis no ranking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        OutlinedButton(
                            onClick = {
                                val defaults = StrategyCriteriaEntity()
                                minDy = defaults.minDyPercent.toFloat()
                                maxPvp = defaults.maxPvp.toFloat()
                                minLiq = defaults.minDailyLiquidityMillions.toFloat()
                                maxInad = defaults.maxInadimplenciaPercent.toFloat()
                                minRoe = defaults.minRoePercent.toFloat()
                            },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("reset_fundamental_filters_button")
                        ) {
                            Text("Restaurar Filtros Padrão")
                        }
                    }
                }
            }
        } else {
            items(rankedAssets, key = { it.asset.ticker }) { item ->
                val asset = item.asset
                val valuation = item.valuation
                val score = item.score
                FintechCard(
                    modifier = Modifier
                        .testTag("fundamental_asset_card_${asset.ticker}")
                        .clickable {
                            inspectedTicker = asset.ticker
                            onQueryLiveQuote(asset.ticker)
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${asset.ticker} • ${asset.name}", style = MaterialTheme.typography.titleSmall)
                                if (!asset.isDemoData) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("• AO VIVO", style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary)
                                }
                            }
                            Text(
                                "${formatBrl(asset.price)} (${formatPct(asset.changePercent1d)}) • DY ${formatPct(asset.dy12m, false)} • P/VP ${"%.2f".format(asset.pvp)}x • Liq R$${"%.1f".format(asset.liquidezDiariaMilhoes)}M",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            val bazinText = valuation.bazinCeilingPriceBrl?.let { " • Teto Bazin ${formatBrl(it)}" }.orEmpty()
                            Text(
                                "Atende filtros • ${item.matchedLabels.take(3).joinToString(" • ")}$bazinText",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (score >= 80) EmeraldContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "$score/100",
                                style = MonoValueStyle,
                                color = if (score >= 80) EmeraldPrimary else AmberGold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        if (state.strategyChangeLog.isNotEmpty()) {
            item {
                val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
                FintechCard {
                    Text("Histórico de Mudanças de Estratégia", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    state.strategyChangeLog.take(10).forEach { log ->
                        Text("• ${fmt.format(Date(log.changedAt))}: ${log.summary}", style = MaterialTheme.typography.bodySmall, color = EmeraldPrimary)
                        Text(log.details, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
            }
        }
    }

    if (inspectedAsset != null) {
        val isLoadingSheet = state.liveLookupState is LiveQuoteLookupState.Loading &&
            state.liveLookupState.ticker.equals(inspectedAsset.ticker, ignoreCase = true)
        ModalBottomSheet(onDismissRequest = { inspectedTicker = null }) {
            AssetDetailSheetContent(
                asset = inspectedAsset,
                onQueryLiveQuote = onQueryLiveQuote,
                isLiveLoading = isLoadingSheet
            )
        }
    }
}

@Composable
fun TechnicalAnalysisScreen(
    state: InvestDashboardState,
    onQueryLiveQuote: (String) -> Unit = {},
    onApplyLiveQuoteToPortfolio: (String) -> Unit = {},
    onCreatePriceAlert: (String, Double, PriceAlertCondition, String) -> Unit = { _, _, _, _ -> },
    onTogglePriceAlert: (Long, Boolean) -> Unit = { _, _ -> },
    onRearmPriceAlert: (Long) -> Unit = {},
    onDeletePriceAlert: (Long) -> Unit = {},
    onCheckPriceAlertsNow: () -> Unit = {},
    onNotificationPermissionGranted: () -> Unit = {}
) {
    val assets = state.marketAssets
    val ownedTickers = remember(state.realPositions) {
        state.realPositions.map { it.ticker.uppercase() }.toSet()
    }
    val quickTickers = remember(state.realPositions, assets) {
        (state.realPositions.map { it.ticker } + assets.map { it.ticker })
            .distinct()
            .take(16)
    }

    var selectedTicker by remember { mutableStateOf(quickTickers.firstOrNull() ?: assets.first().ticker) }

    LaunchedEffect(state.liveLookupState) {
        val success = state.liveLookupState as? LiveQuoteLookupState.Success
        if (success != null) {
            selectedTicker = success.quote.ticker
        }
    }

    val activeAsset = assets.firstOrNull { it.ticker.equals(selectedTicker, ignoreCase = true) }
        ?: assets.first()

    val trendLabel = when {
        activeAsset.price > activeAsset.mm9 && activeAsset.mm9 > activeAsset.mm21 -> "Tendência de Alta (Preço > MM9 > MM21)"
        activeAsset.price < activeAsset.mm9 && activeAsset.mm9 < activeAsset.mm21 -> "Tendência de Baixa (Preço < MM9 < MM21)"
        else -> "Consolidação / Lateralização"
    }
    val rsiLabel = when {
        activeAsset.rsi14 >= 70.0 -> "Sobrecomprado (IFR >= 70)"
        activeAsset.rsi14 <= 35.0 -> "Sobrevendido (IFR <= 35)"
        else -> "Zona Neutra"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("technical_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DataDisclaimerBanner(
                text = if (activeAsset.isDemoData) {
                    "Análise Técnica com Médias Móveis (MM9, MM21, MM200), IFR(14), Suporte e Resistência. Consulte qualquer ticker abaixo para carregar série e cotação em tempo real (B3 / ADVFN / Yahoo B3)."
                } else {
                    "Dados técnicos em tempo real de ${activeAsset.ticker} via ${activeAsset.sourceLabel} (${activeAsset.dataBaseDate})."
                }
            )
        }

        item {
            LiveMarketLookupPanel(
                lookupState = state.liveLookupState,
                quickTickers = quickTickers,
                ownedTickers = ownedTickers,
                onQueryTicker = { ticker ->
                    selectedTicker = ticker.trim().uppercase()
                    onQueryLiveQuote(ticker)
                },
                onApplyPriceToPortfolio = onApplyLiveQuoteToPortfolio,
                initialTicker = selectedTicker
            )
        }

        item {
            PriceTargetAlertsCard(
                alerts = state.priceTargetAlerts,
                marketAssets = state.marketAssets,
                suggestedTicker = selectedTicker,
                isCheckingAlerts = state.isCheckingPriceAlerts,
                onCreateAlert = onCreatePriceAlert,
                onToggleAlert = onTogglePriceAlert,
                onRearmAlert = onRearmPriceAlert,
                onDeleteAlert = onDeletePriceAlert,
                onCheckAlertsNow = onCheckPriceAlertsNow,
                onNotificationPermissionGranted = onNotificationPermissionGranted
            )
        }

        item {
            FilterChipBar(
                options = quickTickers.map { it to it },
                selected = activeAsset.ticker,
                onSelect = { ticker ->
                    selectedTicker = ticker
                    onQueryLiveQuote(ticker)
                }
            )
        }

        item {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${activeAsset.ticker} • ${activeAsset.name}", style = MaterialTheme.typography.titleMedium)
                        Text(trendLabel, style = MaterialTheme.typography.bodySmall, color = ElectricCyan)
                        Text(activeAsset.sourceLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatBrl(activeAsset.price), style = MonoValueStyle, color = EmeraldPrimary)
                        Text(
                            formatPct(activeAsset.changePercent1d),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (activeAsset.changePercent1d >= 0) EmeraldPrimary else CoralRed
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TechnicalPriceChart(
                    prices = activeAsset.priceSeries30d,
                    mm9 = activeAsset.mm9,
                    mm21 = activeAsset.mm21,
                    support = activeAsset.supportPrice,
                    resistance = activeAsset.resistancePrice
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Legenda: Verde = Preço • Ciano = MM9 (${formatBrl(activeAsset.mm9)}) • Âmbar = MM21 (${formatBrl(activeAsset.mm21)}) • Pontilhado Verde/Coral = Suporte/Resistência",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        item {
            FintechCard {
                Text("Indicadores Técnicos", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("IFR (14)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("${"%.1f".format(activeAsset.rsi14)} • $rsiLabel", style = MonoValueStyle, color = AmberGold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("MM200 (Longo Prazo)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(formatBrl(activeAsset.mm200), style = MonoValueStyle)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Suporte Principal", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(formatBrl(activeAsset.supportPrice), style = MonoValueStyle, color = EmeraldPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Resistência Principal", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(formatBrl(activeAsset.resistancePrice), style = MonoValueStyle, color = CoralRed)
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.ComparisonSeriesBuilder
import com.example.domain.model.DataComGroup
import com.example.domain.model.DividendMonitorCalculator
import com.example.domain.model.LiveQuoteLookupState
import com.example.ui.components.DataDisclaimerBanner
import com.example.ui.components.FintechCard
import com.example.ui.components.HorizontalBarDistribution
import com.example.ui.components.InteractiveDonutChart
import com.example.ui.components.InteractiveMultiLineChart
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.PortfolioProfitabilityEvolutionCard
import com.example.ui.components.SecurityGuardBanner
import com.example.ui.components.formatBrl
import com.example.ui.components.formatPct
import com.example.ui.navigation.ALL_INVEST_MODULES
import com.example.ui.navigation.InvestRoutes
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.InvestDashboardState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    state: InvestDashboardState,
    onNavigate: (String) -> Unit,
    onLoadDemoPortfolio: () -> Unit,
    onOpenDataComGroup: (DataComGroup) -> Unit = {},
    onOpenPortfolioClass: (AssetClass) -> Unit = {},
    onToggleHideBanners: (Boolean) -> Unit = {},
    onRecordDividend: (ticker: String, totalReceivedBrl: Double, notes: String) -> Unit = { _, _, _ -> },
    onQueryLiveQuote: (String) -> Unit = {}
) {
    val realHhi = state.realHhi
    val hasRealData = state.realPositions.isNotEmpty()
    val isRealDemo = state.latestRealImport?.isDemoData == true || state.realPositions.any { it.isDemo }
    val hideBanners = state.preferences.hideInfoBanners

    val realCostBrl = remember(state.realPositions) { state.realPositions.sumOf { it.totalCostBrl } }
    val realPnlBrl = remember(state.realPositions, realHhi.totalInvestedBrl, realCostBrl) {
        if (state.realPositions.isEmpty()) 0.0 else realHhi.totalInvestedBrl - realCostBrl
    }
    val realPnlPct = remember(realPnlBrl, realCostBrl) {
        if (realCostBrl > 0.0) (realPnlBrl / realCostBrl) * 100.0 else 0.0
    }
    val realCryptoPositions = remember(state.realPositions) {
        state.realPositions.filter { it.assetClass == AssetClass.CRIPTO }
    }
    val realCryptoTotalBrl = remember(realCryptoPositions) {
        realCryptoPositions.sumOf { it.totalCurrentBrl }
    }

    val dividendSummary = remember(state.realPositions, state.marketAssets, state.operations) {
        DividendMonitorCalculator.compute(
            realPositions = state.realPositions,
            marketAssets = state.marketAssets,
            operations = state.operations
        )
    }

    val comparisonCandidates = remember(state.benchmarks, state.realPositions, state.marketAssets) {
        ComparisonSeriesBuilder.buildCandidates(
            benchmarks = state.benchmarks,
            realPositions = state.realPositions,
            marketAssets = state.marketAssets
        )
    }

    var selectedComparisonIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hasSeededComparisonDefaults by remember { mutableStateOf(false) }
    var compareTickerInput by remember { mutableStateOf("") }
    var pendingCompareTickers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var compareFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(comparisonCandidates) {
        val validIds = comparisonCandidates.map { it.id }.toSet()
        if (!hasSeededComparisonDefaults && comparisonCandidates.isNotEmpty()) {
            val defaultIds = comparisonCandidates
                .filter {
                    it.id.startsWith("BENCH:CDI") ||
                        it.id.startsWith("BENCH:IBOV") ||
                        it.id.startsWith("BENCH:BTC") ||
                        it.groupLabel == "Carteira Real"
                }
                .take(5)
                .map { it.id }
                .ifEmpty { comparisonCandidates.take(4).map { it.id } }
                .toSet()
            selectedComparisonIds = defaultIds
            hasSeededComparisonDefaults = true
        } else {
            val retained = selectedComparisonIds.filter { it in validIds }.toSet()
            val resolvedPending = mutableSetOf<String>()
            val newIds = pendingCompareTickers.mapNotNull { pending ->
                val match = comparisonCandidates.firstOrNull {
                    it.shortLabel.equals(pending, ignoreCase = true) ||
                        it.id.equals("ASSET:$pending", ignoreCase = true) ||
                        it.id.equals("BENCH:$pending", ignoreCase = true)
                }
                if (match != null) {
                    resolvedPending.add(pending)
                    match.id
                } else {
                    null
                }
            }.toSet()
            if (resolvedPending.isNotEmpty()) {
                pendingCompareTickers = pendingCompareTickers - resolvedPending
                compareFeedback = "Adicionado ao comparativo: ${resolvedPending.joinToString(", ")}"
            }
            val merged = retained + newIds
            if (merged.isNotEmpty()) {
                selectedComparisonIds = merged
            }
        }
    }

    LaunchedEffect(state.liveLookupState) {
        val err = state.liveLookupState as? LiveQuoteLookupState.Error
        if (err != null && err.ticker.uppercase() in pendingCompareTickers) {
            pendingCompareTickers = pendingCompareTickers - err.ticker.uppercase()
            compareFeedback = err.message
        }
    }

    fun submitCompareTickerInput() {
        val tokens = compareTickerInput
            .split(',', ';', ' ')
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return

        var updatedIds = selectedComparisonIds
        val missingLive = mutableListOf<String>()
        val foundLabels = mutableListOf<String>()

        tokens.forEach { token ->
            val match = comparisonCandidates.firstOrNull {
                it.shortLabel.equals(token, ignoreCase = true) ||
                    it.id.equals("ASSET:$token", ignoreCase = true) ||
                    it.id.equals("BENCH:$token", ignoreCase = true)
            }
            if (match != null) {
                updatedIds = updatedIds + match.id
                foundLabels.add(match.shortLabel)
            } else {
                missingLive.add(token)
            }
        }

        selectedComparisonIds = updatedIds
        if (missingLive.isNotEmpty()) {
            pendingCompareTickers = pendingCompareTickers + missingLive.toSet()
            missingLive.forEach { onQueryLiveQuote(it) }
            compareFeedback = "Buscando ${missingLive.joinToString(", ")} em tempo real (B3 / ADVFN / Yahoo)..."
        } else if (foundLabels.isNotEmpty()) {
            compareFeedback = "Comparando: ${foundLabels.joinToString(", ")}"
        }
        compareTickerInput = ""
    }

    val activeComparisonSeries = remember(comparisonCandidates, selectedComparisonIds) {
        val chosen = comparisonCandidates.filter { it.id in selectedComparisonIds }.map { it.series }
        chosen.ifEmpty { comparisonCandidates.take(3).map { it.series } }
    }

    val visibleComparisonChips = remember(comparisonCandidates, selectedComparisonIds) {
        val selectedFirst = comparisonCandidates.filter { it.id in selectedComparisonIds }
        (selectedFirst + comparisonCandidates.take(14)).distinctBy { it.id }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("dashboard_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (hideBanners) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = EmeraldContainer.copy(alpha = 0.35f),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("dashboard_show_banners_button")
                            .clickable { onToggleHideBanners(false) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Mostrar avisos",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Mostrar avisos (${state.realPositions.size} ativos)",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldPrimary
                            )
                        }
                    }
                }
            }
        } else {
            item {
                SecurityGuardBanner(
                    onHide = { onToggleHideBanners(true) },
                    hideButtonTag = "dashboard_hide_banners_button"
                )
            }

            item {
                if (!hasRealData) {
                    FintechCard(borderColor = AmberGold.copy(alpha = 0.5f)) {
                        Text(
                            "Carteira Real sem dados importados",
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberGold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Nenhuma posição real foi inventada. Importe sua planilha XLSX ou CSV na aba Carteira, registre posições manualmente ou carregue a carteira demonstrativa fictícia para explorar os gráficos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onNavigate(InvestRoutes.ROUTE_CARTEIRA) },
                                modifier = Modifier.testTag("dashboard_go_import_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = EmeraldOnPrimary)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Importar XLSX/CSV")
                            }
                            OutlinedButton(
                                onClick = onLoadDemoPortfolio,
                                modifier = Modifier.testTag("dashboard_load_demo_button")
                            ) {
                                Text("Carregar Demo (Fictícia)")
                            }
                        }
                    }
                } else if (isRealDemo) {
                    DataDisclaimerBanner(
                        text = "A Carteira Real exibe atualmente DADOS FICTÍCIOS DE DEMONSTRAÇÃO (${state.latestRealImport?.fileName}). Importe um CSV/XLSX oficial para substituir.",
                        isWarning = true,
                        onHide = { onToggleHideBanners(true) }
                    )
                } else {
                    DataDisclaimerBanner(
                        text = "Referência da Carteira Real: ${state.latestRealImport?.fileName ?: "Registro Manual"} • ${state.realPositions.size} posições locais.",
                        onHide = { onToggleHideBanners(true) }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiMetricCard(
                    title = "Patrimônio Real",
                    value = formatBrl(realHhi.totalEquityBrl),
                    subtitle = "Caixa: ${formatBrl(realHhi.cashBrl)} • ${state.realPositions.size} ativos",
                    icon = Icons.Default.AccountBalanceWallet,
                    accentColor = EmeraldPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(InvestRoutes.ROUTE_CARTEIRA) }
                )
                KpiMetricCard(
                    title = "Concentração HHI",
                    value = "${realHhi.hhiScore.toInt()} pts",
                    subtitle = realHhi.classification,
                    icon = Icons.Default.Shield,
                    accentColor = when {
                        realHhi.hhiScore > 2500 -> CoralRed
                        realHhi.hhiScore >= 1500 -> AmberGold
                        else -> ElectricCyan
                    },
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(InvestRoutes.ROUTE_RISCO_HHI) }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val pnlPrefix = if (realPnlBrl > 0) "+" else ""
                KpiMetricCard(
                    title = "Resultado Carteira Real",
                    value = if (hasRealData) "$pnlPrefix${formatBrl(realPnlBrl)}" else formatBrl(0.0),
                    subtitle = if (hasRealData) "Custo PM: ${formatBrl(realCostBrl)} (${formatPct(realPnlPct)})" else "Importe ou cadastre ativos",
                    icon = Icons.Default.TrendingUp,
                    accentColor = if (realPnlBrl >= 0) EmeraldPrimary else CoralRed,
                    modifier = Modifier.weight(1f).testTag("kpi_real_pnl_card"),
                    onClick = { onNavigate(InvestRoutes.ROUTE_CARTEIRA) }
                )
                KpiMetricCard(
                    title = "Cripto na Carteira Real",
                    value = formatBrl(realCryptoTotalBrl),
                    subtitle = if (realCryptoPositions.isNotEmpty()) {
                        "${realCryptoPositions.size} ativo(s) cripto na carteira"
                    } else {
                        "0 na Carteira Real (Módulo 8 p/ simulação)"
                    },
                    icon = Icons.Default.CurrencyBitcoin,
                    accentColor = AmberGold,
                    modifier = Modifier.weight(1f).testTag("kpi_real_crypto_card"),
                    onClick = {
                        if (realCryptoPositions.isNotEmpty()) {
                            onOpenPortfolioClass(AssetClass.CRIPTO)
                        } else {
                            onNavigate(InvestRoutes.ROUTE_CARTEIRA)
                        }
                    }
                )
            }
        }

        item {
            PortfolioProfitabilityEvolutionCard(
                realPositions = state.realPositions,
                marketAssets = state.marketAssets,
                operations = state.operations,
                benchmarks = state.benchmarks,
                onGoImport = { onNavigate(InvestRoutes.ROUTE_CARTEIRA) },
                onLoadDemo = onLoadDemoPortfolio
            )
        }

        item {
            FintechCard {
                Text("Distribuição por Classe (Carteira Real)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Toque em uma fatia ou classe para abrir a lista de ativos",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                InteractiveDonutChart(
                    slices = realHhi.byClass,
                    centerLabel = "Investido",
                    centerValue = formatBrl(realHhi.totalInvestedBrl),
                    sliceTestTagPrefix = "dashboard_class_slice",
                    onSliceOpen = { slice ->
                        val resolved = resolveAssetClassFromSliceLabel(slice.label)
                        if (resolved != null) {
                            onOpenPortfolioClass(resolved)
                        }
                    }
                )
            }
        }

        item {
            DividendMonitorDashboardCard(
                summary = dividendSummary,
                onOpenCalendar = { onNavigate(InvestRoutes.ROUTE_CALENDARIO) },
                onRecordDividend = onRecordDividend
            )
        }

        item {
            FintechCard(modifier = Modifier.testTag("dashboard_comparison_card")) {
                Text("Comparativo de Ativos & Benchmarks (Base 100)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Digite qualquer ticker (ex: PETR4, VALE3, ITUB4) ou toque nos atalhos para comparar no gráfico",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = compareTickerInput,
                        onValueChange = { compareTickerInput = it.uppercase() },
                        label = { Text("Ticker para comparar (ex: PETR4, WEGE3)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { submitCompareTickerInput() }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_compare_ticker_input")
                    )
                    Button(
                        onClick = { submitCompareTickerInput() },
                        enabled = compareTickerInput.isNotBlank(),
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("dashboard_compare_ticker_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = EmeraldOnPrimary
                        )
                    ) {
                        Text("Comparar")
                    }
                }
                if (compareFeedback != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (pendingCompareTickers.isNotEmpty() && state.liveLookupState is LiveQuoteLookupState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                        Text(
                            compareFeedback.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (compareFeedback.orEmpty().contains("Não foi possível") || compareFeedback.orEmpty().contains("Erro")) {
                                CoralRed
                            } else {
                                ElectricCyan
                            },
                            modifier = Modifier.testTag("dashboard_compare_feedback")
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    visibleComparisonChips.forEach { candidate ->
                        val isSelected = candidate.id in selectedComparisonIds
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedComparisonIds = if (isSelected && selectedComparisonIds.size > 1) {
                                    selectedComparisonIds - candidate.id
                                } else {
                                    selectedComparisonIds + candidate.id
                                }
                            },
                            label = {
                                Text(candidate.shortLabel, style = MaterialTheme.typography.labelSmall)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldPrimary
                            ),
                            modifier = Modifier.testTag("compare_picker_chip_${candidate.shortLabel}")
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                InteractiveMultiLineChart(seriesList = activeComparisonSeries)
            }
        }

        item {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mapa Rápido de Datas-Com (G1–G4)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Toque na linha colorida para abrir a lista de ativos do grupo",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = EmeraldPrimary)
                }
                Spacer(Modifier.height(10.dp))
                val groups = DataComGroup.entries
                val realByTicker = state.realPositions.associateBy { it.ticker.trim().uppercase() }
                val marketByTicker = state.marketAssets.associateBy { it.ticker.trim().uppercase() }
                val effectiveGroupByTicker = (realByTicker.keys + marketByTicker.keys).distinct().mapNotNull { tk ->
                    val isCyclical = realByTicker[tk]?.isCyclical ?: marketByTicker[tk]?.isCyclical ?: true
                    val effectiveDay = realByTicker[tk]?.dataComDay
                        ?: state.liveQuotesByTicker[tk]?.dataComDay
                        ?: marketByTicker[tk]?.dataComDay
                    DataComGroup.fromDay(effectiveDay, isCyclical)?.let { tk to it }
                }
                val groupCounts = groups.map { g ->
                    g.label to effectiveGroupByTicker.count { it.second == g }.toDouble()
                }
                HorizontalBarDistribution(
                    items = groupCounts,
                    unitSuffix = " ativos",
                    itemTestTagPrefix = "dashboard_datacom_bar",
                    onItemClick = { idx, _ ->
                        groups.getOrNull(idx)?.let(onOpenDataComGroup)
                    }
                )
            }
        }

        item {
            FintechCard {
                Text("Todos os ${ALL_INVEST_MODULES.size} Módulos da Plataforma", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ALL_INVEST_MODULES.forEach { mod ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = EmeraldContainer.copy(alpha = 0.35f),
                            modifier = Modifier
                                .testTag("quick_module_${mod.number}")
                                .clickable { onNavigate(mod.route) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(mod.icon, contentDescription = mod.title, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("${mod.number}. ${mod.shortTitle}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        item {
            FintechCard {
                Text("Radar de Riscos & Auditoria HHI", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                realHhi.alerts.forEach { alert ->
                    Text("• $alert", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(vertical = 2.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Última verificação local", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("HHI: ${realHhi.hhiScore.toInt()} / 10.000", style = MonoValueStyle, color = EmeraldPrimary)
                }
            }
        }
    }
}

internal fun resolveAssetClassFromSliceLabel(label: String): AssetClass? =
    AssetClass.entries.firstOrNull {
        it.shortLabel.equals(label, ignoreCase = true) ||
            it.label.equals(label, ignoreCase = true) ||
            it.name.equals(label, ignoreCase = true)
    }

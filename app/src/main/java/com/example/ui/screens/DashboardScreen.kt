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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.ComparisonSeriesBuilder
import com.example.domain.model.DataComGroup
import com.example.domain.model.DividendMonitorCalculator
import com.example.ui.components.DataDisclaimerBanner
import com.example.ui.components.FintechCard
import com.example.ui.components.HorizontalBarDistribution
import com.example.ui.components.InteractiveDonutChart
import com.example.ui.components.InteractiveMultiLineChart
import com.example.ui.components.KpiMetricCard
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
    onRecordDividend: (ticker: String, totalReceivedBrl: Double, notes: String) -> Unit = { _, _, _ -> }
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

    var selectedComparisonIds by remember(comparisonCandidates) {
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
        mutableStateOf(defaultIds)
    }

    val activeComparisonSeries = remember(comparisonCandidates, selectedComparisonIds) {
        val chosen = comparisonCandidates.filter { it.id in selectedComparisonIds }.map { it.series }
        chosen.ifEmpty { comparisonCandidates.take(3).map { it.series } }
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
            FintechCard {
                Text("Comparativo de Ativos & Benchmarks (Base 100)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Escolha os ativos da Carteira Real, mercado e benchmarks para comparar no gráfico",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    comparisonCandidates.take(14).forEach { candidate ->
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
                val groupCounts = groups.map { g ->
                    val marketTickers = state.marketAssets.filter { it.dataComGroup == g }.map { it.ticker }
                    val realTickers = state.realPositions.filter { DataComGroup.fromDay(it.dataComDay) == g }.map { it.ticker }
                    val distinctCount = (marketTickers + realTickers).distinct().size.toDouble()
                    g.label to distinctCount
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

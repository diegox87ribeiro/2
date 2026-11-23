package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AssetClass
import com.example.domain.model.DataComGroup
import com.example.domain.model.DividendDateAlertEvaluator
import com.example.domain.model.DividendMonitorCalculator
import com.example.ui.components.DataDisclaimerBanner
import com.example.ui.components.FilterChipBar
import com.example.ui.components.FintechCard
import com.example.ui.components.GroupBadge
import com.example.ui.components.HorizontalBarDistribution
import com.example.ui.components.MonthlyDividendBarChart
import com.example.ui.components.formatBrl
import com.example.ui.components.formatPct
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.InvestDashboardState
import java.util.Calendar

private enum class DividendScreenTab {
    MONTHLY_CONTROL,
    BY_ASSET_12M,
    DATACOM_GROUPS
}

private enum class MonthlyAssetFilter(val label: String) {
    ALL("Todos"),
    PENDING("Pendentes"),
    RECEIVED("Recebidos"),
    FIIS("FIIs / FIAGRO"),
    STOCKS_ETFS("Ações / ETFs")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DividendCalendarScreen(
    state: InvestDashboardState,
    onOpenDataComGroup: (DataComGroup) -> Unit = {},
    onRecordDividend: (ticker: String, quantity: Double, unitPrice: Double, dateIso: String, notes: String) -> Unit = { _, _, _, _, _ -> },
    onDeleteDividendOperation: (Long) -> Unit = {},
    onUpdateDividendDateAlerts: (enabled: Boolean, leadDays: Int, notifyDataCom: Boolean, notifyDataEx: Boolean) -> Unit = { _, _, _, _ -> },
    onCheckDividendDateAlertsNow: () -> Unit = {},
    onSendTestDividendDateAlert: () -> Unit = {}
) {
    val nowCal = remember { Calendar.getInstance() }
    var activeTab by remember { mutableStateOf(DividendScreenTab.MONTHLY_CONTROL) }
    var selectedYear by remember { mutableIntStateOf(nowCal.get(Calendar.YEAR)) }
    var selectedMonthIndex by remember { mutableIntStateOf(nowCal.get(Calendar.MONTH)) }
    var selectedAssetFilter by remember { mutableStateOf(MonthlyAssetFilter.ALL) }
    var selectedSingleTicker by remember { mutableStateOf<String?>(null) }
    var selectedGroup by remember { mutableStateOf<DataComGroup?>(null) }
    var recordDraft by remember { mutableStateOf<DividendRecordDraft?>(null) }

    val dividendSummary = remember(state.realPositions, state.marketAssets, state.operations, selectedYear) {
        DividendMonitorCalculator.compute(
            realPositions = state.realPositions,
            marketAssets = state.marketAssets,
            operations = state.operations,
            selectedYear = selectedYear
        )
    }

    val nextDividendDateEvents = remember(
        state.realPositions,
        state.marketAssets,
        state.liveQuotesByTicker,
        state.preferences.dividendAlertDataCom,
        state.preferences.dividendAlertDataEx
    ) {
        DividendDateAlertEvaluator.findNextEventsForPortfolio(
            realPositions = state.realPositions,
            marketAssets = state.marketAssets,
            liveQuotesByTicker = state.liveQuotesByTicker,
            notifyDataCom = state.preferences.dividendAlertDataCom,
            notifyDataEx = state.preferences.dividendAlertDataEx
        )
    }

    val selectedMonthPoint = dividendSummary.monthlyJanToDec.getOrNull(selectedMonthIndex)
    val monthReceivedTotal = selectedMonthPoint?.receivedBrl ?: 0.0
    val monthProjectedTotal = selectedMonthPoint?.projectedBrl ?: 0.0
    val monthProgress = when {
        monthProjectedTotal > 0.0 -> (monthReceivedTotal / monthProjectedTotal).toFloat().coerceIn(0f, 1f)
        monthReceivedTotal > 0.0 -> 1f
        else -> 0f
    }
    val monthPct = if (monthProjectedTotal > 0.0) (monthReceivedTotal / monthProjectedTotal) * 100.0 else if (monthReceivedTotal > 0.0) 100.0 else 0.0
    val monthNameFull = DividendMonitorCalculator.monthFullNamesPt.getOrElse(selectedMonthIndex) { "Mês" }
    val monthNameShort = DividendMonitorCalculator.monthLabelsPt.getOrElse(selectedMonthIndex) { "Mês" }

    val filteredMonthlyAssets = remember(dividendSummary.perAssetRows, selectedAssetFilter, selectedMonthIndex) {
        dividendSummary.perAssetRows.filter { row ->
            val rec = row.receivedForMonth(selectedMonthIndex)
            val proj = row.projectedForMonth(selectedMonthIndex)
            when (selectedAssetFilter) {
                MonthlyAssetFilter.ALL -> true
                MonthlyAssetFilter.PENDING -> proj > 0.0 && rec < proj * 0.95
                MonthlyAssetFilter.RECEIVED -> rec > 0.0
                MonthlyAssetFilter.FIIS -> row.assetClass == AssetClass.FII || row.assetClass == AssetClass.FIAGRO
                MonthlyAssetFilter.STOCKS_ETFS -> row.assetClass == AssetClass.ACOES_BR || row.assetClass == AssetClass.ETF
            }
        }
    }

    val dividendAssets = remember(state.marketAssets) {
        state.marketAssets.filter { it.dataComDay != null }.sortedBy { it.dataComDay }
    }
    val filteredDataComAssets = remember(dividendAssets, selectedGroup) {
        if (selectedGroup == null) dividendAssets
        else dividendAssets.filter { it.dataComGroup == selectedGroup }
    }
    val realPositionsByTicker = remember(state.realPositions) {
        state.realPositions.associateBy { it.ticker }
    }
    val summaryRowByTicker = remember(dividendSummary.perAssetRows) {
        dividendSummary.perAssetRows.associateBy { it.ticker }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = activeTab.ordinal) {
            Tab(
                selected = activeTab == DividendScreenTab.MONTHLY_CONTROL,
                onClick = { activeTab = DividendScreenTab.MONTHLY_CONTROL },
                text = { Text(stringResource(R.string.dividend_tab_monthly), maxLines = 1) },
                modifier = Modifier.testTag("dividend_screen_tab_monthly")
            )
            Tab(
                selected = activeTab == DividendScreenTab.BY_ASSET_12M,
                onClick = { activeTab = DividendScreenTab.BY_ASSET_12M },
                text = { Text(stringResource(R.string.dividend_tab_by_asset), maxLines = 1) },
                modifier = Modifier.testTag("dividend_screen_tab_by_asset")
            )
            Tab(
                selected = activeTab == DividendScreenTab.DATACOM_GROUPS,
                onClick = { activeTab = DividendScreenTab.DATACOM_GROUPS },
                text = { Text(stringResource(R.string.dividend_tab_datacom), maxLines = 1) },
                modifier = Modifier.testTag("dividend_screen_tab_datacom")
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("dividend_calendar_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (activeTab != DividendScreenTab.DATACOM_GROUPS) {
                item {
                    FintechCard(borderColor = EmeraldPrimary.copy(alpha = 0.4f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.dividend_control_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "$monthNameFull/$selectedYear • Recebido vs Projetado",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Seletor de Ano
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            dividendSummary.availableYears.forEach { yr ->
                                FilterChip(
                                    selected = yr == selectedYear,
                                    onClick = { selectedYear = yr },
                                    label = { Text(yr.toString()) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldContainer,
                                        selectedLabelColor = EmeraldPrimary
                                    ),
                                    modifier = Modifier.testTag("dividend_year_chip_$yr")
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // Seletor de Mês (Jan–Dez)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DividendMonitorCalculator.monthLabelsPt.forEachIndexed { idx, label ->
                                FilterChip(
                                    selected = idx == selectedMonthIndex,
                                    onClick = { selectedMonthIndex = idx },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldContainer,
                                        selectedLabelColor = EmeraldPrimary
                                    ),
                                    modifier = Modifier.testTag("dividend_month_chip_$idx")
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactMetricBox(
                                label = "Recebido ($monthNameShort)",
                                value = formatBrl(monthReceivedTotal),
                                accent = EmeraldPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("calendar_month_received_value")
                            )
                            CompactMetricBox(
                                label = "Projetado ($monthNameShort)",
                                value = formatBrl(monthProjectedTotal),
                                accent = ElectricCyan,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("calendar_month_projected_value")
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactMetricBox(
                                label = "Recebido ($selectedYear)",
                                value = formatBrl(dividendSummary.receivedYearTotalBrl),
                                subValue = "12m móveis: ${formatBrl(dividendSummary.receivedAnnualBrl)}",
                                accent = EmeraldPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            CompactMetricBox(
                                label = "Projetado ($selectedYear)",
                                value = formatBrl(dividendSummary.projectedAnnualBrl),
                                subValue = "DY ${formatPct(dividendSummary.projectedAnnualDyPercent, false)} • YoC ${formatPct(dividendSummary.projectedYieldOnCostPercent, false)}",
                                accent = AmberGold,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { monthProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = if (monthReceivedTotal >= monthProjectedTotal && monthReceivedTotal > 0.0) EmeraldPrimary else ElectricCyan,
                            trackColor = SubtleBorder.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(4.dp))
                        val diffMonth = monthReceivedTotal - monthProjectedTotal
                        val diffSign = if (diffMonth >= 0) "+" else ""
                        Text(
                            text = "Realização de $monthNameFull: ${"%.0f".format(monthPct)}% • Diferença: $diffSign${formatBrl(diffMonth)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (diffMonth >= 0 && monthReceivedTotal > 0.0) EmeraldPrimary else TextSecondary
                        )

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val firstRow = dividendSummary.perAssetRows.firstOrNull()
                                recordDraft = DividendRecordDraft(
                                    ticker = firstRow?.ticker.orEmpty(),
                                    monthIndex = selectedMonthIndex,
                                    year = selectedYear,
                                    suggestedTotalBrl = firstRow?.projectedForMonth(selectedMonthIndex) ?: 0.0,
                                    suggestedPerUnitBrl = firstRow?.projectedPerUnitMonthBrl ?: 0.0,
                                    positionQuantity = firstRow?.quantity ?: 0.0,
                                    preferredDay = firstRow?.paymentDay
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = EmeraldOnPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("calendar_record_dividend_button")
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("${stringResource(R.string.dividend_record_cta)} ($monthNameShort/$selectedYear)")
                        }
                    }
                }
            }

            when (activeTab) {
                DividendScreenTab.MONTHLY_CONTROL -> {
                    if (dividendSummary.monthlyJanToDec.isNotEmpty() && dividendSummary.perAssetRows.isNotEmpty()) {
                        item {
                            FintechCard {
                                Text(
                                    "Proventos da Carteira — Recebidos vs Projetados ($selectedYear)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                MonthlyDividendBarChart(
                                    points = dividendSummary.monthlyJanToDec,
                                    selectedMonthIndex = selectedMonthIndex,
                                    onSelectMonth = { selectedMonthIndex = it },
                                    modifier = Modifier.testTag("calendar_monthly_bar_chart")
                                )
                            }
                        }
                    }

                    item {
                        DividendDateAlertCard(
                            preferences = state.preferences,
                            nextEvents = nextDividendDateEvents,
                            onUpdateConfig = onUpdateDividendDateAlerts,
                            onCheckNow = onCheckDividendDateAlertsNow,
                            onSendTestNotification = onSendTestDividendDateAlert
                        )
                    }

                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            MonthlyAssetFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = selectedAssetFilter == filter,
                                    onClick = { selectedAssetFilter = filter },
                                    label = { Text(filter.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldContainer,
                                        selectedLabelColor = EmeraldPrimary
                                    ),
                                    modifier = Modifier.testTag("dividend_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }

                    if (filteredMonthlyAssets.isEmpty()) {
                        item {
                            FintechCard {
                                Text(
                                    text = if (dividendSummary.perAssetRows.isEmpty()) {
                                        "Importe ou cadastre ativos na Carteira Real (ou registre um provento acima) para acompanhar o recebido e o projetado mês a mês por ativo."
                                    } else {
                                        "Nenhum ativo corresponde ao filtro \"${selectedAssetFilter.label}\" em $monthNameFull/$selectedYear."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        items(filteredMonthlyAssets, key = { it.ticker }) { row ->
                            MonthlyAssetDividendCard(
                                row = row,
                                selectedMonthIndex = selectedMonthIndex,
                                selectedYear = selectedYear,
                                onQuickRecord = { targetMonth ->
                                    recordDraft = DividendRecordDraft(
                                        ticker = row.ticker,
                                        monthIndex = targetMonth,
                                        year = selectedYear,
                                        suggestedTotalBrl = row.projectedForMonth(targetMonth),
                                        suggestedPerUnitBrl = row.projectedPerUnitMonthBrl,
                                        positionQuantity = row.quantity,
                                        preferredDay = row.paymentDay
                                    )
                                },
                                onOpenSingleAsset = {
                                    selectedSingleTicker = row.ticker
                                    activeTab = DividendScreenTab.BY_ASSET_12M
                                },
                                onDeleteOperation = onDeleteDividendOperation
                            )
                        }
                    }
                }

                DividendScreenTab.BY_ASSET_12M -> {
                    item {
                        SingleAssetMonthlyDetailSection(
                            rows = dividendSummary.perAssetRows,
                            selectedTicker = selectedSingleTicker,
                            selectedMonthIndex = selectedMonthIndex,
                            selectedYear = selectedYear,
                            onSelectTicker = { selectedSingleTicker = it },
                            onSelectMonth = { selectedMonthIndex = it },
                            onQuickRecord = { row, targetMonth ->
                                recordDraft = DividendRecordDraft(
                                    ticker = row.ticker,
                                    monthIndex = targetMonth,
                                    year = selectedYear,
                                    suggestedTotalBrl = row.projectedForMonth(targetMonth),
                                    suggestedPerUnitBrl = row.projectedPerUnitMonthBrl,
                                    positionQuantity = row.quantity,
                                    preferredDay = row.paymentDay
                                )
                            },
                            onDeleteOperation = onDeleteDividendOperation
                        )
                    }
                }

                DividendScreenTab.DATACOM_GROUPS -> {
                    item {
                        DividendDateAlertCard(
                            preferences = state.preferences,
                            nextEvents = nextDividendDateEvents,
                            onUpdateConfig = onUpdateDividendDateAlerts,
                            onCheckNow = onCheckDividendDateAlertsNow,
                            onSendTestNotification = onSendTestDividendDateAlert
                        )
                    }

                    item {
                        DataDisclaimerBanner(
                            text = "Calendário estratégico por grupos de Data-Com (G1: dias 1–8 • G2: 9–16 • G3: 17–24 • G4: 25–31). Datas sujeitas a confirmação no RI/B3."
                        )
                    }

                    item {
                        FintechCard {
                            Text("Fluxo de Proventos por Grupo G1–G4", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Projeção mensal na Carteira Real: ${formatBrl(dividendSummary.projectedMonthlyBrl)} / mês",
                                style = MonoValueStyle,
                                color = EmeraldPrimary
                            )
                            Spacer(Modifier.height(10.dp))
                            val groups = DataComGroup.entries
                            val groupCounts = groups.map { grp ->
                                val marketTickers = dividendAssets.filter { it.dataComGroup == grp }.map { it.ticker }
                                val realTickers = state.realPositions.filter { DataComGroup.fromDay(it.dataComDay) == grp }.map { it.ticker }
                                grp.label to (marketTickers + realTickers).distinct().size.toDouble()
                            }
                            HorizontalBarDistribution(
                                items = groupCounts,
                                unitSuffix = " ativos",
                                itemTestTagPrefix = "calendar_datacom_bar",
                                onItemClick = { idx, _ ->
                                    groups.getOrNull(idx)?.let(onOpenDataComGroup)
                                }
                            )
                        }
                    }

                    item {
                        FilterChipBar(
                            options = listOf(null to "Todos (G1–G4)") + DataComGroup.entries.map { it to it.label },
                            selected = selectedGroup,
                            onSelect = { selectedGroup = it }
                        )
                    }

                    items(filteredDataComAssets, key = { it.ticker }) { asset ->
                        val owned = realPositionsByTicker[asset.ticker]
                        val assetRow = summaryRowByTicker[asset.ticker]
                        val projectedBrl = assetRow?.projectedForMonth(selectedMonthIndex)
                            ?: owned?.let { it.quantity * asset.lastDividendBrl }
                            ?: 0.0
                        val receivedBrl = assetRow?.receivedForMonth(selectedMonthIndex) ?: 0.0

                        FintechCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(asset.ticker, style = MaterialTheme.typography.titleMedium, color = EmeraldPrimary)
                                        Spacer(Modifier.width(8.dp))
                                        GroupBadge(asset.dataComGroup)
                                    }
                                    Text("${asset.name} • ${asset.assetClass.shortLabel}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text(
                                        "Data-Com: Dia ${asset.dataComDay} • Pgto previsto: Dia ${asset.paymentDay ?: "-"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ElectricCyan
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatBrl(asset.lastDividendBrl) + "/cota", style = MonoValueStyle)
                                    Text("DY 12m: ${formatPct(asset.dy12m, false)}", style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary)
                                }
                            }
                            if (owned != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Carteira Real (${"%.0f".format(owned.quantity)} cotas) • $monthNameShort: Rec ${formatBrl(receivedBrl)} | Proj ${formatBrl(projectedBrl)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    recordDraft?.let { draft ->
        RecordMonthlyDividendDialog(
            draft = draft,
            portfolioRows = dividendSummary.perAssetRows,
            onDismiss = { recordDraft = null },
            onConfirm = { ticker, qty, unitPrice, dateIso, notes ->
                onRecordDividend(ticker, qty, unitPrice, dateIso, notes)
                recordDraft = null
            }
        )
    }
}

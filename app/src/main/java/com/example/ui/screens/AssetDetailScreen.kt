package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PortfolioOperationEntity
import com.example.domain.model.AssetClass
import com.example.domain.model.AssetDetailCalculator
import com.example.domain.model.AssetDetailTab
import com.example.domain.model.AssetEquityPoint
import com.example.domain.model.AveragePriceStep
import com.example.domain.model.DividendDisplayEvent
import com.example.domain.model.DividendEventType
import com.example.domain.model.DividendFilterState
import com.example.domain.model.DividendHistoryEngine
import com.example.domain.model.DividendPeriodFilter
import com.example.domain.model.DividendStatus
import com.example.domain.model.LiveQuoteLookupState
import com.example.domain.model.OperationType
import com.example.domain.model.OrderHistoryFilter
import com.example.domain.model.OrderPeriodFilter
import com.example.domain.model.PortfolioType
import com.example.ui.components.formatBrl
import com.example.ui.components.formatPct
import com.example.ui.theme.CancelledRed
import com.example.ui.theme.ConfirmedBlue
import com.example.ui.theme.DarkPremiumBg
import com.example.ui.theme.DarkPremiumBorder
import com.example.ui.theme.DarkPremiumCard
import com.example.ui.theme.DarkPremiumCardSecondary
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.PremiumTextPrimary
import com.example.ui.theme.PremiumTextSecondary
import com.example.ui.theme.ProjectedAmber
import com.example.ui.theme.ReceivedGreen
import com.example.ui.theme.TechBluePrimary
import com.example.ui.viewmodel.InvestDashboardState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetDetailScreen(
    ticker: String,
    initialTab: AssetDetailTab,
    state: InvestDashboardState,
    selectedPortfolioType: PortfolioType = PortfolioType.REAL,
    onQueryLiveQuote: (String) -> Unit = {},
    onUpdatePosition: (
        Long,
        String,
        String,
        AssetClass,
        Double,
        Double,
        Double?,
        String,
        Int?,
        String,
        Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
    onDeletePosition: (Long) -> Unit = {},
    onRecordOperation: (PortfolioType, String, OperationType, Double, Double, String) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateOperation: (Long, String, OperationType, Double, Double, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteOperation: (Long) -> Unit = {},
    onSaveDividendEvent: (
        id: Long?,
        ticker: String,
        eventType: DividendEventType,
        status: DividendStatus,
        exDate: String,
        paymentDate: String,
        unitAmount: Double,
        quantityReference: Double,
        totalAmount: Double,
        source: String,
        notes: String,
        isRecurring: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onSoftDeleteDividendEvent: (Long, String) -> Unit = { _, _ -> }
) {
    val cleanTicker = remember(ticker) { ticker.trim().uppercase(Locale.ROOT) }
    var selectedTab by remember(cleanTicker, initialTab) { mutableStateOf(initialTab) }
    var orderTypeFilter by remember { mutableStateOf(OrderHistoryFilter.ALL) }
    var orderPeriodFilter by remember { mutableStateOf(OrderPeriodFilter.ALL) }

    var showEditPositionSheet by remember { mutableStateOf(false) }
    var showNewOperationDialog by remember { mutableStateOf(false) }
    var editingOperation by remember { mutableStateOf<PortfolioOperationEntity?>(null) }
    var deletingOperation by remember { mutableStateOf<PortfolioOperationEntity?>(null) }
    var editingDividendDraft by remember { mutableStateOf<DividendEventFormDraft?>(null) }
    var deletingDividendEvent by remember { mutableStateOf<DividendDisplayEvent?>(null) }

    val activePositions = if (selectedPortfolioType == PortfolioType.REAL) state.realPositions else state.simPositions
    val activeHhi = if (selectedPortfolioType == PortfolioType.REAL) state.realHhi else state.simHhi
    val position = remember(activePositions, state.realPositions, state.simPositions, cleanTicker) {
        activePositions.firstOrNull { it.ticker.equals(cleanTicker, ignoreCase = true) }
            ?: state.realPositions.firstOrNull { it.ticker.equals(cleanTicker, ignoreCase = true) }
            ?: state.simPositions.firstOrNull { it.ticker.equals(cleanTicker, ignoreCase = true) }
    }
    val catalogAsset = remember(state.marketAssets, cleanTicker) {
        state.marketAssets.firstOrNull { it.ticker.equals(cleanTicker, ignoreCase = true) }
    }
    val liveQuote = remember(state.liveQuotesByTicker, cleanTicker) {
        state.liveQuotesByTicker[cleanTicker]
    }
    val isLookupLoading = state.liveLookupState is LiveQuoteLookupState.Loading &&
        state.liveLookupState.ticker.equals(cleanTicker, ignoreCase = true)

    val allAssetOperations = remember(state.operations, cleanTicker, selectedPortfolioType) {
        val scoped = state.operations.filter {
            it.ticker.trim().equals(cleanTicker, ignoreCase = true) &&
                it.portfolioType == selectedPortfolioType.name
        }
        scoped.ifEmpty {
            state.operations.filter { it.ticker.trim().equals(cleanTicker, ignoreCase = true) }
        }
    }
    val filteredOperations = remember(allAssetOperations, cleanTicker, orderTypeFilter, orderPeriodFilter) {
        AssetDetailCalculator.filterOperations(
            operations = allAssetOperations,
            ticker = cleanTicker,
            typeFilter = orderTypeFilter,
            periodFilter = orderPeriodFilter
        )
    }
    val averagePriceSteps = remember(allAssetOperations) {
        AssetDetailCalculator.buildAveragePriceTimeline(allAssetOperations)
    }
    val equityPoints = remember(position, averagePriceSteps, catalogAsset) {
        AssetDetailCalculator.buildEquityEvolution(position, averagePriceSteps, catalogAsset)
    }

    val allAssetDividends = remember(state.dividendEvents, state.realPositions, state.marketAssets, state.liveQuotesByTicker, cleanTicker) {
        DividendHistoryEngine.buildSnapshot(
            persistedEvents = state.dividendEvents,
            realPositions = state.realPositions,
            marketAssets = state.marketAssets,
            liveQuotesByTicker = state.liveQuotesByTicker,
            filter = DividendFilterState(
                selectedTicker = cleanTicker,
                period = DividendPeriodFilter.ALL
            )
        ).filteredEvents.filter { it.status != DividendStatus.CANCELLED }
    }
    val receivedDivTotal = remember(allAssetDividends) {
        allAssetDividends.filter { it.status == DividendStatus.RECEIVED }.sumOf { it.totalAmount }
    }
    val confirmedDivTotal = remember(allAssetDividends) {
        allAssetDividends.filter { it.status == DividendStatus.CONFIRMED }.sumOf { it.totalAmount }
    }
    val projectedDivTotal = remember(allAssetDividends) {
        allAssetDividends.filter { it.status == DividendStatus.PROJECTED }.sumOf { it.totalAmount }
    }

    val currentPrice = liveQuote?.price
        ?: position?.takeIf { it.hasMarketPrice }?.currentPrice
        ?: catalogAsset?.price
    val averagePrice = position?.averagePrice ?: 0.0
    val returnPct = position?.profitLossPercent ?: 0.0
    val returnColor = when {
        position == null || !position.hasMarketPrice -> ProjectedAmber
        returnPct >= 0.0 -> ReceivedGreen
        else -> CancelledRed
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkPremiumBg)
            .testTag("asset_detail_screen_$cleanTicker")
    ) {
        // ==================== RESUMO EXECUTIVO DO ATIVO ====================
        AssetDetailHeaderCard(
            ticker = cleanTicker,
            name = position?.name ?: catalogAsset?.name ?: cleanTicker,
            classLabel = position?.assetClass?.label ?: catalogAsset?.assetClass?.label ?: "Ativo",
            isCyclical = position?.isCyclical ?: false,
            groupCode = position?.dataComGroup?.code,
            currentPrice = currentPrice,
            averagePrice = averagePrice,
            quantity = position?.quantity ?: 0.0,
            currentValue = position?.totalCurrentBrl ?: 0.0,
            returnPct = returnPct,
            hasMarketPrice = position?.hasMarketPrice == true || liveQuote != null,
            returnColor = returnColor,
            isLoadingQuote = isLookupLoading,
            canEditPosition = position != null,
            onRefreshQuote = { onQueryLiveQuote(cleanTicker) },
            onEditPosition = { if (position != null) showEditPositionSheet = true }
        )

        // ==================== ABAS DOS 7 INDICADORES / SEÇÕES ====================
        ScrollableTabRow(
            selectedTabIndex = AssetDetailTab.entries.indexOf(selectedTab),
            containerColor = DarkPremiumCard,
            contentColor = ConfirmedBlue,
            edgePadding = 12.dp
        ) {
            AssetDetailTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selectedTab == tab) ConfirmedBlue else PremiumTextSecondary
                        )
                    },
                    modifier = Modifier.testTag("asset_detail_tab_${tab.name}")
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("asset_detail_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                AssetDetailTab.ORDERS -> {
                    item {
                        AssetDetailPremiumCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Histórico Completo de Ordens", style = MaterialTheme.typography.titleMedium, color = PremiumTextPrimary)
                                    Text(
                                        "Compras, vendas e proventos em ordem cronológica. Preço Médio nunca se mistura com a Cotação Atual.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PremiumTextSecondary
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showNewOperationDialog = true },
                                    modifier = Modifier.testTag("asset_detail_add_order_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Lançar")
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OrderHistoryFilter.entries.forEach { filter ->
                                    FilterChip(
                                        selected = orderTypeFilter == filter,
                                        onClick = { orderTypeFilter = filter },
                                        label = { Text(filter.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TechBluePrimary.copy(alpha = 0.25f),
                                            selectedLabelColor = ConfirmedBlue
                                        ),
                                        modifier = Modifier.testTag("order_type_filter_${filter.name}")
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OrderPeriodFilter.entries.forEach { period ->
                                    FilterChip(
                                        selected = orderPeriodFilter == period,
                                        onClick = { orderPeriodFilter = period },
                                        label = { Text(period.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ConfirmedBlue.copy(alpha = 0.20f),
                                            selectedLabelColor = ConfirmedBlue
                                        ),
                                        modifier = Modifier.testTag("order_period_filter_${period.name}")
                                    )
                                }
                            }
                        }
                    }

                    if (filteredOperations.isEmpty()) {
                        item {
                            AssetDetailPremiumCard {
                                Text("Nenhuma operação encontrada para os filtros selecionados.", style = MaterialTheme.typography.bodyMedium, color = PremiumTextSecondary)
                            }
                        }
                    } else {
                        items(filteredOperations, key = { it.id }) { op ->
                            val isBuy = op.operationType.equals(OperationType.COMPRA.name, ignoreCase = true)
                            val isSell = op.operationType.equals(OperationType.VENDA.name, ignoreCase = true)
                            val accent = when {
                                isBuy -> ReceivedGreen
                                isSell -> CancelledRed
                                else -> ProjectedAmber
                            }
                            AssetDetailPremiumCard(borderColor = accent.copy(alpha = 0.45f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = accent.copy(alpha = 0.18f)
                                            ) {
                                                Text(
                                                    text = op.operationType,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = accent,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(op.dateIso, style = MaterialTheme.typography.labelMedium, color = PremiumTextSecondary)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Qtd: ${"%.2f".format(op.quantity)} • Preço Exec.: ${formatBrl(op.unitPrice)}",
                                            style = MonoValueStyle.copy(fontSize = 12.sp),
                                            color = PremiumTextPrimary
                                        )
                                        if (op.notes.isNotBlank()) {
                                            Text(
                                                text = "Obs/Custos: ${op.notes}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PremiumTextSecondary
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(formatBrl(op.totalValue), style = MonoValueStyle, color = accent)
                                        Row {
                                            IconButton(
                                                onClick = { editingOperation = op },
                                                modifier = Modifier.size(40.dp).testTag("detail_edit_op_${op.id}")
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Editar operação", tint = ConfirmedBlue, modifier = Modifier.size(17.dp))
                                            }
                                            IconButton(
                                                onClick = { deletingOperation = op },
                                                modifier = Modifier.size(40.dp).testTag("detail_delete_op_${op.id}")
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir operação", tint = CancelledRed, modifier = Modifier.size(17.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AssetDetailTab.QUOTES -> {
                    item {
                        AssetDetailPremiumCard {
                            Text("Histórico e Dados de Cotação", style = MaterialTheme.typography.titleMedium, color = PremiumTextPrimary)
                            Spacer(Modifier.height(8.dp))
                            val priceSeries = liveQuote?.priceSeries30d?.takeIf { it.size >= 2 }
                                ?: catalogAsset?.priceSeries30d?.takeIf { it.size >= 2 }
                                ?: listOfNotNull(averagePrice.takeIf { it > 0.0 }, currentPrice)
                            if (priceSeries.size >= 2) {
                                AssetLineChartCanvas(
                                    points = priceSeries,
                                    lineColor = if (priceSeries.last() >= priceSeries.first()) ReceivedGreen else CancelledRed,
                                    modifier = Modifier.fillMaxWidth().height(150.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            DetailMetricRow("Cotação Atual", currentPrice?.let { formatBrl(it) } ?: "Não disponível", ConfirmedBlue)
                            DetailMetricRow(
                                "Variação do Dia",
                                (liveQuote?.changePercent1d ?: catalogAsset?.changePercent1d)?.let { formatPct(it) } ?: "—",
                                if ((liveQuote?.changePercent1d ?: catalogAsset?.changePercent1d ?: 0.0) >= 0.0) ReceivedGreen else CancelledRed
                            )
                            DetailMetricRow("Preço Médio da Posição (Referência)", formatBrl(averagePrice), PremiumTextPrimary)
                            liveQuote?.openPrice?.let { DetailMetricRow("Abertura", formatBrl(it), PremiumTextSecondary) }
                            liveQuote?.dayLow?.let { low ->
                                liveQuote.dayHigh?.let { high ->
                                    DetailMetricRow("Mín / Máx Dia", "${formatBrl(low)} / ${formatBrl(high)}", PremiumTextSecondary)
                                }
                            }
                            liveQuote?.low52w?.let { low ->
                                liveQuote.high52w?.let { high ->
                                    DetailMetricRow("Faixa 52 semanas", "${formatBrl(low)} — ${formatBrl(high)}", PremiumTextSecondary)
                                }
                            }
                            DetailMetricRow(
                                "Fonte da Cotação",
                                liveQuote?.primarySourceLabel ?: position?.dataSourceLabel ?: "Catálogo de referência",
                                PremiumTextSecondary
                            )
                        }
                    }
                }

                AssetDetailTab.DIVIDENDS -> {
                    item {
                        AssetDetailPremiumCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Histórico de Dividendos • $cleanTicker", style = MaterialTheme.typography.titleMedium, color = PremiumTextPrimary)
                                    Text(
                                        "Categorias independentes: Recebido, Confirmado e Projetado nunca são somados juntos.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PremiumTextSecondary
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        editingDividendDraft = DividendEventFormDraft(
                                            ticker = cleanTicker,
                                            quantityReference = (position?.quantity ?: 1.0).coerceAtLeast(1.0),
                                            isRecurring = position?.isCyclical ?: false
                                        )
                                    },
                                    modifier = Modifier.testTag("asset_detail_add_dividend_button")
                                ) {
                                    Icon(Icons.Default.Paid, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Novo")
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MiniStatusKpi("Recebido", formatBrl(receivedDivTotal), ReceivedGreen, Modifier.weight(1f))
                                MiniStatusKpi("Confirmado", formatBrl(confirmedDivTotal), ConfirmedBlue, Modifier.weight(1f))
                                MiniStatusKpi("Projetado", formatBrl(projectedDivTotal), ProjectedAmber, Modifier.weight(1f))
                            }
                        }
                    }

                    if (allAssetDividends.isEmpty()) {
                        item {
                            AssetDetailPremiumCard {
                                Text("Nenhum provento registrado ou projetado para $cleanTicker.", style = MaterialTheme.typography.bodyMedium, color = PremiumTextSecondary)
                            }
                        }
                    } else {
                        items(allAssetDividends, key = { "${it.id}_${it.paymentDate}_${it.status.name}_${it.isAutoGeneratedProjection}" }) { ev ->
                            val color = when (ev.status) {
                                DividendStatus.RECEIVED -> ReceivedGreen
                                DividendStatus.CONFIRMED -> ConfirmedBlue
                                DividendStatus.PROJECTED -> ProjectedAmber
                                DividendStatus.CANCELLED -> CancelledRed
                            }
                            AssetDetailPremiumCard(borderColor = color.copy(alpha = 0.45f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.18f)) {
                                                Text(
                                                    text = ev.status.label,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = color,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(ev.eventType.label, style = MaterialTheme.typography.labelMedium, color = PremiumTextPrimary)
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            "Pgto: ${ev.paymentDate.ifBlank { "—" }} • Data-Com: ${ev.exDate.ifBlank { "—" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PremiumTextSecondary
                                        )
                                        Text(
                                            "Unit.: ${formatBrl(ev.unitAmount)} × ${"%.2f".format(ev.quantityReference)} • ${ev.source}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PremiumTextSecondary
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(formatBrl(ev.totalAmount), style = MonoValueStyle, color = color)
                                        Row {
                                            IconButton(
                                                onClick = { editingDividendDraft = ev.toEditDraft() },
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Editar provento", tint = ConfirmedBlue, modifier = Modifier.size(16.dp))
                                            }
                                            if (!ev.isAutoGeneratedProjection && ev.id > 0L) {
                                                IconButton(
                                                    onClick = { deletingDividendEvent = ev },
                                                    modifier = Modifier.size(38.dp)
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir provento", tint = CancelledRed, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AssetDetailTab.DIVIDEND_CALENDAR -> {
                    item {
                        AssetDetailPremiumCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = ConfirmedBlue)
                                Text("Calendário de Proventos • $cleanTicker", style = MaterialTheme.typography.titleMedium, color = PremiumTextPrimary)
                            }
                            Spacer(Modifier.height(8.dp))
                            DetailMetricRow(
                                "Classificação de Grupo",
                                if (position?.isCyclical == true && position.dataComGroup != null) {
                                    "Dividendo Mensal • Grupo ${position.dataComGroup?.code}"
                                } else {
                                    "Sem classificação (não identificado como mensal)"
                                },
                                ConfirmedBlue
                            )
                            val dataComDay = liveQuote?.dataComDay ?: position?.dataComDay ?: catalogAsset?.dataComDay
                            val paymentDay = liveQuote?.paymentDay ?: catalogAsset?.paymentDay
                            DetailMetricRow("Data-Com habitual", dataComDay?.let { "Dia $it do mês" } ?: "Sob anúncio", PremiumTextPrimary)
                            DetailMetricRow("Dia de Pagamento habitual", paymentDay?.let { "Dia $it do mês" } ?: "Sob anúncio", PremiumTextPrimary)
                        }
                    }
                    val calendarItems = allAssetDividends.sortedByDescending { ev ->
                        DividendHistoryEngine.parseDateToYmd(ev.paymentDate)?.let { (y, m, d) ->
                            DividendHistoryEngine.ymdToInt(y, m, d)
                        } ?: 0
                    }
                    if (calendarItems.isEmpty()) {
                        item {
                            AssetDetailPremiumCard {
                                Text("Sem datas de pagamento agendadas ou registradas para $cleanTicker.", style = MaterialTheme.typography.bodyMedium, color = PremiumTextSecondary)
                            }
                        }
                    } else {
                        items(calendarItems, key = { "cal_${it.id}_${it.paymentDate}_${it.status.name}_${it.isAutoGeneratedProjection}" }) { ev ->
                            val color = when (ev.status) {
                                DividendStatus.RECEIVED -> ReceivedGreen
                                DividendStatus.CONFIRMED -> ConfirmedBlue
                                DividendStatus.PROJECTED -> ProjectedAmber
                                DividendStatus.CANCELLED -> CancelledRed
                            }
                            AssetDetailPremiumCard(borderColor = color.copy(alpha = 0.4f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Pagamento: ${ev.paymentDate.ifBlank { "—" }}", style = MaterialTheme.typography.titleSmall, color = PremiumTextPrimary)
                                        Text(
                                            "Data-Com: ${ev.exDate.ifBlank { "—" }} • ${ev.status.label}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = color
                                        )
                                    }
                                    Text(formatBrl(ev.totalAmount), style = MonoValueStyle, color = color)
                                }
                            }
                        }
                    }
                }

                AssetDetailTab.POSITION_MOVEMENTS -> {
                    val totalBought = averagePriceSteps.filter { it.operationType == OperationType.COMPRA }.sumOf { it.quantityDelta }
                    val totalSold = averagePriceSteps.filter { it.operationType == OperationType.VENDA }.sumOf { -it.quantityDelta }
                    item {
                        AssetDetailPremiumCard {
                            Text("Histórico de Movimentações da Posição", style = MaterialTheme.typography.titleMedium, color = PremiumTextPrimary)
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MiniStatusKpi("Comprado", "%.2f".format(totalBought), ReceivedGreen, Modifier.weight(1f))
                                MiniStatusKpi("Vendido", "%.2f".format(totalSold), CancelledRed, Modifier.weight(1f))
                                MiniStatusKpi("Posição Atual", "%.2f".format(position?.quantity ?: 0.0), ConfirmedBlue, Modifier.weight(1f))
                            }
                            if (averagePriceSteps.size >= 2) {
                                Spacer(Modifier.height(10.dp))
                                AssetLineChartCanvas(
                                    points = averagePriceSteps.map { it.accumulatedQuantity },
                                    lineColor = ConfirmedBlue,
                                    modifier = Modifier.fillMaxWidth().height(120.dp)
                                )
                            }
                        }
                    }
                    if (averagePriceSteps.isEmpty()) {
                        item {
                            AssetDetailPremiumCard {
                                Text(
                                    "Posição atual de ${"%.2f".format(position?.quantity ?: 0.0)} cotas/ações sem ordens individuais de compra/venda registradas.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PremiumTextSecondary
                                )
                            }
                        }
                    } else {
                        items(averagePriceSteps.reversed(), key = { "mov_${it.operationId}" }) { step ->
                            val isBuy = step.operationType == OperationType.COMPRA
                            val accent = if (isBuy) ReceivedGreen else CancelledRed
                            AssetDetailPremiumCard(borderColor = accent.copy(alpha = 0.4f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${step.dateIso} • ${step.operationType.label}", style = MaterialTheme.typography.titleSmall, color = accent)
                                        Text("Execução: ${formatBrl(step.executionPrice)}", style = MaterialTheme.typography.labelSmall, color = PremiumTextSecondary)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        val deltaText = (if (step.quantityDelta >= 0) "+" else "") + "%.2f".format(step.quantityDelta)
                                        Text(deltaText, style = MonoValueStyle, color = accent)
                                        Text("Saldo: ${"%.2f".format(step.accumulatedQuantity)}", style = MaterialTheme.typography.labelSmall, color = PremiumTextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }

                AssetDetailTab.AVERAGE_PRICE -> {
                    item {
                        AssetDetailPremiumCard(borderColor = ConfirmedBlue.copy(alpha = 0.55f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = ConfirmedBlue)
                                Text("Composição Detalhada do Preço Médio", style = MaterialTheme.typography.titleMedium, color = PremiumTextPrimary)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "O Preço Médio reflete o custo ponderado das suas compras (Custo Acumulado ÷ Quantidade Acumulada) e nunca deve ser confundido com a Cotação Atual de mercado.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PremiumTextSecondary
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MiniStatusKpi("Preço Médio", formatBrl(averagePrice), ConfirmedBlue, Modifier.weight(1f))
                                MiniStatusKpi("Cotação Atual", currentPrice?.let { formatBrl(it) } ?: "—", returnColor, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            DetailMetricRow("Custo Total Investido", formatBrl(position?.totalCostBrl ?: 0.0), PremiumTextPrimary)
                            DetailMetricRow("Quantidade na Carteira", "%.2f".format(position?.quantity ?: 0.0), PremiumTextPrimary)
                        }
                    }
                    if (averagePriceSteps.isEmpty()) {
                        item {
                            AssetDetailPremiumCard {
                                Text(
                                    "Preço médio definido diretamente na posição cadastrada/importada (${formatBrl(averagePrice)}). Ao lançar novas compras, cada etapa do cálculo aparecerá aqui.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PremiumTextSecondary
                                )
                            }
                        }
                    } else {
                        items(averagePriceSteps, key = { "pm_${it.operationId}" }) { step ->
                            AveragePriceStepCard(step = step)
                        }
                    }
                }

                AssetDetailTab.EQUITY_EVOLUTION -> {
                    val invested = position?.totalCostBrl ?: 0.0
                    val currentVal = position?.totalCurrentBrl ?: 0.0
                    val capitalGain = currentVal - invested
                    val totalReturnWithDivs = capitalGain + receivedDivTotal
                    val weightPct = if (activeHhi.totalEquityBrl > 0.0) (currentVal / activeHhi.totalEquityBrl) * 100.0 else 0.0
                    item {
                        AssetDetailPremiumCard {
                            Text("Evolução Patrimonial • $cleanTicker", style = MaterialTheme.typography.titleMedium, color = PremiumTextPrimary)
                            Spacer(Modifier.height(8.dp))
                            if (equityPoints.isNotEmpty()) {
                                AssetEquityBarsCanvas(
                                    points = equityPoints,
                                    modifier = Modifier.fillMaxWidth().height(155.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            DetailMetricRow("Capital Investido (Custo)", formatBrl(invested), PremiumTextSecondary)
                            DetailMetricRow("Valor Atual de Mercado", formatBrl(currentVal), ConfirmedBlue)
                            DetailMetricRow("Ganho / Perda de Capital", formatBrl(capitalGain), if (capitalGain >= 0.0) ReceivedGreen else CancelledRed)
                            DetailMetricRow("Proventos Recebidos (Efetivos)", formatBrl(receivedDivTotal), ReceivedGreen)
                            HorizontalDivider(color = DarkPremiumBorder, modifier = Modifier.padding(vertical = 6.dp))
                            DetailMetricRow(
                                "Retorno Total (Capital + Recebidos)",
                                formatBrl(totalReturnWithDivs),
                                if (totalReturnWithDivs >= 0.0) ReceivedGreen else CancelledRed
                            )
                            DetailMetricRow("Peso na Carteira", "${String.format(Locale("pt", "BR"), "%.2f", weightPct)}%", PremiumTextPrimary)
                        }
                    }
                }
            }
        }
    }

    if (showEditPositionSheet && position != null) {
        AssetPositionDetailAndEditSheet(
            position = position,
            catalogAsset = catalogAsset,
            portfolioTotalInvestedBrl = activeHhi.totalInvestedBrl,
            onDismiss = { showEditPositionSheet = false },
            onSave = { id, tkr, name, cls, qty, pm, cur, sec, dc, src, isCyclical ->
                onUpdatePosition(id, tkr, name, cls, qty, pm, cur, sec, dc, src, isCyclical)
                showEditPositionSheet = false
            },
            onDelete = { id ->
                onDeletePosition(id)
                showEditPositionSheet = false
            }
        )
    }

    if (showNewOperationDialog) {
        RecordOperationDialog(
            onDismiss = { showNewOperationDialog = false },
            onConfirm = { tkr, opType, qty, price, notes ->
                onRecordOperation(selectedPortfolioType, tkr.ifBlank { cleanTicker }, opType, qty, price, notes)
                showNewOperationDialog = false
            }
        )
    }

    editingOperation?.let { op ->
        EditOperationDialog(
            operation = op,
            onDismiss = { editingOperation = null },
            onConfirm = { opId, tkr, opType, qty, price, dateIso, notes ->
                onUpdateOperation(opId, tkr, opType, qty, price, dateIso, notes)
                editingOperation = null
            }
        )
    }

    deletingOperation?.let { op ->
        DeleteConfirmImpactDialog(
            title = "Excluir Operação?",
            entitySummary = "${op.ticker} • ${op.operationType} • ${op.dateIso} • ${formatBrl(op.totalValue)}",
            impactDescription = "A quantidade acumulada e o preço médio serão recalculados automaticamente.",
            onDismiss = { deletingOperation = null },
            onConfirm = {
                onDeleteOperation(op.id)
                deletingOperation = null
            }
        )
    }

    editingDividendDraft?.let { draft ->
        DividendEventFormDialog(
            draft = draft,
            realPositions = state.realPositions,
            onDismiss = { editingDividendDraft = null },
            onSave = { id, tkr, evType, st, exD, payD, unit, qtyRef, total, src, notes, recurring ->
                onSaveDividendEvent(id, tkr, evType, st, exD, payD, unit, qtyRef, total, src, notes, recurring)
                editingDividendDraft = null
            }
        )
    }

    deletingDividendEvent?.let { ev ->
        DeleteConfirmImpactDialog(
            title = "Excluir Provento?",
            entitySummary = "${ev.assetTicker} • ${ev.status.label} • ${ev.paymentDate} • ${formatBrl(ev.totalAmount)}",
            impactDescription = "O provento será removido do histórico de $cleanTicker e registrado na auditoria.",
            onDismiss = { deletingDividendEvent = null },
            onConfirm = {
                onSoftDeleteDividendEvent(ev.id, ev.assetTicker)
                deletingDividendEvent = null
            }
        )
    }
}

@Composable
private fun AssetDetailHeaderCard(
    ticker: String,
    name: String,
    classLabel: String,
    isCyclical: Boolean,
    groupCode: String?,
    currentPrice: Double?,
    averagePrice: Double,
    quantity: Double,
    currentValue: Double,
    returnPct: Double,
    hasMarketPrice: Boolean,
    returnColor: Color,
    isLoadingQuote: Boolean,
    canEditPosition: Boolean,
    onRefreshQuote: () -> Unit,
    onEditPosition: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(DarkPremiumCard, DarkPremiumBg))
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = ticker,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = PremiumTextPrimary
                    )
                    if (isCyclical && !groupCode.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = TechBluePrimary.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, ConfirmedBlue)
                        ) {
                            Text(
                                text = groupCode,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ConfirmedBlue,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text("$name • $classLabel", style = MaterialTheme.typography.bodySmall, color = PremiumTextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Rentabilidade", style = MaterialTheme.typography.labelSmall, color = PremiumTextSecondary)
                Text(
                    text = if (hasMarketPrice) formatPct(returnPct) else "Sem cotação",
                    style = MonoValueStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = returnColor
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStatusKpi("Cotação Atual", currentPrice?.let { formatBrl(it) } ?: "—", ConfirmedBlue, Modifier.weight(1f))
            MiniStatusKpi("Preço Médio", formatBrl(averagePrice), PremiumTextPrimary, Modifier.weight(1f))
            MiniStatusKpi("Valor Atual", formatBrl(currentValue), returnColor, Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRefreshQuote,
                enabled = !isLoadingQuote,
                colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary, contentColor = Color.White),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("asset_detail_refresh_quote_button")
            ) {
                if (isLoadingQuote) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("Cotação Ao Vivo", style = MaterialTheme.typography.labelMedium)
            }
            if (canEditPosition) {
                OutlinedButton(
                    onClick = onEditPosition,
                    border = BorderStroke(1.dp, ConfirmedBlue.copy(alpha = 0.7f)),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("asset_detail_edit_position_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = ConfirmedBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Editar Cadastro (${"%.0f".format(quantity)})", style = MaterialTheme.typography.labelMedium, color = ConfirmedBlue)
                }
            }
        }
    }
}

@Composable
private fun AveragePriceStepCard(step: AveragePriceStep) {
    val isBuy = step.operationType == OperationType.COMPRA
    val accent = if (isBuy) ReceivedGreen else CancelledRed
    AssetDetailPremiumCard(borderColor = accent.copy(alpha = 0.4f)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${step.dateIso} • ${step.operationType.label}", style = MaterialTheme.typography.titleSmall, color = accent)
            Text("PM Resultante: ${formatBrl(step.resultingAveragePrice)}", style = MonoValueStyle, color = ConfirmedBlue)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Execução: ${"%.2f".format(kotlin.math.abs(step.quantityDelta))} × ${formatBrl(step.executionPrice)} = ${formatBrl(step.financialValue)}",
            style = MaterialTheme.typography.bodySmall,
            color = PremiumTextPrimary
        )
        Text(
            text = "Acumulado: ${"%.2f".format(step.accumulatedQuantity)} unid. • Custo: ${formatBrl(step.accumulatedCostBrl)}",
            style = MaterialTheme.typography.labelSmall,
            color = PremiumTextSecondary
        )
    }
}

@Composable
private fun AssetDetailPremiumCard(
    borderColor: Color = DarkPremiumBorder,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkPremiumCard)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun MiniStatusKpi(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DarkPremiumCardSecondary)
            .border(1.dp, DarkPremiumBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = PremiumTextSecondary, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MonoValueStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = accent, maxLines = 1)
    }
}

@Composable
private fun DetailMetricRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = PremiumTextSecondary)
        Text(value, style = MonoValueStyle.copy(fontSize = 12.sp), color = valueColor)
    }
}

@Composable
private fun AssetLineChartCanvas(
    points: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkPremiumCardSecondary)
            .padding(10.dp)
    ) {
        if (points.size < 2) return@Canvas
        val minVal = points.minOrNull() ?: 0.0
        val maxVal = points.maxOrNull() ?: 1.0
        val range = (maxVal - minVal).takeIf { it > 0.0001 } ?: 1.0
        val stepX = size.width / (points.size - 1).toFloat()

        val linePath = Path()
        val fillPath = Path()
        var lastOffset = Offset.Zero

        points.forEachIndexed { idx, value ->
            val x = idx * stepX
            val y = size.height * (1f - ((value - minVal) / range).toFloat().coerceIn(0f, 1f))
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            lastOffset = Offset(x, y)
        }
        fillPath.lineTo(lastOffset.x, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), Color.Transparent))
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(color = Color.White, radius = 4f, center = lastOffset)
    }
}

@Composable
private fun AssetEquityBarsCanvas(
    points: List<AssetEquityPoint>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkPremiumCardSecondary)
            .padding(12.dp)
    ) {
        if (points.isEmpty()) return@Canvas
        val maxVal = points.maxOf { maxOf(it.investedCostBrl, it.marketValueBrl) }.coerceAtLeast(1.0)
        val groupW = size.width / points.size.toFloat()
        val barW = (groupW * 0.32f).coerceAtMost(26f)

        points.forEachIndexed { idx, pt ->
            val cx = idx * groupW + groupW / 2f
            val invH = ((pt.investedCostBrl / maxVal).toFloat().coerceIn(0f, 1f)) * (size.height - 8f)
            val mktH = ((pt.marketValueBrl / maxVal).toFloat().coerceIn(0f, 1f)) * (size.height - 8f)

            drawRoundRect(
                color = PremiumTextSecondary.copy(alpha = 0.55f),
                topLeft = Offset(cx - barW - 2f, size.height - invH),
                size = Size(barW, invH),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = if (pt.marketValueBrl >= pt.investedCostBrl) ReceivedGreen else CancelledRed,
                topLeft = Offset(cx + 2f, size.height - mktH),
                size = Size(barW, mktH),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }
    }
}

package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DividendDisplayEvent
import com.example.domain.model.DividendEventType
import com.example.domain.model.DividendFilterState
import com.example.domain.model.DividendHistoryEngine
import com.example.domain.model.DividendHistorySnapshot
import com.example.domain.model.DividendPeriodFilter
import com.example.domain.model.DividendSortColumn
import com.example.domain.model.DividendStatus
import com.example.ui.components.ThreeSeriesDividendChart
import com.example.ui.components.formatBrl
import com.example.ui.theme.CancelledRed
import com.example.ui.theme.ConfirmedBlue
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
import com.example.ui.theme.VioletAccent
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.dividendHistoryTabItems(
    snapshot: DividendHistorySnapshot,
    filter: DividendFilterState,
    isSyncingDividends: Boolean,
    syncStatusMessage: String,
    auditCount: Int,
    onFilterChange: (DividendFilterState) -> Unit,
    onRefreshNow: () -> Unit,
    onOpenNewEvent: () -> Unit,
    onOpenAuditDialog: () -> Unit,
    onOpenRenameTicker: (String) -> Unit,
    onEditEvent: (DividendDisplayEvent) -> Unit,
    onTransitionStatus: (DividendDisplayEvent, DividendStatus) -> Unit,
    onRequestDeleteEvent: (DividendDisplayEvent) -> Unit,
    onRestoreEvent: (DividendDisplayEvent) -> Unit,
    onInspectDetails: (DividendDisplayEvent) -> Unit,
    onExportCsvResult: (String) -> Unit
) {
    // 1. Cabeçalho Dark Premium + Botão Atualizar + Ações
    item(key = "history_header_card") {
        val context = LocalContext.current
        val csvExporter = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv")
        ) { uri ->
            if (uri != null) {
                runCatching {
                    val csv = DividendHistoryEngine.exportFilteredCsv(snapshot.filteredEvents)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write("\uFEFF".toByteArray(Charsets.UTF_8))
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess {
                    onExportCsvResult("CSV exportado com ${snapshot.filteredEvents.size} registro(s).")
                }.onFailure { err ->
                    onExportCsvResult("Falha ao exportar CSV: ${err.localizedMessage ?: "erro"}")
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkPremiumCard,
            border = BorderStroke(1.dp, DarkPremiumBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dividend_history_header")
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Histórico de Proventos",
                            style = MaterialTheme.typography.titleLarge,
                            color = PremiumTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Acompanhe proventos recebidos, confirmados e projetados sem misturar categorias.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PremiumTextSecondary
                        )
                    }
                    Button(
                        onClick = onRefreshNow,
                        enabled = !isSyncingDividends,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TechBluePrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("dividend_history_refresh_button")
                    ) {
                        if (isSyncingDividends) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Atualizar")
                    }
                }

                if (syncStatusMessage.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkPremiumCardSecondary,
                        border = BorderStroke(1.dp, DarkPremiumBorder)
                    ) {
                        Text(
                            text = syncStatusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = ConfirmedBlue,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenNewEvent,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ReceivedGreen,
                            contentColor = EmeraldOnPrimary
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("dividend_history_add_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Novo Provento")
                    }
                    OutlinedButton(
                        onClick = { csvExporter.launch("invest_ai_historico_proventos.csv") },
                        border = BorderStroke(1.dp, DarkPremiumBorder),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("dividend_history_export_csv_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = ConfirmedBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Exportar CSV", color = PremiumTextPrimary)
                    }
                    OutlinedButton(
                        onClick = onOpenAuditDialog,
                        border = BorderStroke(1.dp, DarkPremiumBorder),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("dividend_history_audit_button")
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = ProjectedAmber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Auditoria ($auditCount)", color = PremiumTextPrimary)
                    }
                }
            }
        }
    }

    // 2. Filtros combináveis (Período, Ativo/Busca, Status, Tipo, Cancelados, Limpar)
    item(key = "history_filters_card") {
        DividendHistoryFiltersCard(
            filter = filter,
            availableTickers = snapshot.allAvailableTickers,
            onFilterChange = onFilterChange
        )
    }

    // 3. Banner para edição rápida de ticker de ativos que possuem APENAS dividendos cadastrados
    if (snapshot.dividendOnlyTickers.isNotEmpty()) {
        item(key = "dividend_only_tickers_banner") {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DarkPremiumCardSecondary,
                border = BorderStroke(1.dp, ProjectedAmber.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dividend_only_tickers_banner")
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ativos cadastrados apenas por dividendos — toque para corrigir o ticker",
                        style = MaterialTheme.typography.labelLarge,
                        color = ProjectedAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        snapshot.dividendOnlyTickers.sorted().forEach { divOnlyTicker ->
                            OutlinedButton(
                                onClick = { onOpenRenameTicker(divOnlyTicker) },
                                border = BorderStroke(1.dp, ProjectedAmber.copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("edit_dividend_only_ticker_$divOnlyTicker")
                            ) {
                                Text(divOnlyTicker, color = PremiumTextPrimary, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar ticker $divOnlyTicker",
                                    tint = ProjectedAmber,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. 6 Indicadores KPI independentes (jamais somam Recebido + Confirmado + Projetado)
    item(key = "history_kpi_grid") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumKpiCard(
                    title = "Total Recebido",
                    value = formatBrl(snapshot.totalReceivedBrl),
                    subtitle = "${snapshot.receivedPaymentsCount} pago(s) efetivamente",
                    accent = ReceivedGreen,
                    icon = Icons.Default.Paid,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_total_received")
                )
                PremiumKpiCard(
                    title = "Total Confirmado",
                    value = formatBrl(snapshot.totalConfirmedBrl),
                    subtitle = "${snapshot.confirmedPaymentsCount} aguardando pgto",
                    accent = ConfirmedBlue,
                    icon = Icons.Default.EventAvailable,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_total_confirmed")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumKpiCard(
                    title = "Total Projetado",
                    value = formatBrl(snapshot.totalProjectedBrl),
                    subtitle = "${snapshot.projectedPaymentsCount} estimativa(s) futura(s)",
                    accent = ProjectedAmber,
                    icon = Icons.Default.Schedule,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_total_projected")
                )
                PremiumKpiCard(
                    title = "Média Mensal Rec.",
                    value = formatBrl(snapshot.monthlyAverageReceivedBrl),
                    subtitle = snapshot.largestReceivedPayment?.let {
                        "Maior: ${it.assetTicker} (${formatBrl(it.totalAmount)})"
                    } ?: "Baseado nos recebidos",
                    accent = VioletAccent,
                    icon = Icons.Default.TrendingUp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_monthly_average")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumKpiCard(
                    title = "Pagamentos Recebidos",
                    value = "${snapshot.receivedPaymentsCount}",
                    subtitle = "No período e filtros ativos",
                    accent = ReceivedGreen,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_received_count")
                )
                val nextPay = snapshot.nextExpectedPayment
                PremiumKpiCard(
                    title = "Próximo Pagamento",
                    value = nextPay?.let { "${it.assetTicker} • ${formatBrl(it.totalAmount)}" } ?: "—",
                    subtitle = nextPay?.let { "${it.paymentDate} (${it.status.label})" } ?: "Sem evento futuro",
                    accent = if (nextPay?.status == DividendStatus.CONFIRMED) ConfirmedBlue else ProjectedAmber,
                    icon = Icons.Default.CalendarMonth,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_next_payment")
                )
            }
        }
    }

    // 5. Gráfico de Evolução com 3 Séries Independentes
    item(key = "history_three_series_chart") {
        ThreeSeriesDividendChart(
            points = snapshot.monthlySeries,
            monthlyAverageReceivedBrl = snapshot.monthlyAverageReceivedBrl
        )
    }

    // 6. Barra de ordenação e cabeçalho do Detalhamento
    item(key = "history_table_header") {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = DarkPremiumCard,
            border = BorderStroke(1.dp, DarkPremiumBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detalhamento dos Proventos (${snapshot.filteredEvents.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = PremiumTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = { onFilterChange(filter.copy(sortAscending = !filter.sortAscending)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Inverter ordem", tint = ConfirmedBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (filter.sortAscending) "Crescente" else "Decrescente", color = ConfirmedBlue)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DividendSortColumn.entries.forEach { col ->
                        FilterChip(
                            selected = filter.sortColumn == col,
                            onClick = {
                                val nextAsc = if (filter.sortColumn == col) !filter.sortAscending else false
                                onFilterChange(filter.copy(sortColumn = col, sortAscending = nextAsc))
                            },
                            label = { Text(col.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TechBluePrimary.copy(alpha = 0.25f),
                                selectedLabelColor = ConfirmedBlue
                            ),
                            modifier = Modifier.testTag("dividend_sort_${col.name}")
                        )
                    }
                }
            }
        }
    }

    // 7. Lista / Tabela responsiva de eventos de proventos
    if (snapshot.filteredEvents.isEmpty()) {
        item(key = "history_empty_state") {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DarkPremiumCard,
                border = BorderStroke(1.dp, DarkPremiumBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Nenhum provento encontrado com os filtros atuais.",
                        style = MaterialTheme.typography.titleSmall,
                        color = PremiumTextPrimary
                    )
                    Text(
                        text = "Cadastre um provento manual, ajuste os filtros ou marque ativos recorrentes (cíclicos) na Carteira para gerar projeções futuras.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PremiumTextSecondary
                    )
                }
            }
        }
    } else {
        items(
            items = snapshot.filteredEvents,
            key = { ev -> "${ev.id}_${ev.assetTicker}_${ev.paymentDate}_${ev.status.name}" }
        ) { event ->
            DividendHistoryEventCard(
                event = event,
                onEdit = { onEditEvent(event) },
                onTransitionStatus = { target -> onTransitionStatus(event, target) },
                onRequestDelete = { onRequestDeleteEvent(event) },
                onRestore = { onRestoreEvent(event) },
                onInspectDetails = { onInspectDetails(event) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DividendHistoryFiltersCard(
    filter: DividendFilterState,
    availableTickers: List<String>,
    onFilterChange: (DividendFilterState) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkPremiumCard,
        border = BorderStroke(1.dp, DarkPremiumBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dividend_history_filters_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filtros Combináveis",
                    style = MaterialTheme.typography.titleSmall,
                    color = PremiumTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (!filter.isDefault) {
                    TextButton(
                        onClick = { onFilterChange(DividendFilterState()) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("dividend_clear_filters_button")
                    ) {
                        Icon(Icons.Default.FilterListOff, contentDescription = null, tint = CancelledRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Limpar filtros", color = CancelledRed)
                    }
                }
            }

            OutlinedTextField(
                value = filter.searchQuery,
                onValueChange = { onFilterChange(filter.copy(searchQuery = it)) },
                label = { Text("Buscar por ticker, ativo ou observação") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ConfirmedBlue) },
                trailingIcon = {
                    if (filter.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onFilterChange(filter.copy(searchQuery = "")) }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dividend_history_search_input")
            )

            // Período
            Text("Período", style = MaterialTheme.typography.labelSmall, color = PremiumTextSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DividendPeriodFilter.entries.forEach { p ->
                    FilterChip(
                        selected = filter.period == p,
                        onClick = { onFilterChange(filter.copy(period = p)) },
                        label = { Text(p.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TechBluePrimary.copy(alpha = 0.28f),
                            selectedLabelColor = ConfirmedBlue
                        ),
                        modifier = Modifier.testTag("dividend_period_${p.name}")
                    )
                }
            }

            if (filter.period == DividendPeriodFilter.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = filter.customStartDate,
                        onValueChange = { onFilterChange(filter.copy(customStartDate = it)) },
                        label = { Text("Início (dd/MM/aaaa)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = filter.customEndDate,
                        onValueChange = { onFilterChange(filter.copy(customEndDate = it)) },
                        label = { Text("Fim (dd/MM/aaaa)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Status
            Text("Status", style = MaterialTheme.typography.labelSmall, color = PremiumTextSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filter.selectedStatus == null,
                    onClick = { onFilterChange(filter.copy(selectedStatus = null)) },
                    label = { Text("Todos ativos", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("dividend_status_filter_ALL")
                )
                listOf(DividendStatus.RECEIVED, DividendStatus.CONFIRMED, DividendStatus.PROJECTED).forEach { st ->
                    val col = statusColor(st)
                    FilterChip(
                        selected = filter.selectedStatus == st,
                        onClick = {
                            onFilterChange(filter.copy(selectedStatus = if (filter.selectedStatus == st) null else st))
                        },
                        label = { Text(st.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = col.copy(alpha = 0.22f),
                            selectedLabelColor = col
                        ),
                        modifier = Modifier.testTag("dividend_status_filter_${st.name}")
                    )
                }
            }

            // Tipo de provento
            Text("Tipo de Provento", style = MaterialTheme.typography.labelSmall, color = PremiumTextSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filter.selectedType == null,
                    onClick = { onFilterChange(filter.copy(selectedType = null)) },
                    label = { Text("Todos tipos", style = MaterialTheme.typography.labelSmall) }
                )
                DividendEventType.entries.forEach { tp ->
                    FilterChip(
                        selected = filter.selectedType == tp,
                        onClick = {
                            onFilterChange(filter.copy(selectedType = if (filter.selectedType == tp) null else tp))
                        },
                        label = { Text(tp.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Ativo (Ticker)
            if (availableTickers.isNotEmpty()) {
                Text("Ativo", style = MaterialTheme.typography.labelSmall, color = PremiumTextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filter.selectedTicker == null,
                        onClick = { onFilterChange(filter.copy(selectedTicker = null)) },
                        label = { Text("Todos ativos", style = MaterialTheme.typography.labelSmall) }
                    )
                    availableTickers.forEach { tkr ->
                        FilterChip(
                            selected = filter.selectedTicker.equals(tkr, ignoreCase = true),
                            onClick = {
                                val next = if (filter.selectedTicker.equals(tkr, ignoreCase = true)) null else tkr
                                onFilterChange(filter.copy(selectedTicker = next))
                            },
                            label = { Text(tkr, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mostrar proventos cancelados",
                    style = MaterialTheme.typography.bodySmall,
                    color = PremiumTextSecondary
                )
                Switch(
                    checked = filter.showCancelled,
                    onCheckedChange = { onFilterChange(filter.copy(showCancelled = it)) },
                    modifier = Modifier.testTag("dividend_show_cancelled_switch")
                )
            }
        }
    }
}

@Composable
private fun PremiumKpiCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = DarkPremiumCard,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MonoValueStyle.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = FontWeight.Bold
                ),
                color = PremiumTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DividendHistoryEventCard(
    event: DividendDisplayEvent,
    onEdit: () -> Unit,
    onTransitionStatus: (DividendStatus) -> Unit,
    onRequestDelete: () -> Unit,
    onRestore: () -> Unit,
    onInspectDetails: () -> Unit
) {
    val accent = statusColor(event.status)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkPremiumCard,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInspectDetails() }
            .testTag("dividend_event_card_${event.assetTicker}_${event.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = event.assetTicker,
                            style = MaterialTheme.typography.titleMedium,
                            color = PremiumTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = event.status.badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        if (!event.isAssetInPortfolio) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = ProjectedAmber.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "Só dividendo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ProjectedAmber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${event.eventType.label} • ${event.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatBrl(event.totalAmount),
                        style = MonoValueStyle.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                    Text(
                        text = "Pgto ${event.paymentDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumTextPrimary
                    )
                }
            }

            // Linha de dados detalhados (Data-Com, Valor/Cota, Quantidade)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Data-Com: ${event.exDate.ifBlank { "—" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumTextSecondary
                )
                Text(
                    text = "${String.format(Locale("pt", "BR"), "%.2f", event.quantityReference)} unid. × ${formatBrl(event.unitAmount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumTextSecondary
                )
            }

            if (event.syncDivergenceNote.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ProjectedAmber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ProjectedAmber.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = ProjectedAmber, modifier = Modifier.size(16.dp))
                        Text(event.syncDivergenceNote, style = MaterialTheme.typography.labelSmall, color = ProjectedAmber)
                    }
                }
            }

            // Ações de transição de status + Editar + Excluir + Detalhes
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (event.status) {
                    DividendStatus.PROJECTED -> {
                        OutlinedButton(
                            onClick = { onTransitionStatus(DividendStatus.CONFIRMED) },
                            border = BorderStroke(1.dp, ConfirmedBlue.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("action_confirm_dividend_${event.id}")
                        ) {
                            Text("Confirmar", color = ConfirmedBlue, style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { onTransitionStatus(DividendStatus.RECEIVED) },
                            border = BorderStroke(1.dp, ReceivedGreen.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("action_receive_dividend_${event.id}")
                        ) {
                            Text("Marcar Recebido", color = ReceivedGreen, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    DividendStatus.CONFIRMED -> {
                        OutlinedButton(
                            onClick = { onTransitionStatus(DividendStatus.RECEIVED) },
                            border = BorderStroke(1.dp, ReceivedGreen.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("action_receive_dividend_${event.id}")
                        ) {
                            Text("Marcar Recebido", color = ReceivedGreen, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    DividendStatus.CANCELLED -> {
                        OutlinedButton(
                            onClick = onRestore,
                            border = BorderStroke(1.dp, ReceivedGreen.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("action_restore_dividend_${event.id}")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = ReceivedGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restaurar", color = ReceivedGreen, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    DividendStatus.RECEIVED -> Unit
                }

                OutlinedButton(
                    onClick = onEdit,
                    border = BorderStroke(1.dp, DarkPremiumBorder),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("action_edit_dividend_${event.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar provento ${event.assetTicker}", tint = ConfirmedBlue, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar", color = PremiumTextPrimary, style = MaterialTheme.typography.labelMedium)
                }

                if (!event.isAutoGeneratedProjection) {
                    IconButton(
                        onClick = onRequestDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("action_delete_dividend_${event.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Excluir provento ${event.assetTicker}",
                            tint = CancelledRed
                        )
                    }
                } else {
                    TextButton(
                        onClick = { onTransitionStatus(DividendStatus.CANCELLED) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Ignorar", color = PremiumTextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }

                IconButton(
                    onClick = onInspectDetails,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Ver detalhes de ${event.assetTicker}",
                        tint = PremiumTextSecondary
                    )
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetDividendRow
import com.example.domain.model.DividendMonitorSummary
import com.example.ui.components.FintechCard
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

private enum class DividendMonitorViewTab(val label: String) {
    MENSAL("Mensal"),
    ANUAL("Anual"),
    POR_ATIVO("Por Ativo"),
    JAN_DEZ("Jan–Dez")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DividendMonitorDashboardCard(
    summary: DividendMonitorSummary,
    onOpenCalendar: () -> Unit,
    onRecordDividend: (ticker: String, totalReceivedBrl: Double, notes: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(DividendMonitorViewTab.MENSAL) }
    var showAllAssets by remember { mutableStateOf(false) }
    var showRecordDialog by remember { mutableStateOf(false) }

    FintechCard(
        modifier = modifier.testTag("dashboard_dividend_monitor_card"),
        borderColor = EmeraldPrimary.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Monitor de Dividendos", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Recebido (op. registradas) vs Projetado na Carteira Real",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.Payments,
                contentDescription = "Monitor de Dividendos",
                tint = EmeraldPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        // Grid 2x2: Recebido Mensal / Projetado Mensal / Recebido Anual / Projetado Anual
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DividendStatTile(
                label = "Recebido (Mês)",
                value = formatBrl(summary.receivedMonthlyBrl),
                accent = EmeraldPrimary,
                modifier = Modifier.weight(1f).testTag("dividend_received_monthly")
            )
            DividendStatTile(
                label = "Projetado (Mês)",
                value = formatBrl(summary.projectedMonthlyBrl),
                accent = ElectricCyan,
                modifier = Modifier.weight(1f).testTag("dividend_projected_monthly")
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DividendStatTile(
                label = "Recebido (12m)",
                value = formatBrl(summary.receivedAnnualBrl),
                accent = EmeraldPrimary,
                modifier = Modifier.weight(1f).testTag("dividend_received_annual")
            )
            DividendStatTile(
                label = "Projetado (Anual)",
                value = formatBrl(summary.projectedAnnualBrl),
                accent = AmberGold,
                modifier = Modifier.weight(1f).testTag("dividend_projected_annual")
            )
        }

        if (summary.totalInvestedBrl > 0.0) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "DY projetado atual: ${formatPct(summary.projectedAnnualDyPercent, false)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan
                )
                Text(
                    "YoC (Custo): ${formatPct(summary.projectedYieldOnCostPercent, false)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldPrimary
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DividendMonitorViewTab.entries.forEach { tab ->
                FilterChip(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    label = {
                        val title = if (tab == DividendMonitorViewTab.POR_ATIVO) {
                            "Por Ativo (${summary.perAssetRows.size})"
                        } else {
                            tab.label
                        }
                        Text(title, style = MaterialTheme.typography.labelMedium)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldContainer,
                        selectedLabelColor = EmeraldPrimary
                    ),
                    modifier = Modifier.testTag("dividend_tab_${tab.name.lowercase()}")
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (activeTab == DividendMonitorViewTab.JAN_DEZ) {
            if (summary.monthlyJanToDec.isEmpty() || summary.perAssetRows.isEmpty()) {
                Text(
                    "Importe ativos ou registre proventos para visualizar a curva Jan–Dez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            } else {
                MonthlyDividendBarChart(
                    points = summary.monthlyJanToDec,
                    modifier = Modifier.testTag("dividend_jan_dec_chart")
                )
            }
        } else if (summary.perAssetRows.isEmpty()) {
            Text(
                "Importe ativos ou registre proventos na Carteira Real para acompanhar o recebido e o projetado mensal e anual por ativo.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        } else {
            val visibleRows = when {
                activeTab == DividendMonitorViewTab.POR_ATIVO && showAllAssets -> summary.perAssetRows
                activeTab == DividendMonitorViewTab.POR_ATIVO -> summary.perAssetRows.take(8)
                else -> summary.perAssetRows.take(4)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                visibleRows.forEach { row ->
                    AssetDividendItemRow(row = row, tab = activeTab)
                }
            }

            if (summary.perAssetRows.size > visibleRows.size) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        activeTab = DividendMonitorViewTab.POR_ATIVO
                        showAllAssets = true
                    },
                    modifier = Modifier.testTag("dividend_show_all_assets_button")
                ) {
                    Text("Ver todos os ${summary.perAssetRows.size} ativos")
                }
            } else if (showAllAssets && summary.perAssetRows.size > 8) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { showAllAssets = false }) {
                    Text("Mostrar menos")
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showRecordDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = EmeraldOnPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("dividend_monitor_record_button")
            ) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Registrar Provento")
            }
            OutlinedButton(
                onClick = onOpenCalendar,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("dividend_monitor_calendar_button")
            ) {
                Text("Datas-Com G1–G4")
            }
        }
    }

    if (showRecordDialog) {
        QuickRecordDividendDialog(
            suggestedTickers = summary.perAssetRows.map { it.ticker },
            onDismiss = { showRecordDialog = false },
            onConfirm = { ticker, totalBrl, notes ->
                onRecordDividend(ticker, totalBrl, notes)
                showRecordDialog = false
            }
        )
    }
}

@Composable
private fun DividendStatTile(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MonoValueStyle.copy(fontWeight = FontWeight.Bold),
                color = accent,
                softWrap = false,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AssetDividendItemRow(
    row: AssetDividendRow,
    tab: DividendMonitorViewTab
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = SubtleBorder.copy(alpha = 0.6f))
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.ticker, style = MaterialTheme.typography.titleSmall, color = EmeraldPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${"%.0f".format(row.quantity)} unid. • ${row.assetClass.shortLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                val dcInfo = row.dataComDay?.let { "Data-Com dia $it" } ?: "Sem Data-Com fixa"
                val dyInfo = if (row.hasEstimateData) "DY ${formatPct(row.estimatedDyPercent, false)} • YoC ${formatPct(row.yieldOnCostPercent, false)}" else "Registre proventos ou consulte cotação"
                Text("$dcInfo • $dyInfo", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                when (tab) {
                    DividendMonitorViewTab.MENSAL -> {
                        Text("Rec: ${formatBrl(row.receivedMonthBrl)}", style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary)
                        Text("Proj: ${formatBrl(row.projectedMonthBrl)}/mês", style = MonoValueStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize), color = ElectricCyan)
                    }
                    DividendMonitorViewTab.ANUAL, DividendMonitorViewTab.JAN_DEZ -> {
                        Text("Rec 12m: ${formatBrl(row.receivedAnnualBrl)}", style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary)
                        Text("Proj: ${formatBrl(row.projectedAnnualBrl)}/ano", style = MonoValueStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize), color = AmberGold)
                    }
                    DividendMonitorViewTab.POR_ATIVO -> {
                        Text("Mês: Rec ${formatBrl(row.receivedMonthBrl)} | Proj ${formatBrl(row.projectedMonthBrl)}", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                        Text("Ano: Rec ${formatBrl(row.receivedAnnualBrl)} | Proj ${formatBrl(row.projectedAnnualBrl)}", style = MaterialTheme.typography.labelSmall, color = AmberGold)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickRecordDividendDialog(
    suggestedTickers: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (ticker: String, totalReceivedBrl: Double, notes: String) -> Unit
) {
    var ticker by remember { mutableStateOf(suggestedTickers.firstOrNull() ?: "") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("Dividendo / JCP / Rendimento recebido") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Provento Recebido") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ticker,
                    onValueChange = { ticker = it.uppercase() },
                    label = { Text("Ticker (ex.: HGLG11, ITUB4)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dividend_dialog_ticker_input")
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Valor líquido recebido (R$)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dividend_dialog_amount_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observação (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val amount = parseBrlDecimal(amountText) ?: 0.0
            Button(
                onClick = { onConfirm(ticker.trim().uppercase(), amount, notes.trim()) },
                enabled = ticker.trim().length >= 4 && amount > 0.0,
                modifier = Modifier.testTag("dividend_dialog_confirm_button")
            ) {
                Text("Salvar Provento")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

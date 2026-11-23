package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AssetDividendOperationRecord
import com.example.domain.model.AssetDividendRow
import com.example.domain.model.DataComGroup
import com.example.domain.model.DividendMonitorCalculator
import com.example.ui.components.FintechCard
import com.example.ui.components.GroupBadge
import com.example.ui.components.MonthlyDividendBarChart
import com.example.ui.components.formatBrl
import com.example.ui.components.formatPct
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextSecondary
import java.util.Locale

data class DividendRecordDraft(
    val ticker: String = "",
    val monthIndex: Int = 0,
    val year: Int = 2026,
    val suggestedTotalBrl: Double = 0.0,
    val suggestedPerUnitBrl: Double = 0.0,
    val positionQuantity: Double = 0.0,
    val preferredDay: Int? = null
)

@Composable
internal fun MonthlyAssetDividendCard(
    row: AssetDividendRow,
    selectedMonthIndex: Int,
    selectedYear: Int,
    onQuickRecord: (monthIndex: Int) -> Unit,
    onOpenSingleAsset: () -> Unit,
    onDeleteOperation: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(row.ticker, selectedYear) { mutableStateOf(false) }

    val receivedMonth = row.receivedForMonth(selectedMonthIndex)
    val projectedMonth = row.projectedForMonth(selectedMonthIndex)
    val monthOps = remember(row.operationsInYear, selectedMonthIndex) {
        row.operationsForMonth(selectedMonthIndex)
    }
    val progress = when {
        projectedMonth > 0.0 -> (receivedMonth / projectedMonth).toFloat().coerceIn(0f, 1f)
        receivedMonth > 0.0 -> 1f
        else -> 0f
    }
    val pctRealized = if (projectedMonth > 0.0) (receivedMonth / projectedMonth) * 100.0 else if (receivedMonth > 0.0) 100.0 else 0.0
    val diffMonth = receivedMonth - projectedMonth

    val (statusLabel, statusColor) = when {
        receivedMonth > 0.0 && projectedMonth > 0.0 && receivedMonth >= projectedMonth * 1.01 ->
            "Acima (+${formatPct(((receivedMonth / projectedMonth) - 1.0) * 100.0, false)})" to EmeraldPrimary
        receivedMonth > 0.0 && (projectedMonth <= 0.0 || receivedMonth >= projectedMonth * 0.95) ->
            "Recebido" to EmeraldPrimary
        receivedMonth > 0.0 ->
            "Parcial (${"%.0f".format(pctRealized)}%)" to AmberGold
        projectedMonth > 0.0 ->
            "Pendente" to ElectricCyan
        else ->
            "Sem previsão" to TextSecondary
    }

    val monthLabel = DividendMonitorCalculator.monthLabelsPt.getOrElse(selectedMonthIndex) { "Mês" }

    FintechCard(
        modifier = modifier.testTag("dividend_asset_card_${row.ticker}"),
        borderColor = when {
            receivedMonth > 0.0 -> EmeraldPrimary.copy(alpha = 0.42f)
            projectedMonth > 0.0 -> ElectricCyan.copy(alpha = 0.28f)
            else -> SubtleBorder
        }
    ) {
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
                        text = row.ticker,
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldPrimary
                    )
                    row.dataComGroup?.let { GroupBadge(it) }
                    DividendStatusPill(label = statusLabel, color = statusColor)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${row.name} • ${"%.0f".format(row.quantity)} unid. • ${row.assetClass.shortLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val dcText = when {
                    !row.isCyclical -> row.dataComDay?.let { "Não cíclico • Dia $it" } ?: "Não cíclico"
                    row.dataComDay != null -> "Data-Com dia ${row.dataComDay}"
                    else -> "Data-Com livre"
                }
                val pgtoText = row.paymentDay?.let { " • Pgto dia $it" }.orEmpty()
                val dyText = if (row.hasEstimateData) {
                    " • DY ${formatPct(row.estimatedDyPercent, false)} • YoC ${formatPct(row.yieldOnCostPercent, false)}"
                } else {
                    ""
                }
                Text(
                    text = "$dcText$pgtoText$dyText",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.testTag("dividend_expand_${row.ticker}")
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.cd_expand_asset_months),
                    tint = ElectricCyan
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactMetricBox(
                label = "Recebido ($monthLabel)",
                value = formatBrl(receivedMonth),
                accent = EmeraldPrimary,
                modifier = Modifier.weight(1f)
            )
            CompactMetricBox(
                label = "Projetado ($monthLabel)",
                value = formatBrl(projectedMonth),
                accent = ElectricCyan,
                modifier = Modifier.weight(1f)
            )
            CompactMetricBox(
                label = "Ano $selectedYear",
                value = formatBrl(row.receivedYearBrl),
                subValue = "Proj. ${formatBrl(row.projectedAnnualBrl)}",
                accent = AmberGold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = if (receivedMonth >= projectedMonth && receivedMonth > 0.0) EmeraldPrimary else ElectricCyan,
            trackColor = SubtleBorder.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val diffPrefix = if (diffMonth > 0) "+" else ""
            val diffColor = when {
                diffMonth > 0.01 -> EmeraldPrimary
                diffMonth < -0.01 && receivedMonth > 0.0 -> AmberGold
                else -> TextSecondary
            }
            Text(
                text = if (projectedMonth > 0.0) {
                    "Dif. mês: $diffPrefix${formatBrl(diffMonth)} (${"%.0f".format(pctRealized)}%)"
                } else if (row.projectedPerUnitMonthBrl > 0.0) {
                    "Ref.: ${formatBrl(row.projectedPerUnitMonthBrl)}/cota"
                } else {
                    "Sem projeção mensal cadastrada"
                },
                style = MaterialTheme.typography.labelSmall,
                color = diffColor
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = onOpenSingleAsset,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("dividend_detail_12m_${row.ticker}")
                ) {
                    Text("12 meses")
                }
                OutlinedButton(
                    onClick = { onQuickRecord(selectedMonthIndex) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("dividend_quick_record_${row.ticker}")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Lançar")
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = SubtleBorder.copy(alpha = 0.7f))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Mapa Mensal de ${row.ticker} ($selectedYear) — toque no mês para lançar",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan
                )
                Spacer(Modifier.height(8.dp))

                Asset12MonthMiniGrid(
                    row = row,
                    selectedMonthIndex = selectedMonthIndex,
                    onClickMonth = onQuickRecord
                )

                if (monthOps.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Lançamentos em $monthLabel/$selectedYear (${monthOps.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    monthOps.forEach { op ->
                        DividendOperationRow(
                            record = op,
                            onDelete = { onDeleteOperation(op.operationId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun Asset12MonthMiniGrid(
    row: AssetDividendRow,
    selectedMonthIndex: Int,
    onClickMonth: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (chunkStart in 0 until 12 step 3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (m in chunkStart until (chunkStart + 3)) {
                    val pt = row.monthlyPoints.getOrNull(m)
                    val rec = pt?.receivedBrl ?: 0.0
                    val proj = pt?.projectedBrl ?: 0.0
                    val isSelected = m == selectedMonthIndex
                    val borderCol = when {
                        isSelected -> EmeraldPrimary
                        rec > 0.0 -> EmeraldPrimary.copy(alpha = 0.45f)
                        proj > 0.0 -> ElectricCyan.copy(alpha = 0.25f)
                        else -> SubtleBorder
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) EmeraldContainer.copy(alpha = 0.45f) else Color.Transparent,
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderCol),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 58.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onClickMonth(m) }
                            .testTag("asset_month_cell_${row.ticker}_$m")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = DividendMonitorCalculator.monthLabelsPt[m],
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                if (rec > 0.0) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Recebido",
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                } else if (proj > 0.0) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Projetado",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Text(
                                text = "R: ${formatCompactBrl(rec)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (rec > 0.0) EmeraldPrimary else TextSecondary,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "P: ${formatCompactBrl(proj)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (proj > 0.0) ElectricCyan else TextSecondary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SingleAssetMonthlyDetailSection(
    rows: List<AssetDividendRow>,
    selectedTicker: String?,
    selectedMonthIndex: Int,
    selectedYear: Int,
    onSelectTicker: (String) -> Unit,
    onSelectMonth: (Int) -> Unit,
    onQuickRecord: (AssetDividendRow, Int) -> Unit,
    onDeleteOperation: (Long) -> Unit
) {
    if (rows.isEmpty()) {
        FintechCard {
            Text(
                text = "Cadastre ou importe ativos na Carteira Real para visualizar o controle mensal de 12 meses por ativo.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        return
    }

    val activeRow = remember(rows, selectedTicker) {
        rows.firstOrNull { it.ticker.equals(selectedTicker, ignoreCase = true) } ?: rows.first()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FintechCard {
            Text("Selecione o Ativo (12 Meses • $selectedYear)", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { row ->
                    val isSelected = row.ticker == activeRow.ticker
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectTicker(row.ticker) },
                        label = { Text(row.ticker) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldContainer,
                            selectedLabelColor = EmeraldPrimary
                        ),
                        modifier = Modifier.testTag("dividend_asset_selector_${row.ticker}")
                    )
                }
            }
        }

        FintechCard(borderColor = EmeraldPrimary.copy(alpha = 0.4f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(activeRow.ticker, style = MaterialTheme.typography.titleLarge, color = EmeraldPrimary)
                        activeRow.dataComGroup?.let { GroupBadge(it) }
                    }
                    Text(
                        "${activeRow.name} • ${"%.0f".format(activeRow.quantity)} cotas/ações • ${activeRow.assetClass.shortLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            val monthName = DividendMonitorCalculator.monthLabelsPt.getOrElse(selectedMonthIndex) { "Mês" }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactMetricBox(
                    label = "Recebido ($monthName)",
                    value = formatBrl(activeRow.receivedForMonth(selectedMonthIndex)),
                    accent = EmeraldPrimary,
                    modifier = Modifier.weight(1f)
                )
                CompactMetricBox(
                    label = "Projetado ($monthName)",
                    value = formatBrl(activeRow.projectedForMonth(selectedMonthIndex)),
                    accent = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactMetricBox(
                    label = "Recebido ($selectedYear)",
                    value = formatBrl(activeRow.receivedYearBrl),
                    subValue = "Média: ${formatBrl(activeRow.receivedYearBrl / 12.0)}/mês",
                    accent = EmeraldPrimary,
                    modifier = Modifier.weight(1f)
                )
                CompactMetricBox(
                    label = "Projetado ($selectedYear)",
                    value = formatBrl(activeRow.projectedAnnualBrl),
                    subValue = "DY ${formatPct(activeRow.estimatedDyPercent, false)} • YoC ${formatPct(activeRow.yieldOnCostPercent, false)}",
                    accent = AmberGold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            MonthlyDividendBarChart(
                points = activeRow.monthlyPoints,
                selectedMonthIndex = selectedMonthIndex,
                onSelectMonth = onSelectMonth,
                modifier = Modifier.testTag("single_asset_dividend_chart_${activeRow.ticker}")
            )
        }

        FintechCard {
            Text(
                "Detalhamento Mensal de ${activeRow.ticker} ($selectedYear)",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Compare o recebido com o projetado mês a mês e lance proventos diretamente no mês desejado.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))

            activeRow.monthlyPoints.forEach { pt ->
                val isSelected = pt.monthIndex == selectedMonthIndex
                val monthOps = activeRow.operationsForMonth(pt.monthIndex)
                val diff = pt.receivedBrl - pt.projectedBrl
                val rowBg = if (isSelected) EmeraldContainer.copy(alpha = 0.25f) else Color.Transparent

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(rowBg)
                        .clickable { onSelectMonth(pt.monthIndex) }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${DividendMonitorCalculator.monthFullNamesPt[pt.monthIndex]}/$selectedYear",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            val diffTxt = when {
                                pt.receivedBrl > 0.0 && pt.projectedBrl > 0.0 -> {
                                    val prefix = if (diff >= 0) "+" else ""
                                    "Dif.: $prefix${formatBrl(diff)}"
                                }
                                pt.receivedBrl > 0.0 -> "${monthOps.size} lançamento(s)"
                                pt.projectedBrl > 0.0 -> "Aguardando recebimento"
                                else -> "Sem previsão no mês"
                            }
                            Text(diffTxt, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Rec: ${formatBrl(pt.receivedBrl)}",
                                style = MonoValueStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                                color = if (pt.receivedBrl > 0.0) EmeraldPrimary else TextSecondary
                            )
                            Text(
                                text = "Proj: ${formatBrl(pt.projectedBrl)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = { onQuickRecord(activeRow, pt.monthIndex) },
                            modifier = Modifier.testTag("single_asset_add_${activeRow.ticker}_${pt.monthIndex}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = "Lançar provento em ${pt.monthLabel}",
                                tint = EmeraldPrimary
                            )
                        }
                    }

                    if (monthOps.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        monthOps.forEach { op ->
                            DividendOperationRow(
                                record = op,
                                onDelete = { onDeleteOperation(op.operationId) }
                            )
                        }
                    }
                }
                if (pt.monthIndex < 11) {
                    HorizontalDivider(color = SubtleBorder.copy(alpha = 0.45f))
                }
            }
        }
    }
}

@Composable
internal fun DividendOperationRow(
    record: AssetDividendOperationRecord,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${record.dateIso} • ${formatBrl(record.totalValueBrl)}",
                style = MaterialTheme.typography.labelMedium,
                color = EmeraldPrimary
            )
            val detail = buildString {
                if (record.quantity > 1.0) {
                    append("${"%.0f".format(record.quantity)} cotas × ${formatBrl(record.unitValueBrl)}")
                }
                if (record.notes.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(record.notes)
                }
            }
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("dividend_delete_op_${record.operationId}")
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = stringResource(R.string.cd_delete_dividend),
                tint = CoralRed,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecordMonthlyDividendDialog(
    draft: DividendRecordDraft,
    portfolioRows: List<AssetDividendRow>,
    onDismiss: () -> Unit,
    onConfirm: (ticker: String, quantity: Double, unitPrice: Double, dateIso: String, notes: String) -> Unit
) {
    var ticker by remember(draft) { mutableStateOf(draft.ticker.ifBlank { portfolioRows.firstOrNull()?.ticker.orEmpty() }) }
    var usePerUnitMode by remember(draft) { mutableStateOf(false) }
    var amountText by remember(draft) {
        val initial = if (draft.suggestedTotalBrl > 0.0) {
            String.format(Locale("pt", "BR"), "%.2f", draft.suggestedTotalBrl)
        } else {
            ""
        }
        mutableStateOf(initial)
    }
    var dateText by remember(draft) {
        mutableStateOf(
            DividendMonitorCalculator.formatDateForMonth(
                monthIndex = draft.monthIndex,
                year = draft.year,
                preferredDay = draft.preferredDay
            )
        )
    }
    var selectedType by remember { mutableStateOf("Dividendo") }
    var notes by remember { mutableStateOf("") }

    val matchedAsset = remember(ticker, portfolioRows) {
        portfolioRows.firstOrNull { it.ticker.equals(ticker.trim(), ignoreCase = true) }
    }
    val ownedQty = matchedAsset?.quantity?.takeIf { it > 0.0 } ?: draft.positionQuantity.takeIf { it > 0.0 } ?: 1.0
    val parsedInput = parseBrlDecimal(amountText) ?: 0.0
    val computedTotal = if (usePerUnitMode) parsedInput * ownedQty else parsedInput

    val eventTypes = listOf("Dividendo", "Rendimento", "JCP", "Amortização")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Provento Mensal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (portfolioRows.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        portfolioRows.take(10).forEach { row ->
                            FilterChip(
                                selected = ticker.equals(row.ticker, ignoreCase = true),
                                onClick = {
                                    ticker = row.ticker
                                    val proj = row.projectedForMonth(draft.monthIndex)
                                    if (proj > 0.0 && amountText.isBlank()) {
                                        val suggested = if (usePerUnitMode && row.quantity > 0.0) proj / row.quantity else proj
                                        amountText = String.format(Locale("pt", "BR"), "%.2f", suggested)
                                    }
                                },
                                label = { Text(row.ticker, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = ticker,
                    onValueChange = { ticker = it.uppercase() },
                    label = { Text("Ticker (ex.: MXRF11, SMRE11, ITUB4)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monthly_dividend_ticker_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !usePerUnitMode,
                        onClick = {
                            if (usePerUnitMode) {
                                usePerUnitMode = false
                                if (parsedInput > 0.0) {
                                    amountText = String.format(Locale("pt", "BR"), "%.2f", parsedInput * ownedQty)
                                }
                            }
                        },
                        label = { Text("Valor Total (R$)") },
                        modifier = Modifier.testTag("monthly_dividend_mode_total")
                    )
                    FilterChip(
                        selected = usePerUnitMode,
                        onClick = {
                            if (!usePerUnitMode) {
                                usePerUnitMode = true
                                if (parsedInput > 0.0 && ownedQty > 0.0) {
                                    amountText = String.format(Locale("pt", "BR"), "%.4f", parsedInput / ownedQty)
                                }
                            }
                        },
                        label = { Text("Por Cota (R$)") },
                        modifier = Modifier.testTag("monthly_dividend_mode_unit")
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = {
                        Text(
                            if (usePerUnitMode) "Valor por cota/ação (R$)"
                            else "Valor líquido recebido (R$)"
                        )
                    },
                    supportingText = {
                        if (usePerUnitMode) {
                            Text("${"%.0f".format(ownedQty)} cotas → Total: ${formatBrl(computedTotal)}")
                        } else if (matchedAsset != null && matchedAsset.projectedForMonth(draft.monthIndex) > 0.0) {
                            Text("Projetado no mês: ${formatBrl(matchedAsset.projectedForMonth(draft.monthIndex))}")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monthly_dividend_amount_input")
                )

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Data de pagamento (dd/MM/aaaa)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monthly_dividend_date_input")
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    eventTypes.forEach { ev ->
                        FilterChip(
                            selected = selectedType == ev,
                            onClick = { selectedType = ev },
                            label = { Text(ev, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

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
            val isValid = ticker.trim().length >= 4 && computedTotal > 0.0
            Button(
                onClick = {
                    val cleanTicker = ticker.trim().uppercase()
                    val finalNotes = listOf(selectedType, notes.trim())
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    val finalQty = if (usePerUnitMode) ownedQty else 1.0
                    val finalUnit = if (usePerUnitMode) parsedInput else computedTotal
                    onConfirm(cleanTicker, finalQty, finalUnit, dateText.trim(), finalNotes)
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = EmeraldOnPrimary
                ),
                modifier = Modifier.testTag("monthly_dividend_confirm_button")
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
internal fun CompactMetricBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    subValue: String? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MonoValueStyle.copy(
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    fontWeight = FontWeight.Bold
                ),
                color = accent,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            if (!subValue.isNullOrBlank()) {
                Text(
                    text = subValue,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DividendStatusPill(
    label: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}

private fun formatCompactBrl(value: Double): String {
    if (value <= 0.0) return "—"
    return if (value >= 1000.0) {
        String.format(Locale("pt", "BR"), "R$ %.1fk", value / 1000.0)
    } else {
        String.format(Locale("pt", "BR"), "R$ %.2f", value)
    }
}

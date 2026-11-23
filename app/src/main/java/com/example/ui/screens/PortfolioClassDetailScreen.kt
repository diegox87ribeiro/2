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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.PortfolioItemView
import com.example.ui.components.DataDisclaimerBanner
import com.example.ui.components.FilterChipBar
import com.example.ui.components.FintechCard
import com.example.ui.components.GroupBadge
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

@Composable
fun PortfolioClassDetailScreen(
    assetClass: AssetClass,
    state: InvestDashboardState,
    onSelectClass: (AssetClass) -> Unit,
    onUpdatePosition: (
        id: Long,
        ticker: String,
        name: String,
        assetClass: AssetClass,
        quantity: Double,
        averagePrice: Double,
        currentPrice: Double?,
        sector: String,
        dataComDay: Int?,
        existingSourceLabel: String,
        isCyclical: Boolean
    ) -> Unit,
    onDeletePosition: (Long) -> Unit,
    onOpenCarteira: () -> Unit,
    onBack: () -> Unit
) {
    var editingPosition by remember { mutableStateOf<PortfolioItemView?>(null) }

    val classPositions = remember(state.realPositions, assetClass) {
        state.realPositions
            .filter { it.assetClass == assetClass }
            .sortedByDescending { it.totalCurrentBrl }
    }
    val classTotalCurrent = remember(classPositions) { classPositions.sumOf { it.totalCurrentBrl } }
    val classTotalCost = remember(classPositions) { classPositions.sumOf { it.totalCostBrl } }
    val portfolioInvested = state.realHhi.totalInvestedBrl.coerceAtLeast(1.0)
    val classWeightPct = (classTotalCurrent / portfolioInvested) * 100.0
    val classPnlBrl = classTotalCurrent - classTotalCost
    val classPnlPct = if (classTotalCost > 0.0) (classPnlBrl / classTotalCost) * 100.0 else 0.0

    val presentClasses = remember(state.realPositions, assetClass) {
        val owned = state.realPositions.map { it.assetClass }.distinct()
        if (assetClass in owned) owned else listOf(assetClass) + owned
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("portfolio_class_detail_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FintechCard(borderColor = EmeraldPrimary.copy(alpha = 0.45f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${assetClass.label} • Carteira Real",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${classPositions.size} ativo(s) • Peso ${"%.1f".format(classWeightPct)}% da carteira",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Atual", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(formatBrl(classTotalCurrent), style = MonoValueStyle)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Resultado (L/P)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    "${if (classPnlBrl >= 0) "+" else ""}${formatBrl(classPnlBrl)} (${formatPct(classPnlPct)})",
                                    style = MonoValueStyle,
                                    color = if (classPnlBrl >= 0) EmeraldPrimary else CoralRed
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        if (presentClasses.size > 1) {
            item {
                FilterChipBar(
                    options = presentClasses.map { it to it.shortLabel },
                    selected = assetClass,
                    onSelect = onSelectClass
                )
            }
        }

        if (classPositions.isEmpty()) {
            item {
                DataDisclaimerBanner(
                    text = "Nenhum ativo da classe ${assetClass.label} na Carteira Real."
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onOpenCarteira,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = EmeraldOnPrimary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Ir para Carteira")
                }
            }
        } else {
            items(classPositions, key = { it.id }) { pos ->
                val weightInPortfolio = (pos.totalCurrentBrl / portfolioInvested) * 100.0
                FintechCard(borderColor = EmeraldPrimary.copy(alpha = 0.25f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = EmeraldContainer.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .testTag("class_asset_ticker_${pos.ticker}")
                                        .clickable { editingPosition = pos }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(pos.ticker, style = MaterialTheme.typography.titleMedium, color = EmeraldPrimary)
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Default.Edit, contentDescription = "Editar ${pos.ticker}", tint = EmeraldPrimary, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Editar", style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary)
                                    }
                                }
                                if (pos.isCyclical) {
                                    GroupBadge(pos.dataComGroup)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("${pos.name} • ${pos.sector}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            val cycleSuffix = if (pos.isCyclical) {
                                "Data-Com: ${pos.dataComDay?.let { "Dia $it" } ?: "N/D"}"
                            } else {
                                "Não cíclico${pos.dataComDay?.let { " • Dia $it" }.orEmpty()}"
                            }
                            Text(
                                "Peso na carteira: ${"%.1f".format(weightInPortfolio)}% • $cycleSuffix",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatBrl(pos.totalCurrentBrl), style = MonoValueStyle)
                            Text("${"%.2f".format(pos.quantity)} x ${formatBrl(pos.averagePrice)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            if (pos.hasMarketPrice) {
                                Text(
                                    formatPct(pos.profitLossPercent),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (pos.profitLossPercent >= 0) EmeraldPrimary else CoralRed
                                )
                            } else {
                                Text("Sem cotação atual", style = MaterialTheme.typography.labelSmall, color = AmberGold)
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("portfolio_class_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Voltar para Dashboard")
            }
        }
    }

    editingPosition?.let { editingPos ->
        val catalogMatch = state.marketAssets.firstOrNull {
            it.ticker.equals(editingPos.ticker, ignoreCase = true)
        }
        AssetPositionDetailAndEditSheet(
            position = editingPos,
            catalogAsset = catalogMatch,
            portfolioTotalInvestedBrl = state.realHhi.totalInvestedBrl,
            onDismiss = { editingPosition = null },
            onSave = { id, ticker, name, ac, qty, pm, cur, sector, dcDay, src, isCyclical ->
                onUpdatePosition(id, ticker, name, ac, qty, pm, cur, sector, dcDay, src, isCyclical)
                editingPosition = null
            },
            onDelete = { id ->
                onDeletePosition(id)
                editingPosition = null
            }
        )
    }
}

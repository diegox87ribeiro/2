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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.example.domain.model.DataComGroup
import com.example.domain.model.MarketAsset
import com.example.ui.components.AssetDetailSheetContent
import com.example.ui.components.DataDisclaimerBanner
import com.example.ui.components.FilterChipBar
import com.example.ui.components.FintechCard
import com.example.ui.components.GroupBadge
import com.example.ui.components.formatBrl
import com.example.ui.components.formatPct
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.InvestDashboardState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataComGroupDetailScreen(
    group: DataComGroup,
    state: InvestDashboardState,
    onSelectGroup: (DataComGroup) -> Unit,
    onBack: () -> Unit,
    onOpenCarteira: () -> Unit
) {
    var inspectedMarketAsset by remember { mutableStateOf<MarketAsset?>(null) }

    val realPositionsByTicker = remember(state.realPositions) {
        state.realPositions.associateBy { it.ticker.trim().uppercase() }
    }

    val catalogAssetsInGroup = remember(state.marketAssets, realPositionsByTicker, group) {
        state.marketAssets
            .filter { asset ->
                val effectiveDay = realPositionsByTicker[asset.ticker.trim().uppercase()]?.dataComDay ?: asset.dataComDay
                DataComGroup.fromDay(effectiveDay) == group
            }
            .sortedWith(compareBy<MarketAsset> {
                realPositionsByTicker[it.ticker.trim().uppercase()]?.dataComDay ?: it.dataComDay ?: 99
            }.thenBy { it.ticker })
    }

    val realPositionsInGroup = remember(state.realPositions, state.liveQuotesByTicker, state.marketAssets, group) {
        val marketByTicker = state.marketAssets.associateBy { it.ticker.trim().uppercase() }
        state.realPositions
            .filter { pos ->
                val upper = pos.ticker.trim().uppercase()
                val effectiveDay = pos.dataComDay ?: state.liveQuotesByTicker[upper]?.dataComDay ?: marketByTicker[upper]?.dataComDay
                DataComGroup.fromDay(effectiveDay) == group
            }
            .sortedWith(compareBy<com.example.domain.model.PortfolioItemView> { it.dataComDay ?: 99 }.thenBy { it.ticker })
    }

    val projectedDividendGroupBrl = remember(catalogAssetsInGroup, realPositionsByTicker) {
        catalogAssetsInGroup.sumOf { asset ->
            val owned = realPositionsByTicker[asset.ticker.uppercase()]
            if (owned != null && asset.lastDividendBrl > 0) owned.quantity * asset.lastDividendBrl else 0.0
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("datacom_group_detail_list"),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GroupBadge(group)
                            Spacer(Modifier.width(8.dp))
                            Text(group.label, style = MaterialTheme.typography.titleMedium, color = EmeraldPrimary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${catalogAssetsInGroup.size} ativos no catálogo • ${realPositionsInGroup.size} na Carteira Real",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        if (projectedDividendGroupBrl > 0.0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Projeção de proventos no grupo: ${formatBrl(projectedDividendGroupBrl)}",
                                style = MonoValueStyle,
                                color = EmeraldPrimary
                            )
                        }
                    }
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(26.dp))
                }
            }
        }

        item {
            FilterChipBar(
                options = DataComGroup.entries.map { it to it.code },
                selected = group,
                onSelect = onSelectGroup
            )
        }

        if (realPositionsInGroup.isNotEmpty()) {
            item {
                Text(
                    "Na sua Carteira Real (${realPositionsInGroup.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = EmeraldPrimary
                )
            }
            items(realPositionsInGroup, key = { "real_${it.id}" }) { pos ->
                FintechCard(
                    borderColor = EmeraldPrimary.copy(alpha = 0.35f),
                    modifier = Modifier
                        .testTag("datacom_real_asset_${pos.ticker}")
                        .clickable {
                            val match = state.marketAssets.firstOrNull { it.ticker.equals(pos.ticker, ignoreCase = true) }
                            if (match != null) inspectedMarketAsset = match else onOpenCarteira()
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(pos.ticker, style = MaterialTheme.typography.titleMedium, color = EmeraldPrimary)
                                Spacer(Modifier.width(8.dp))
                                GroupBadge(group)
                            }
                            Text("${pos.name} • ${pos.assetClass.shortLabel}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                "Data-Com: Dia ${pos.dataComDay} • ${"%.2f".format(pos.quantity)} cotas",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatBrl(pos.totalCurrentBrl), style = MonoValueStyle)
                            Text("PM ${formatBrl(pos.averagePrice)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Ativos do Grupo ${group.code} (${catalogAssetsInGroup.size})",
                style = MaterialTheme.typography.titleSmall,
                color = ElectricCyan
            )
        }

        if (catalogAssetsInGroup.isEmpty()) {
            item {
                DataDisclaimerBanner(
                    text = "Nenhum ativo do catálogo está classificado no grupo ${group.code}. Cadastre ou edite o Dia Data-Com em seus ativos na Carteira.",
                    isWarning = false
                )
            }
        } else {
            items(catalogAssetsInGroup, key = { "cat_${it.ticker}" }) { asset ->
                val owned = realPositionsByTicker[asset.ticker.uppercase()]
                FintechCard(
                    modifier = Modifier
                        .testTag("datacom_catalog_asset_${asset.ticker}")
                        .clickable { inspectedMarketAsset = asset }
                ) {
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
                            Text("${asset.name} • ${asset.assetClass.shortLabel} • ${asset.sector}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                "Data-Com: Dia ${asset.dataComDay} • Pgto previsto: Dia ${asset.paymentDay ?: "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricCyan
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatBrl(asset.price), style = MonoValueStyle)
                            Text(
                                "${formatBrl(asset.lastDividendBrl)}/cota • DY ${formatPct(asset.dy12m, false)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldPrimary
                            )
                        }
                    }
                    if (owned != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Na sua Carteira Real: ${"%.2f".format(owned.quantity)} cotas • Projeção ${formatBrl(owned.quantity * asset.lastDividendBrl)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldPrimary
                        )
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
                    .testTag("datacom_group_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Voltar")
            }
        }
    }

    inspectedMarketAsset?.let { asset ->
        ModalBottomSheet(onDismissRequest = { inspectedMarketAsset = null }) {
            AssetDetailSheetContent(asset)
        }
    }
}

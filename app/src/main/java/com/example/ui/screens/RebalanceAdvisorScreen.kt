package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.RebalanceAdvisorCalculator
import com.example.ui.components.DataDisclaimerBanner
import com.example.ui.components.FintechCard
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
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RebalanceAdvisorScreen(
    state: InvestDashboardState,
    onSaveTargets: (Double, Double, Double, Double, Double, Double) -> Unit
) {
    val prefs = state.preferences
    var contributionText by remember { mutableStateOf("2000,00") }
    var respectHhiCap by remember { mutableStateOf(true) }

    var pctAcoes by remember(prefs) { mutableFloatStateOf(prefs.targetPctAcoes.toFloat()) }
    var pctFii by remember(prefs) { mutableFloatStateOf(prefs.targetPctFii.toFloat()) }
    var pctFiagro by remember(prefs) { mutableFloatStateOf(prefs.targetPctFiagro.toFloat()) }
    var pctEtf by remember(prefs) { mutableFloatStateOf(prefs.targetPctEtf.toFloat()) }
    var pctRf by remember(prefs) { mutableFloatStateOf(prefs.targetPctRendaFixa.toFloat()) }
    var pctCripto by remember(prefs) { mutableFloatStateOf(prefs.targetPctCripto.toFloat()) }

    val rawTargets = remember(pctAcoes, pctFii, pctFiagro, pctEtf, pctRf, pctCripto) {
        mapOf(
            AssetClass.ACOES_BR to pctAcoes.toDouble(),
            AssetClass.FII to pctFii.toDouble(),
            AssetClass.FIAGRO to pctFiagro.toDouble(),
            AssetClass.ETF to pctEtf.toDouble(),
            AssetClass.RENDA_FIXA to pctRf.toDouble(),
            AssetClass.CRIPTO to pctCripto.toDouble()
        )
    }
    val totalPctSum = rawTargets.values.sum()
    val contributionBrl = parseBrlDecimal(contributionText) ?: 0.0

    val plan = remember(state.realPositions, state.marketAssets, rawTargets, contributionBrl, respectHhiCap) {
        RebalanceAdvisorCalculator.calculatePlan(
            realPositions = state.realPositions,
            marketAssets = state.marketAssets,
            rawTargets = rawTargets,
            contributionBrl = contributionBrl,
            respectHhiCap = respectHhiCap
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("rebalance_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DataDisclaimerBanner(
                text = "Assistente de Aporte & Rebalanceamento (Somente Análise). Calcula onde alocar o novo aporte para reduzir desvios de classe e concentração HHI."
            )
        }

        item {
            FintechCard {
                Text("Valor do Novo Aporte (R$)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = contributionText,
                    onValueChange = { contributionText = it },
                    label = { Text("Aporte planejado (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rebalance_contribution_input")
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("500,00", "1000,00", "2500,00", "5000,00").forEach { preset ->
                        FilterChip(
                            selected = contributionText == preset,
                            onClick = { contributionText = preset },
                            label = { Text("R$ $preset") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldPrimary
                            )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Respeitar teto HHI (máx. 20% por ativo)", style = MaterialTheme.typography.bodyMedium)
                        Text("Evita concentrar o aporte em ativos que já passaram de 20%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Switch(
                        checked = respectHhiCap,
                        onCheckedChange = { respectHhiCap = it },
                        modifier = Modifier.testTag("rebalance_hhi_cap_switch")
                    )
                }
            }
        }

        item {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Metas por Classe", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Soma: ${"%.0f".format(totalPctSum)}%",
                        style = MonoValueStyle,
                        color = if (totalPctSum in 99.0..101.0) EmeraldPrimary else AmberGold
                    )
                }
                Spacer(Modifier.height(8.dp))

                TargetClassSliderRow("Ações BR", pctAcoes) { pctAcoes = it }
                TargetClassSliderRow("FIIs", pctFii) { pctFii = it }
                TargetClassSliderRow("FIAGROs", pctFiagro) { pctFiagro = it }
                TargetClassSliderRow("ETFs", pctEtf) { pctEtf = it }
                TargetClassSliderRow("Renda Fixa", pctRf) { pctRf = it }
                TargetClassSliderRow("Cripto", pctCripto) { pctCripto = it }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val norm = RebalanceAdvisorCalculator.normalizeTargets(rawTargets)
                            pctAcoes = (norm[AssetClass.ACOES_BR] ?: 0.0).roundToInt().toFloat()
                            pctFii = (norm[AssetClass.FII] ?: 0.0).roundToInt().toFloat()
                            pctFiagro = (norm[AssetClass.FIAGRO] ?: 0.0).roundToInt().toFloat()
                            pctEtf = (norm[AssetClass.ETF] ?: 0.0).roundToInt().toFloat()
                            pctRf = (norm[AssetClass.RENDA_FIXA] ?: 0.0).roundToInt().toFloat()
                            pctCripto = (100f - pctAcoes - pctFii - pctFiagro - pctEtf - pctRf).coerceAtLeast(0f)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Ajustar p/ 100%")
                    }
                    Button(
                        onClick = {
                            val norm = RebalanceAdvisorCalculator.normalizeTargets(rawTargets)
                            onSaveTargets(
                                norm[AssetClass.ACOES_BR] ?: 35.0,
                                norm[AssetClass.FII] ?: 25.0,
                                norm[AssetClass.FIAGRO] ?: 10.0,
                                norm[AssetClass.ETF] ?: 15.0,
                                norm[AssetClass.RENDA_FIXA] ?: 10.0,
                                norm[AssetClass.CRIPTO] ?: 5.0
                            )
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save_rebalance_targets_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = EmeraldOnPrimary)
                    ) {
                        Text("Salvar Metas")
                    }
                }
            }
        }

        item {
            FintechCard(modifier = Modifier.testTag("rebalance_impact_card")) {
                Text("Impacto do Aporte na Carteira", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("HHI Antes → Depois", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            "${plan.hhiBefore.toInt()} → ${plan.hhiAfter.toInt()} pts",
                            style = MonoValueStyle.copy(fontWeight = FontWeight.Bold),
                            color = if (plan.hhiAfter <= plan.hhiBefore) EmeraldPrimary else AmberGold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("DY Estimado Antes → Depois", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            "${formatPct(plan.dyBeforePct, false)} → ${formatPct(plan.dyAfterPct, false)}",
                            style = MonoValueStyle.copy(fontWeight = FontWeight.Bold),
                            color = ElectricCyan
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = SubtleBorder)
                Spacer(Modifier.height(8.dp))
                Text("Diagnóstico por Classe (Atual vs Meta)", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                plan.classGaps.forEach { gap ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${gap.assetClass.shortLabel} (${"%.1f".format(gap.currentWeightPct)}% / Meta ${"%.0f".format(gap.targetWeightPct)}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (gap.currentWeightPct < gap.targetWeightPct) AmberGold else EmeraldPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (gap.suggestedContributionBrl > 0) "+${formatBrl(gap.suggestedContributionBrl)}" else "Equilibrada",
                            style = MonoValueStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                            color = if (gap.suggestedContributionBrl > 0) ElectricCyan else TextSecondary
                        )
                    }
                }
            }
        }

        item {
            Text("Sugestão de Alocação do Aporte (${plan.suggestions.size})", style = MaterialTheme.typography.titleMedium)
        }

        items(plan.suggestions, key = { it.ticker }) { item ->
            FintechCard(modifier = Modifier.testTag("rebalance_suggestion_${item.ticker}")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.ticker, style = MaterialTheme.typography.titleMedium, color = EmeraldPrimary)
                            if (item.isNewPosition) {
                                Text(" • NOVO NA CARTEIRA", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                            }
                        }
                        Text("${item.name} • ${item.assetClass.shortLabel}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(item.reason, style = MaterialTheme.typography.labelSmall, color = AmberGold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatBrl(item.suggestedAmountBrl), style = MonoValueStyle, color = EmeraldPrimary)
                        Text(
                            "~${"%.2f".format(item.estimatedUnits)} unid. (${formatBrl(item.unitPrice)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            "Peso ${"%.1f".format(item.currentWeightPct)}% → ${"%.1f".format(item.postWeightPct)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetClassSliderRow(
    label: String,
    value: Float,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("${value.roundToInt()}%", style = MonoValueStyle, color = ElectricCyan)
        }
        Slider(
            value = value,
            onValueChange = { onChange(it.roundToInt().toFloat()) },
            valueRange = 0f..80f
        )
    }
}

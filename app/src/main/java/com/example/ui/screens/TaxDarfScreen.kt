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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.TaxReportCalculator
import com.example.ui.components.DataDisclaimerBanner
import com.example.ui.components.FintechCard
import com.example.ui.components.formatBrl
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.InvestDashboardState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaxDarfScreen(
    state: InvestDashboardState,
    onSaveCarryLoss: (commonBrl: Double, fiiBrl: Double) -> Unit
) {
    val prefs = state.preferences
    var carryCommonText by remember(prefs.carryLossCommonBrl) {
        mutableStateOf("%.2f".format(prefs.carryLossCommonBrl).replace(".", ","))
    }
    var carryFiiText by remember(prefs.carryLossFiiBrl) {
        mutableStateOf("%.2f".format(prefs.carryLossFiiBrl).replace(".", ","))
    }

    val reports = remember(
        state.operations,
        state.realPositions,
        state.marketAssets,
        prefs.carryLossCommonBrl,
        prefs.carryLossFiiBrl
    ) {
        TaxReportCalculator.computeMonthlyReports(
            operations = state.operations,
            realPositions = state.realPositions,
            marketAssets = state.marketAssets,
            initialCarryLossCommonBrl = prefs.carryLossCommonBrl,
            initialCarryLossFiiBrl = prefs.carryLossFiiBrl
        )
    }

    var selectedMonthKey by remember(reports) {
        mutableStateOf(reports.firstOrNull()?.monthKey)
    }
    val activeReport = reports.firstOrNull { it.monthKey == selectedMonthKey } ?: reports.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("tax_darf_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DataDisclaimerBanner(
                text = "Apuração Analítica Mensal de IR B3 (DARF Código 6015). Ações têm isenção até R$ 20.000/mês em vendas; FIIs/FIAGROs (20%) e ETFs (15%) não têm isenção de R$ 20k. Valide sempre no e-CAC."
            )
        }

        item {
            FintechCard {
                Text("Prejuízos Acumulados Anteriores", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = carryCommonText,
                        onValueChange = { carryCommonText = it },
                        label = { Text("Ações/ETFs (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("tax_carry_common_input")
                    )
                    OutlinedTextField(
                        value = carryFiiText,
                        onValueChange = { carryFiiText = it },
                        label = { Text("FIIs/FIAGROs (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("tax_carry_fii_input")
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val common = parseBrlDecimal(carryCommonText) ?: 0.0
                        val fii = parseBrlDecimal(carryFiiText) ?: 0.0
                        onSaveCarryLoss(common, fii)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_tax_carry_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = EmeraldOnPrimary)
                ) {
                    Text("Aplicar Prejuízo Acumulado")
                }
            }
        }

        if (reports.isEmpty() || activeReport == null) {
            item {
                FintechCard(modifier = Modifier.testTag("tax_empty_card")) {
                    Text("Nenhuma venda realizada registrada", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Registre operações de VENDA na aba Carteira ou sincronize ordens executadas no ProfitChart para calcular automaticamente o IR mensal, a faixa de isenção de R$ 20.000 e o valor do DARF 6015.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    reports.forEach { r ->
                        FilterChip(
                            selected = r.monthKey == activeReport.monthKey,
                            onClick = { selectedMonthKey = r.monthKey },
                            label = { Text(r.monthYearLabel) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldPrimary
                            ),
                            modifier = Modifier.testTag("tax_month_chip_${r.monthKey}")
                        )
                    }
                }
            }

            item {
                val darColor = when {
                    activeReport.totalDarfBrl <= 0.0 -> EmeraldPrimary
                    activeReport.isBelowDarfMinimum10Brl -> AmberGold
                    else -> CoralRed
                }
                FintechCard(borderColor = darColor.copy(alpha = 0.45f), modifier = Modifier.testTag("tax_darf_summary_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("DARF Código 6015 • ${activeReport.monthYearLabel}", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                            Text(
                                formatBrl(activeReport.totalDarfBrl),
                                style = MonoValueStyle.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize, fontWeight = FontWeight.Bold),
                                color = darColor
                            )
                        }
                        Text(
                            when {
                                activeReport.totalDarfBrl <= 0.0 -> "ISENTO / ZERADO"
                                activeReport.isBelowDarfMinimum10Brl -> "ABAIXO DE R$ 10\n(Acumular p/ próx. mês)"
                                else -> "PAGAR ATÉ ÚLTIMO\nDIA ÚTIL DO MÊS SEG."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = darColor
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Vendas Ações (limite R$ 20.000): ${formatBrl(activeReport.stockSalesBrl)}" +
                            if (activeReport.isStockExempt20k) " • ISENTO" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (activeReport.stockSalesBrl <= 20_000.0) EmeraldPrimary else CoralRed
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (activeReport.stockSalesBrl / 20_000.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (activeReport.stockSalesBrl <= 20_000.0) EmeraldPrimary else CoralRed
                    )
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = SubtleBorder)
                    Spacer(Modifier.height(8.dp))
                    TaxBreakdownRow("Ações (Swing) Lucro Líq.", activeReport.stockNetProfitBrl)
                    TaxBreakdownRow("ETFs (15%) Lucro Líq.", activeReport.etfNetProfitBrl)
                    TaxBreakdownRow("DARF Comum (Ações + ETFs 15%)", activeReport.darfCommonBrl)
                    TaxBreakdownRow("FIIs / FIAGROs (20%) Lucro Líq.", activeReport.fiiNetProfitBrl)
                    TaxBreakdownRow("DARF FIIs / FIAGROs (20%)", activeReport.darfFiiBrl)
                    if (activeReport.cryptoSalesBrl > 0.0) {
                        TaxBreakdownRow("Cripto (isenção R$ 35k)", activeReport.cryptoNetProfitBrl)
                        TaxBreakdownRow("DARF Cripto (15%)", activeReport.darfCryptoBrl)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Saldo Prejuízo a Compensar → Comum: ${formatBrl(activeReport.endingCarryLossCommonBrl)} | FIIs: ${formatBrl(activeReport.endingCarryLossFiiBrl)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
            }

            item {
                Text("Vendas Realizadas no Mês (${activeReport.realizedSales.size})", style = MaterialTheme.typography.titleMedium)
            }

            items(activeReport.realizedSales) { sale ->
                FintechCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${sale.ticker} • ${sale.category.label}", style = MaterialTheme.typography.titleSmall, color = EmeraldPrimary)
                            Text(
                                "${sale.dateLabel} • ${"%.2f".format(sale.quantity)} x ${formatBrl(sale.sellUnitPrice)} (PM ${formatBrl(sale.avgCostPrice)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                formatBrl(sale.realizedProfitBrl),
                                style = MonoValueStyle,
                                color = if (sale.realizedProfitBrl >= 0) EmeraldPrimary else CoralRed
                            )
                            Text("Venda: ${formatBrl(sale.totalSalesBrl)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxBreakdownRow(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            formatBrl(value),
            style = MonoValueStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            color = if (value >= 0.0) MaterialTheme.colorScheme.onSurface else CoralRed
        )
    }
}

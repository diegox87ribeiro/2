package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.DataComGroup
import com.example.domain.model.MarketAsset
import com.example.domain.model.PortfolioItemView
import com.example.ui.components.FintechCard
import com.example.ui.components.GroupBadge
import com.example.ui.components.formatBrl
import com.example.ui.components.formatPct
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextSecondary
import java.util.Locale

internal fun parseBrlDecimal(raw: String): Double? {
    val cleaned = raw.trim()
        .replace("R$", "", ignoreCase = true)
        .replace(" ", "")
    if (cleaned.isEmpty()) return null
    val normalized = when {
        cleaned.contains(",") && cleaned.contains(".") -> cleaned.replace(".", "").replace(",", ".")
        cleaned.contains(",") -> cleaned.replace(",", ".")
        else -> cleaned
    }
    return normalized.toDoubleOrNull()
}

private fun formatEditableNumber(value: Double): String {
    return if (value % 1.0 == 0.0) {
        String.format(Locale("pt", "BR"), "%.0f", value)
    } else {
        String.format(Locale("pt", "BR"), "%.2f", value)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetPositionDetailAndEditSheet(
    position: PortfolioItemView,
    catalogAsset: MarketAsset?,
    portfolioTotalInvestedBrl: Double,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        ticker: String,
        name: String,
        assetClass: AssetClass,
        quantity: Double,
        averagePrice: Double,
        currentPrice: Double?,
        sector: String,
        dataComDay: Int?,
        existingSourceLabel: String
    ) -> Unit,
    onDelete: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var ticker by remember(position.id) { mutableStateOf(position.ticker) }
    var name by remember(position.id) { mutableStateOf(position.name) }
    var selectedClass by remember(position.id) { mutableStateOf(position.assetClass) }
    var classDropdownExpanded by remember { mutableStateOf(false) }
    var qtyText by remember(position.id) { mutableStateOf(formatEditableNumber(position.quantity)) }
    var pmText by remember(position.id) { mutableStateOf(String.format(Locale("pt", "BR"), "%.2f", position.averagePrice)) }
    var curText by remember(position.id) {
        mutableStateOf(
            if (position.hasMarketPrice && position.currentPrice > 0.0) {
                String.format(Locale("pt", "BR"), "%.2f", position.currentPrice)
            } else {
                ""
            }
        )
    }
    var sector by remember(position.id) { mutableStateOf(position.sector) }
    var dataComText by remember(position.id) { mutableStateOf(position.dataComDay?.toString() ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val parsedQty = parseBrlDecimal(qtyText) ?: 0.0
    val parsedPm = parseBrlDecimal(pmText) ?: 0.0
    val parsedCur = parseBrlDecimal(curText)?.takeIf { it > 0.0 }
    val previewCost = parsedQty * parsedPm
    val previewCurrent = parsedQty * (parsedCur ?: parsedPm)
    val previewPnlBrl = if (parsedCur != null) previewCurrent - previewCost else 0.0
    val previewPnlPct = if (parsedCur != null && previewCost > 0.0) (previewPnlBrl / previewCost) * 100.0 else 0.0
    val weightPct = if (portfolioTotalInvestedBrl > 0.0) (previewCurrent / portfolioTotalInvestedBrl) * 100.0 else 0.0
    val parsedDataCom = dataComText.trim().toIntOrNull()?.takeIf { it in 1..31 }
    val marketDataComDay = catalogAsset?.dataComDay?.takeIf { it in 1..31 }
    val effectiveDataComDay = parsedDataCom ?: marketDataComDay
    val effectiveGroup = DataComGroup.fromDay(effectiveDataComDay)
    val marketGroup = DataComGroup.fromDay(marketDataComDay)
    val hasDataComDivergence = parsedDataCom != null && marketDataComDay != null && parsedDataCom != marketDataComDay

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepNavySurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Cadastro e Edição • ${ticker.trim().uppercase().ifBlank { position.ticker }}",
                            style = MaterialTheme.typography.titleLarge,
                            color = EmeraldPrimary
                        )
                    }
                    Text(
                        "${position.portfolioType.label} • Origem: ${position.dataSourceLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    GroupBadge(effectiveGroup, showGroupPrefix = true)
                    if (effectiveDataComDay != null && effectiveGroup != null) {
                        Text(
                            "Dia $effectiveDataComDay • ${effectiveGroup.label.substringAfter("• ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            FintechCard(borderColor = EmeraldPrimary.copy(alpha = 0.35f)) {
                Text("Resumo da Posição", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Custo Total", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(formatBrl(previewCost), style = MonoValueStyle)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Valor Atual", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(formatBrl(previewCurrent), style = MonoValueStyle, color = EmeraldPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Peso / Var.", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(
                            if (parsedCur != null) {
                                "${"%.1f".format(weightPct)}% (${formatPct(previewPnlPct)})"
                            } else {
                                "${"%.1f".format(weightPct)}% • Sem cotação"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                parsedCur == null -> AmberGold
                                previewPnlPct >= 0 -> EmeraldPrimary
                                else -> CoralRed
                            }
                        )
                    }
                }
            }

            if (catalogAsset != null) {
                FintechCard(borderColor = ElectricCyan.copy(alpha = 0.35f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ficha de Mercado (${catalogAsset.ticker})", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${catalogAsset.name} • ${catalogAsset.sector} • Ref.: ${formatBrl(catalogAsset.price)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    val safeDyLabel = if (catalogAsset.dy12m in 0.01..80.0) formatPct(catalogAsset.dy12m, false) else "N/D"
                    val safePvpLabel = if (catalogAsset.pvp in 0.05..30.0) {
                        String.format(Locale("pt", "BR"), "%.2fx", catalogAsset.pvp)
                    } else {
                        "N/D"
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("DY (12m)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(safeDyLabel, style = MonoValueStyle, color = EmeraldPrimary)
                        }
                        Column {
                            Text("P/VP", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(safePvpLabel, style = MonoValueStyle, modifier = Modifier.testTag("sheet_market_pvp_value"))
                        }
                        Column {
                            Text(
                                if (parsedDataCom != null) "Data-Com (Cadastro)" else "Data-Com",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                effectiveDataComDay?.let { "Dia $it (Grupo ${effectiveGroup?.code})" } ?: "N/D",
                                style = MonoValueStyle,
                                color = ElectricCyan,
                                modifier = Modifier.testTag("sheet_market_datacom_value")
                            )
                        }
                    }
                    if (hasDataComDivergence) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AmberGold.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("datacom_divergence_banner")
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.WarningAmber,
                                        contentDescription = "Divergência de Data-Com",
                                        tint = AmberGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Divergência de Data-Com / Grupo",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = AmberGold
                                    )
                                }
                                Text(
                                    "Seu cadastro usa Dia $parsedDataCom (Grupo ${DataComGroup.fromDay(parsedDataCom)?.code}), " +
                                        "enquanto a fonte externa informou Dia $marketDataComDay (Grupo ${marketGroup?.code}).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(
                                    onClick = { dataComText = marketDataComDay.toString() },
                                    modifier = Modifier.testTag("sync_market_datacom_button")
                                ) {
                                    Text(
                                        "Adotar Dia $marketDataComDay (Grupo ${marketGroup?.code})",
                                        color = AmberGold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(catalogAsset.riskSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            HorizontalDivider(color = SubtleBorder)

            Text("Dados Cadastrais & Posição", style = MaterialTheme.typography.titleSmall, color = EmeraldPrimary)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ticker,
                    onValueChange = { ticker = it; validationError = null },
                    label = { Text("Sigla / Ticker") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_pos_ticker")
                )
                OutlinedTextField(
                    value = dataComText,
                    onValueChange = { dataComText = it; validationError = null },
                    label = { Text("Dia Data-Com (1–31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_pos_datacom")
                )
            }
            Text(
                text = if (effectiveGroup != null && effectiveDataComDay != null) {
                    "Classificação automática do Grupo Data-Com: Grupo ${effectiveGroup.code} (${effectiveGroup.label.substringAfter("• ")})"
                } else {
                    "Informe o dia da Data-Com (1–31) para classificar entre G1 (1–8), G2 (9–16), G3 (17–24) e G4 (25–31)."
                },
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan,
                modifier = Modifier.testTag("edit_pos_group_helper")
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do Ativo") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_pos_name")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = classDropdownExpanded,
                    onExpandedChange = { classDropdownExpanded = !classDropdownExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedClass.shortLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Classe") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = classDropdownExpanded,
                        onDismissRequest = { classDropdownExpanded = false }
                    ) {
                        AssetClass.entries.forEach { cls ->
                            DropdownMenuItem(
                                text = { Text(cls.label) },
                                onClick = {
                                    selectedClass = cls
                                    classDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = sector,
                    onValueChange = { sector = it },
                    label = { Text("Setor / Segmento") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_pos_sector")
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it; validationError = null },
                    label = { Text("Quantidade") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_pos_qty")
                )
                OutlinedTextField(
                    value = pmText,
                    onValueChange = { pmText = it; validationError = null },
                    label = { Text("Preço Médio (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_pos_pm")
                )
            }

            OutlinedTextField(
                value = curText,
                onValueChange = { curText = it; validationError = null },
                label = { Text("Cotação / Preço Atual (R$, opcional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_pos_current_price")
            )

            validationError?.let { err ->
                Text(err, style = MaterialTheme.typography.bodySmall, color = CoralRed)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        val cleanTicker = ticker.trim().uppercase()
                        val q = parseBrlDecimal(qtyText) ?: 0.0
                        val pm = parseBrlDecimal(pmText) ?: 0.0
                        val cur = parseBrlDecimal(curText)
                        val dcRaw = dataComText.trim()
                        val dc = if (dcRaw.isEmpty()) null else dcRaw.toIntOrNull()

                        when {
                            cleanTicker.isEmpty() -> validationError = "Informe a sigla (Ticker) do ativo."
                            q <= 0.0 -> validationError = "A quantidade deve ser maior que zero."
                            pm <= 0.0 -> validationError = "O preço médio deve ser maior que zero."
                            dcRaw.isNotEmpty() && (dc == null || dc !in 1..31) ->
                                validationError = "Dia Data-Com deve ser um número entre 1 e 31."
                            else -> {
                                onSave(
                                    position.id,
                                    cleanTicker,
                                    name,
                                    selectedClass,
                                    q,
                                    pm,
                                    cur?.takeIf { it > 0.0 },
                                    sector,
                                    dc,
                                    position.dataSourceLabel
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp)
                        .testTag("save_position_edit_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = EmeraldOnPrimary
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Salvar Alterações")
                }
            }

            TextButton(
                onClick = { onDelete(position.id) },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = 48.dp)
                    .testTag("delete_position_from_sheet_button")
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = CoralRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Remover Posição da Carteira", color = CoralRed)
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

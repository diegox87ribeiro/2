package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.importexport.ImportParseResult
import com.example.data.importexport.SpreadsheetImporter
import com.example.domain.model.AssetClass
import com.example.domain.model.DataComGroup
import com.example.domain.model.OperationType
import com.example.domain.model.PortfolioType
import com.example.ui.components.formatBrl
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CardElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextSecondary

@Composable
fun ImportPreviewDialog(
    preview: ImportParseResult,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Pré-visualização da Importação", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${preview.fileName} • Formato ${preview.fileType}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isQuickUpdateMode = preview.positions.isEmpty() && preview.quickUpdates.isNotEmpty()
                Text(
                    when {
                        preview.positions.isNotEmpty() ->
                            "Ao confirmar, esta importação substituirá a Carteira Real atual, gravando os ativos e os proventos detectados na tabela."
                        isQuickUpdateMode ->
                            "Modo Atualização Rápida: atualiza preço, data, Data-Com e proventos dos ativos existentes na Carteira Real sem alterar quantidades nem preços médios."
                        else ->
                            "Ao confirmar, os proventos detectados na planilha serão adicionados ao histórico da Carteira Real (sem alterar posições)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val countSummary = if (isQuickUpdateMode) {
                        "${preview.quickUpdates.size} atualização(ões) • ${preview.dividends.size} provento(s)"
                    } else {
                        "${preview.positions.size} ativos • ${preview.dividends.size} provento(s)"
                    }
                    Text(
                        countSummary,
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldPrimary,
                        modifier = Modifier.testTag("preview_counts_label")
                    )
                    preview.cashBalanceBrl?.let {
                        Text("Caixa: ${formatBrl(it)}", style = MonoValueStyle, color = ElectricCyan)
                    }
                }

                if (preview.detectedColumns.isNotEmpty()) {
                    Text(
                        "Colunas: ${preview.detectedColumns.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                if (preview.warnings.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AmberGold.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .border(1.dp, AmberGold.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Alertas de Auditoria (${preview.warnings.size})", style = MaterialTheme.typography.labelMedium, color = AmberGold)
                        preview.warnings.take(5).forEach { w ->
                            Text("• $w", style = MaterialTheme.typography.bodySmall, color = AmberGold)
                        }
                        if (preview.warnings.size > 5) {
                            Text("... e mais ${preview.warnings.size - 5} alertas.", style = MaterialTheme.typography.labelSmall, color = AmberGold)
                        }
                    }
                }

                HorizontalDivider(color = SubtleBorder)

                preview.positions.forEach { pos ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardElevated.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${pos.ticker} • ${pos.assetClass}", style = MaterialTheme.typography.labelLarge, color = EmeraldPrimary)
                            Text(
                                "${"%.2f".format(pos.quantity)} x PM ${formatBrl(pos.averagePrice)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (pos.hasMarketPrice) {
                                Text(formatBrl(pos.currentPrice), style = MonoValueStyle)
                                Text("Cotação ok", style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary)
                            } else {
                                Text("Sem cotação", style = MaterialTheme.typography.labelSmall, color = AmberGold)
                            }
                        }
                    }
                }

                if (preview.positions.isEmpty() && preview.quickUpdates.isNotEmpty()) {
                    Text(
                        "Atualizações de Ativo, Preço, Data e Dividendos (${preview.quickUpdates.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldPrimary
                    )
                    preview.quickUpdates.take(10).forEach { upd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardElevated.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(upd.ticker, style = MaterialTheme.typography.labelLarge, color = EmeraldPrimary)
                                val metaParts = buildList {
                                    upd.referenceDate?.let { add("Data $it") }
                                    upd.dataComDay?.let { add("Data-Com dia $it") }
                                }
                                Text(
                                    metaParts.joinToString(" • ").ifBlank { "Atualização de ativo" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                upd.price?.let { p ->
                                    Text(formatBrl(p), style = MonoValueStyle)
                                }
                                upd.dividendAmount?.let { d ->
                                    Text("+${formatBrl(d)} div.", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                }
                            }
                        }
                    }
                    if (preview.quickUpdates.size > 10) {
                        Text(
                            "... e mais ${preview.quickUpdates.size - 10} linha(s).",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                if (preview.dividends.isNotEmpty()) {
                    HorizontalDivider(color = SubtleBorder)
                    Text(
                        "Proventos / Dividendos Detectados (${preview.dividends.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan
                    )
                    preview.dividends.take(8).forEach { divOp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ElectricCyan.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(divOp.ticker, style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                                Text("${divOp.dateIso} • ${divOp.notes.substringBefore(" •")}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            Text(formatBrl(divOp.totalValue), style = MonoValueStyle, color = EmeraldPrimary)
                        }
                    }
                    if (preview.dividends.size > 8) {
                        Text(
                            "... e mais ${preview.dividends.size - 8} provento(s).",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_import_button"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = EmeraldOnPrimary)
            ) {
                Text(
                    if (preview.positions.isEmpty() && preview.quickUpdates.isNotEmpty()) {
                        "Aplicar Atualizações"
                    } else {
                        "Confirmar na Carteira Real"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_import_button")
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun ImportStandardSpecDialog(
    onDismiss: () -> Unit,
    onDownloadTemplate: () -> Unit,
    onDownloadQuickUpdateTemplate: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modelos de Planilha CSV / XLSX") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Arquivos .csv (UTF-8 ou Windows-1252, separador ';' ou ',') e .xlsx são processados localmente. Valores aceitam formato brasileiro (ex: 1.234,56).",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Text(
                    "1) Modelo Atualização Rápida (Ativo, Preço, Data, Dividendos)",
                    style = MaterialTheme.typography.titleSmall,
                    color = ElectricCyan
                )
                Text(
                    "Atualiza cotação, data, Data-Com e lança dividendos na Carteira Real sem apagar suas quantidades e preços médios.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                SpreadsheetImporter.QUICK_UPDATE_COLUMN_SPECS.forEach { spec ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ElectricCyan.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(spec.name, style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                            Text(
                                if (spec.required) "OBRIGATÓRIA" else "OPCIONAL",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (spec.required) AmberGold else EmeraldPrimary
                            )
                        }
                        Text("Aceita: ${spec.aliases}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("Exemplo: ${spec.example}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Button(
                    onClick = {
                        onDismiss()
                        onDownloadQuickUpdateTemplate()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_download_quick_update_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = EmeraldOnPrimary)
                ) {
                    Text("Baixar Modelo Atualização (Ativo;Preço;Data;Dividendos)")
                }

                HorizontalDivider(color = SubtleBorder)

                Text(
                    "2) Modelo Completo de Carteira (Substitui Posições)",
                    style = MaterialTheme.typography.titleSmall,
                    color = EmeraldPrimary
                )
                SpreadsheetImporter.STANDARD_COLUMN_SPECS.forEach { spec ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardElevated.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(spec.name, style = MaterialTheme.typography.labelLarge, color = EmeraldPrimary)
                            Text(
                                if (spec.required) "OBRIGATÓRIA" else "OPCIONAL",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (spec.required) AmberGold else ElectricCyan
                            )
                        }
                        Text("Aceita: ${spec.aliases}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("Exemplo: ${spec.example}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text(
                    "Regra de integridade: linhas com Ticker = CAIXA atualizam o saldo em caixa. Cotações ausentes permanecem vazias e nunca são inventadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberGold
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onDownloadTemplate()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = EmeraldOnPrimary)
            ) {
                Text("Modelo Completo CSV")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPositionDialog(
    portfolioType: PortfolioType,
    onDismiss: () -> Unit,
    onConfirm: (String, String, AssetClass, Double, Double, Double?, String, Int?, Boolean) -> Unit
) {
    var ticker by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf(AssetClass.ACOES_BR) }
    var expanded by remember { mutableStateOf(false) }
    var qtyText by remember { mutableStateOf("100") }
    var pmText by remember { mutableStateOf("") }
    var curText by remember { mutableStateOf("") }
    var sector by remember { mutableStateOf("") }
    var dataComText by remember { mutableStateOf("") }
    var isCyclical by remember { mutableStateOf(false) }

    val parsedDataCom = dataComText.trim().toIntOrNull()?.takeIf { it in 1..31 }
    val previewGroup = DataComGroup.fromDay(parsedDataCom, isCyclical)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova Posição • ${portfolioType.label}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = ticker,
                    onValueChange = { ticker = it },
                    label = { Text("Ticker (ex: ITUB4)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_position_ticker_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedClass.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Classe") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        AssetClass.entries.forEach { cls ->
                            DropdownMenuItem(
                                text = { Text(cls.label) },
                                onClick = {
                                    selectedClass = cls
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText, onValueChange = { qtyText = it }, label = { Text("Qtd") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = pmText, onValueChange = { pmText = it }, label = { Text("Preço Médio") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = curText, onValueChange = { curText = it }, label = { Text("Preço Atual (opc.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = dataComText, onValueChange = { dataComText = it }, label = { Text("Dia Data-Com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardElevated.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .border(1.dp, SubtleBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Classificação de Grupo (Dividendo Mensal)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isCyclical,
                            onClick = { isCyclical = false },
                            label = { Text("Sem grupo") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                                selectedLabelColor = ElectricCyan
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("add_position_cyclical_no")
                        )
                        FilterChip(
                            selected = isCyclical,
                            onClick = { isCyclical = true },
                            label = { Text("Mensal (G1–G4)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("add_position_cyclical_yes")
                        )
                    }
                    Text(
                        text = when {
                            !isCyclical -> "Lançado sem classificação de grupo (G1–G4). Só será classificado se for identificado pagamento mensal de dividendos."
                            previewGroup != null -> "Pagador mensal: será classificado no Grupo ${previewGroup.code} (${previewGroup.label.substringAfter("• ")})."
                            else -> "Informe o dia Data-Com (1–31) ou atualize as cotações para vincular ao grupo G1–G4."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCyclical) EmeraldPrimary else ElectricCyan,
                        modifier = Modifier.testTag("add_position_cyclical_helper")
                    )
                }

                OutlinedTextField(
                    value = sector,
                    onValueChange = { sector = it },
                    label = { Text("Setor / Segmento") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = qtyText.replace(",", ".").toDoubleOrNull() ?: 0.0
                    val pm = pmText.replace(",", ".").toDoubleOrNull() ?: 0.0
                    val cur = curText.replace(",", ".").toDoubleOrNull()
                    val dc = dataComText.toIntOrNull()?.takeIf { it in 1..31 }
                    if (ticker.isNotBlank() && qty > 0 && pm > 0) {
                        onConfirm(ticker, name, selectedClass, qty, pm, cur, sector, dc, isCyclical)
                    }
                },
                modifier = Modifier.testTag("add_position_confirm_button")
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordOperationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, OperationType, Double, Double, String) -> Unit
) {
    var ticker by remember { mutableStateOf("") }
    var opType by remember { mutableStateOf(OperationType.PROVENTO) }
    var expanded by remember { mutableStateOf(false) }
    var qtyText by remember { mutableStateOf("1") }
    var priceText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Operação / Provento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = opType.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        OperationType.entries.forEach { t ->
                            DropdownMenuItem(text = { Text(t.label) }, onClick = { opType = t; expanded = false })
                        }
                    }
                }
                OutlinedTextField(value = ticker, onValueChange = { ticker = it }, label = { Text("Ativo / Ticker") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qtyText, onValueChange = { qtyText = it }, label = { Text("Qtd") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = priceText, onValueChange = { priceText = it }, label = { Text("Valor Unit. (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Observação") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = qtyText.replace(",", ".").toDoubleOrNull() ?: 1.0
                val p = priceText.replace(",", ".").toDoubleOrNull() ?: 0.0
                if (p > 0) onConfirm(ticker, opType, q, p, notes)
            }) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun EditCashDialog(
    currentCash: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var text by remember { mutableStateOf("%.2f".format(currentCash)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atualizar Saldo em Caixa (R$)") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Saldo em R$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = {
                text.replace(",", ".").toDoubleOrNull()?.let { onConfirm(it) }
            }) { Text("Atualizar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditOperationDialog(
    operation: com.example.data.local.PortfolioOperationEntity,
    onDismiss: () -> Unit,
    onConfirm: (
        operationId: Long,
        ticker: String,
        opType: OperationType,
        quantity: Double,
        unitPrice: Double,
        dateIso: String,
        notes: String
    ) -> Unit
) {
    val initialType = runCatching { OperationType.valueOf(operation.operationType) }.getOrDefault(OperationType.COMPRA)
    var ticker by remember(operation) { mutableStateOf(operation.ticker) }
    var opType by remember(operation) { mutableStateOf(initialType) }
    var expanded by remember { mutableStateOf(false) }
    var qtyText by remember(operation) { mutableStateOf("%.2f".format(operation.quantity).replace(".", ",")) }
    var priceText by remember(operation) { mutableStateOf("%.2f".format(operation.unitPrice).replace(".", ",")) }
    var dateIso by remember(operation) { mutableStateOf(operation.dateIso) }
    var notes by remember(operation) { mutableStateOf(operation.notes) }

    val parsedQty = qtyText.replace(".", "").replace(",", ".").toDoubleOrNull()
        ?: qtyText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val parsedPrice = priceText.replace(".", "").replace(",", ".").toDoubleOrNull()
        ?: priceText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val isValid = ticker.trim().length >= 3 && parsedQty > 0.0 && parsedPrice > 0.0 && dateIso.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Operação • ${operation.ticker}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Ao salvar, a quantidade, o preço médio e os indicadores da carteira são recalculados automaticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricCyan
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = opType.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        OperationType.entries.forEach { t ->
                            DropdownMenuItem(text = { Text(t.label) }, onClick = { opType = t; expanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = ticker,
                    onValueChange = { ticker = it.uppercase() },
                    label = { Text("Ativo / Ticker") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_operation_ticker_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Quantidade") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_operation_qty_input")
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Valor Unit. (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_operation_price_input")
                    )
                }
                OutlinedTextField(
                    value = dateIso,
                    onValueChange = { dateIso = it },
                    label = { Text("Data (dd/MM/aaaa)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_operation_date_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observação") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        operation.id,
                        ticker.trim().uppercase(),
                        opType,
                        parsedQty,
                        parsedPrice,
                        dateIso.trim(),
                        notes.trim()
                    )
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = EmeraldOnPrimary),
                modifier = Modifier.testTag("edit_operation_save_button")
            ) {
                Text("Salvar Alterações")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}


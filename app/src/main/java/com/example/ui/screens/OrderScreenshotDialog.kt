package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.OperationType
import com.example.domain.model.OrderScreenshotRecognitionState
import com.example.domain.model.PortfolioType
import com.example.domain.model.RecognizedOrderDraft
import com.example.ui.components.formatBrl
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.CardElevated
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class EditableOrderUiRow(
    val id: String,
    val ticker: String,
    val operationType: OperationType,
    val quantityText: String,
    val unitPriceText: String,
    val dateIso: String,
    val timeLabel: String,
    val statusLabel: String,
    val orderTypeLabel: String,
    val brokerLabel: String,
    val isExecuted: Boolean,
    val isIncomplete: Boolean,
    val selected: Boolean,
    val notes: String
) {
    fun parseQuantity(): Double = parseDecimalPtBr(quantityText)
    fun parseUnitPrice(): Double = parseDecimalPtBr(unitPriceText)
    fun computedTotal(): Double = (parseQuantity() * parseUnitPrice()).coerceAtLeast(0.0)
    fun isValidForLaunch(): Boolean = ticker.trim().length >= 4 && parseQuantity() > 0.0 && parseUnitPrice() > 0.0

    fun toDraft(): RecognizedOrderDraft {
        val qty = parseQuantity()
        val price = parseUnitPrice()
        return RecognizedOrderDraft(
            id = id,
            ticker = ticker.trim().uppercase(),
            operationType = operationType,
            quantity = qty,
            unitPrice = price,
            totalValue = qty * price,
            dateIso = dateIso.trim(),
            timeLabel = timeLabel,
            statusLabel = statusLabel,
            orderTypeLabel = orderTypeLabel,
            brokerLabel = brokerLabel,
            isExecuted = isExecuted,
            isIncomplete = !isValidForLaunch(),
            selected = selected,
            notes = notes
        )
    }
}

private fun parseDecimalPtBr(raw: String): Double {
    val cleaned = raw.trim().replace("R$", "").replace(" ", "")
    if (cleaned.isBlank()) return 0.0
    val normalized = if (cleaned.contains(",") && cleaned.contains(".")) {
        cleaned.replace(".", "").replace(",", ".")
    } else {
        cleaned.replace(",", ".")
    }
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
}

private fun formatEditableNumber(value: Double): String {
    if (value <= 0.0) return ""
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(Locale("pt", "BR"), "%.2f", value)
    }
}

@Composable
fun OrderScreenshotReviewDialog(
    state: OrderScreenshotRecognitionState,
    targetPortfolioType: PortfolioType,
    onDismiss: () -> Unit,
    onPickAnotherImage: () -> Unit,
    onConfirmLaunch: (orders: List<RecognizedOrderDraft>, applyToPositions: Boolean) -> Unit
) {
    if (state is OrderScreenshotRecognitionState.Idle) return

    Dialog(
        onDismissRequest = {
            if (state !is OrderScreenshotRecognitionState.Analyzing) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DeepNavySurface,
            border = BorderStroke(1.dp, SubtleBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .testTag("order_screenshot_dialog")
        ) {
            when (state) {
                is OrderScreenshotRecognitionState.Analyzing -> {
                    AnalyzingScreenshotContent(statusLabel = state.statusLabel)
                }

                is OrderScreenshotRecognitionState.Error -> {
                    ErrorScreenshotContent(
                        message = state.message,
                        onPickAnotherImage = onPickAnotherImage,
                        onDismiss = onDismiss
                    )
                }

                is OrderScreenshotRecognitionState.Review -> {
                    ReviewScreenshotOrdersContent(
                        state = state,
                        targetPortfolioType = targetPortfolioType,
                        onPickAnotherImage = onPickAnotherImage,
                        onDismiss = onDismiss,
                        onConfirmLaunch = onConfirmLaunch
                    )
                }

                OrderScreenshotRecognitionState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun AnalyzingScreenshotContent(statusLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            color = EmeraldPrimary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(44.dp)
        )
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Identificando corretora, ativos, compra/venda, quantidade executada, preço médio e data...",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun ErrorScreenshotContent(
    message: String,
    onPickAnotherImage: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Erro na leitura do print",
                tint = CoralRed,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Não foi possível reconhecer o print",
                style = MaterialTheme.typography.titleMedium,
                color = CoralRed
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = CoralContainer.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, CoralRed.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
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
                    .testTag("dismiss_order_screenshot_error_button")
            ) {
                Text("Fechar")
            }
            Button(
                onClick = onPickAnotherImage,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = EmeraldOnPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("retry_order_screenshot_button")
            ) {
                Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Outro Print")
            }
        }
    }
}

@Composable
private fun ReviewScreenshotOrdersContent(
    state: OrderScreenshotRecognitionState.Review,
    targetPortfolioType: PortfolioType,
    onPickAnotherImage: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmLaunch: (orders: List<RecognizedOrderDraft>, applyToPositions: Boolean) -> Unit
) {
    val result = state.result
    val editableRows = remember(state.imageUriString, result.orders) {
        mutableStateListOf<EditableOrderUiRow>().apply {
            addAll(
                result.orders.map { draft ->
                    EditableOrderUiRow(
                        id = draft.id,
                        ticker = draft.ticker,
                        operationType = draft.operationType,
                        quantityText = formatEditableNumber(draft.quantity),
                        unitPriceText = formatEditableNumber(draft.unitPrice),
                        dateIso = draft.dateIso,
                        timeLabel = draft.timeLabel,
                        statusLabel = draft.statusLabel,
                        orderTypeLabel = draft.orderTypeLabel,
                        brokerLabel = draft.brokerLabel.ifBlank { result.brokerHeader },
                        isExecuted = draft.isExecuted,
                        isIncomplete = draft.isIncomplete,
                        selected = draft.selected,
                        notes = draft.notes
                    )
                }
            )
        }
    }
    var applyToPositions by remember { mutableStateOf(true) }

    val selectedValidDrafts = editableRows
        .filter { it.selected && it.isValidForLaunch() }
        .map { it.toDraft() }
    val selectedInvalidCount = editableRows.count { it.selected && !it.isValidForLaunch() }
    val totalSelectedBrl = selectedValidDrafts.sumOf { it.computedTotalValue }
    val todayBr = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.DocumentScanner,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Revisar Ordens do Print",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildString {
                            if (result.brokerHeader.isNotBlank()) {
                                append(result.brokerHeader)
                                append(" • ")
                            }
                            append("Destino: ${targetPortfolioType.label}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_screenshot_review_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Fechar revisão do print")
            }
        }

        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (result.warnings.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AmberGold.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Avisos da Leitura",
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberGold,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        result.warnings.forEach { warn ->
                            Text("• $warn", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardElevated,
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Atualizar posição e preço médio",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Recalcula quantidade e PM automaticamente na ${targetPortfolioType.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = applyToPositions,
                        onCheckedChange = { applyToPositions = it },
                        modifier = Modifier.testTag("screenshot_apply_positions_switch")
                    )
                }
            }

            if (editableRows.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardElevated,
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Nenhuma ordem listada. Adicione uma ordem abaixo ou selecione outro print.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                editableRows.forEachIndexed { idx, row ->
                    RecognizedOrderEditableCard(
                        index = idx,
                        row = row,
                        onUpdate = { updated -> editableRows[idx] = updated },
                        onRemove = { editableRows.removeAt(idx) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        editableRows.add(
                            EditableOrderUiRow(
                                id = java.util.UUID.randomUUID().toString(),
                                ticker = "",
                                operationType = OperationType.COMPRA,
                                quantityText = "",
                                unitPriceText = "",
                                dateIso = todayBr,
                                timeLabel = "",
                                statusLabel = "Manual",
                                orderTypeLabel = "Limite",
                                brokerLabel = result.brokerHeader,
                                isExecuted = true,
                                isIncomplete = false,
                                selected = true,
                                notes = "Adicionada na revisão do print"
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("screenshot_add_manual_row_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Adicionar Ordem")
                }
                OutlinedButton(
                    onClick = onPickAnotherImage,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("screenshot_pick_another_button")
                ) {
                    Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Trocar Print")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = SubtleBorder)
        Spacer(Modifier.height(10.dp))

        if (selectedInvalidCount > 0) {
            Text(
                text = "$selectedInvalidCount ordem(ns) marcada(s) ainda estão sem ticker, quantidade ou preço válidos.",
                style = MaterialTheme.typography.labelSmall,
                color = AmberGold
            )
            Spacer(Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${selectedValidDrafts.size} ordem(ns) pronta(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    text = formatBrl(totalSelectedBrl),
                    style = MonoValueStyle,
                    color = EmeraldPrimary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        onConfirmLaunch(selectedValidDrafts, applyToPositions)
                    },
                    enabled = selectedValidDrafts.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = EmeraldOnPrimary
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_screenshot_orders_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Lançar (${selectedValidDrafts.size})")
                }
            }
        }
    }
}

@Composable
private fun RecognizedOrderEditableCard(
    index: Int,
    row: EditableOrderUiRow,
    onUpdate: (EditableOrderUiRow) -> Unit,
    onRemove: () -> Unit
) {
    val isBuy = row.operationType == OperationType.COMPRA
    val sideColor = if (isBuy) EmeraldPrimary else CoralRed
    val needsAttention = row.isIncomplete || !row.isValidForLaunch() || !row.isExecuted
    val borderColor = when {
        row.selected && row.isValidForLaunch() -> sideColor.copy(alpha = 0.55f)
        needsAttention -> AmberGold.copy(alpha = 0.55f)
        else -> SubtleBorder
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardElevated,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("screenshot_order_card_$index")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = row.selected,
                        onCheckedChange = { checked -> onUpdate(row.copy(selected = checked)) },
                        colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary),
                        modifier = Modifier.testTag("screenshot_order_checkbox_$index")
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (needsAttention) AmberGold.copy(alpha = 0.16f) else EmeraldContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = if (row.isIncomplete && !row.isValidForLaunch()) {
                                "${row.statusLabel} • Incompleta"
                            } else {
                                row.statusLabel
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (needsAttention) AmberGold else EmeraldPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (row.timeLabel.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = row.timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag("screenshot_order_remove_$index")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remover ordem ${row.ticker}",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = row.ticker,
                    onValueChange = { onUpdate(row.copy(ticker = it.uppercase())) },
                    label = { Text("Ativo") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("screenshot_order_ticker_$index")
                )
                FilterChip(
                    selected = isBuy,
                    onClick = { onUpdate(row.copy(operationType = OperationType.COMPRA)) },
                    label = { Text("Compra") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldContainer,
                        selectedLabelColor = EmeraldPrimary
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("screenshot_order_buy_$index")
                )
                FilterChip(
                    selected = !isBuy,
                    onClick = { onUpdate(row.copy(operationType = OperationType.VENDA)) },
                    label = { Text("Venda") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CoralContainer,
                        selectedLabelColor = CoralRed
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("screenshot_order_sell_$index")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = row.quantityText,
                    onValueChange = { onUpdate(row.copy(quantityText = it)) },
                    label = { Text("Qtd Exec.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("screenshot_order_qty_$index")
                )
                OutlinedTextField(
                    value = row.unitPriceText,
                    onValueChange = { onUpdate(row.copy(unitPriceText = it)) },
                    label = { Text("Preço Médio (R$)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("screenshot_order_price_$index")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = row.dateIso,
                    onValueChange = { onUpdate(row.copy(dateIso = it)) },
                    label = { Text("Data") },
                    placeholder = { Text("dd/MM/aaaa") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("screenshot_order_date_$index")
                )
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Total da Ordem", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(
                        text = formatBrl(row.computedTotal()),
                        style = MonoValueStyle,
                        color = sideColor
                    )
                }
            }

            if (row.notes.isNotBlank()) {
                Text(
                    text = row.notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (needsAttention) AmberGold else TextSecondary
                )
            }
        }
    }
}

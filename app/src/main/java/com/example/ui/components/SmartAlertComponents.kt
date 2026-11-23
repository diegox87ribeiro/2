package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AlertNotificationHistoryEntity
import com.example.data.local.AssetSwingAlertEntity
import com.example.data.local.InvestmentGoalEntity
import com.example.domain.model.InvestmentGoalType
import com.example.domain.model.LiveMarketQuote
import com.example.domain.model.PortfolioItemView
import com.example.domain.model.SmartAlertEvaluator
import com.example.domain.model.SwingDirection
import com.example.ui.screens.parseBrlDecimal
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharpSwingAlertsSection(
    alerts: List<AssetSwingAlertEntity>,
    portfolioTickers: List<String>,
    liveQuotesByTicker: Map<String, LiveMarketQuote>,
    onCreateSwingAlert: (ticker: String, thresholdPercent: Double, direction: SwingDirection, notes: String) -> Unit,
    onToggleSwingAlert: (id: Long, enabled: Boolean) -> Unit,
    onResetSwingAlert: (id: Long) -> Unit,
    onDeleteSwingAlert: (id: Long) -> Unit,
    onRequestPermissionIfNeeded: () -> Unit
) {
    var tickerInput by remember { mutableStateOf(portfolioTickers.firstOrNull().orEmpty()) }
    var thresholdInput by remember { mutableStateOf("5,0") }
    var selectedDirection by remember { mutableStateOf(SwingDirection.ANY) }
    var notesInput by remember { mutableStateOf("") }

    val parsedThreshold = remember(thresholdInput) { parseBrlDecimal(thresholdInput) }
    val todayKey = remember { SmartAlertEvaluator.todayDayKey() }
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val dateFmt = remember { SimpleDateFormat("dd/MM HH:mm", ptBr) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FintechCard(borderColor = ElectricCyan.copy(alpha = 0.45f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Novo Alerta de Variação Brusca", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Dispara push local quando o ativo oscilar acima do percentual no pregão.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            if (portfolioTickers.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Ativos da sua carteira:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    portfolioTickers.take(10).forEach { tk ->
                        FilterChip(
                            selected = tickerInput.equals(tk, ignoreCase = true),
                            onClick = { tickerInput = tk },
                            label = { Text(tk, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanContainer,
                                selectedLabelColor = ElectricCyan
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = tickerInput,
                    onValueChange = { raw ->
                        tickerInput = raw.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }
                    },
                    label = { Text("Ticker (ex: PETR4)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f).testTag("swing_ticker_input")
                )
                OutlinedTextField(
                    value = thresholdInput,
                    onValueChange = { thresholdInput = it },
                    label = { Text("Gatilho (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(0.9f).testTag("swing_threshold_input")
                )
            }

            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("3,0", "5,0", "8,0", "10,0").forEach { preset ->
                    FilterChip(
                        selected = thresholdInput == preset,
                        onClick = { thresholdInput = preset },
                        label = { Text("±$preset%", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldContainer,
                            selectedLabelColor = EmeraldPrimary
                        ),
                        modifier = Modifier.testTag("swing_preset_${preset.replace(",", "_")}")
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SwingDirection.entries.forEach { dir ->
                    FilterChip(
                        selected = selectedDirection == dir,
                        onClick = { selectedDirection = dir },
                        label = { Text(dir.label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldContainer,
                            selectedLabelColor = EmeraldPrimary
                        ),
                        modifier = Modifier.testTag("swing_direction_${dir.code.lowercase(Locale.ROOT)}")
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = { Text("Observação (opcional, ex: Checar fato relevante)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("swing_notes_input")
            )

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val threshold = parsedThreshold ?: return@Button
                    onRequestPermissionIfNeeded()
                    onCreateSwingAlert(tickerInput, threshold, selectedDirection, notesInput)
                    notesInput = ""
                },
                enabled = tickerInput.trim().length >= 4 && parsedThreshold != null && parsedThreshold > 0.0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = EmeraldOnPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("create_swing_alert_button")
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Criar Alerta de Variação Brusca", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (alerts.isEmpty()) {
            FintechCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Nenhum alerta de variação brusca cadastrado", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Escolha um ativo acima para monitorar altas ou quedas fortes no pregão.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            alerts.forEach { alert ->
                val dir = SwingDirection.fromCode(alert.direction)
                val live = liveQuotesByTicker[alert.ticker.uppercase(Locale.ROOT)]
                val currentChange = live?.changePercent1d ?: alert.lastChangePercent
                val currentPrice = live?.price ?: alert.lastPriceBrl
                val triggeredToday = alert.lastTriggeredDayKey == todayKey
                val borderColor = when {
                    triggeredToday -> AmberGold.copy(alpha = 0.7f)
                    alert.isEnabled -> SubtleBorder
                    else -> SubtleBorder.copy(alpha = 0.4f)
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (triggeredToday) AmberContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("swing_alert_card_${alert.ticker}")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "${alert.ticker}  ${dir.shortSymbol}${String.format(ptBr, "%.1f%%", alert.thresholdPercent)}",
                                    style = MonoValueStyle.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val badgeText = when {
                                    triggeredToday -> "DISPARADO HOJE"
                                    alert.isEnabled -> "Monitorando"
                                    else -> "Pausado"
                                }
                                val badgeColor = when {
                                    triggeredToday -> AmberGold
                                    alert.isEnabled -> ElectricCyan
                                    else -> TextSecondary
                                }
                                Text(badgeText, style = MaterialTheme.typography.labelSmall, color = badgeColor)
                            }

                            if (currentChange != null) {
                                val pctColor = if (currentChange >= 0.0) EmeraldPrimary else CoralRed
                                val priceSuffix = currentPrice?.let { " • ${formatBrl(it)}" }.orEmpty()
                                val timeSuffix = alert.lastEvaluatedAt?.let { " (${dateFmt.format(Date(it))})" }.orEmpty()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (currentChange >= 0.0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                        contentDescription = null,
                                        tint = pctColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Oscilação atual: ${String.format(ptBr, "%+.2f%%", currentChange)}$priceSuffix$timeSuffix",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = pctColor
                                    )
                                }
                            } else {
                                Text(
                                    "Aguardando próxima atualização de cotação",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            if (alert.notes.isNotBlank()) {
                                Text(
                                    alert.notes,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (triggeredToday) {
                                OutlinedButton(
                                    onClick = { onResetSwingAlert(alert.id) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("reset_swing_alert_${alert.ticker}")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Rearmar", maxLines = 1)
                                }
                            } else {
                                Switch(
                                    checked = alert.isEnabled,
                                    onCheckedChange = { onToggleSwingAlert(alert.id, it) },
                                    modifier = Modifier.testTag("toggle_swing_alert_${alert.ticker}")
                                )
                            }
                            IconButton(
                                onClick = { onDeleteSwingAlert(alert.id) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("delete_swing_alert_${alert.ticker}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.cd_delete_swing_alert),
                                    tint = CoralRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvestmentGoalsSection(
    goals: List<InvestmentGoalEntity>,
    totalEquityBrl: Double,
    estimatedMonthlyDividendsBrl: Double,
    realPositions: List<PortfolioItemView>,
    onCreateGoal: (title: String, goalType: InvestmentGoalType, ticker: String, targetValue: Double, notes: String) -> Unit,
    onToggleGoal: (id: Long, enabled: Boolean) -> Unit,
    onRearmGoal: (id: Long) -> Unit,
    onDeleteGoal: (id: Long) -> Unit,
    onRequestPermissionIfNeeded: () -> Unit
) {
    var selectedType by remember { mutableStateOf(InvestmentGoalType.TOTAL_EQUITY) }
    var titleInput by remember { mutableStateOf("") }
    var tickerInput by remember { mutableStateOf(realPositions.firstOrNull()?.ticker.orEmpty()) }
    var targetValueInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    val parsedTarget = remember(targetValueInput) { parseBrlDecimal(targetValueInput) }
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", ptBr) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FintechCard(borderColor = EmeraldPrimary.copy(alpha = 0.45f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Nova Meta de Investimento", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Receba notificação local assim que sua carteira ou posição atingir o objetivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                InvestmentGoalType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldContainer,
                            selectedLabelColor = EmeraldPrimary
                        ),
                        modifier = Modifier.testTag("goal_type_${type.code.lowercase(Locale.ROOT)}")
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("Nome da meta (ex: Primeiros R$ 100 mil)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("goal_title_input")
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedType.requiresTicker) {
                    OutlinedTextField(
                        value = tickerInput,
                        onValueChange = { raw ->
                            tickerInput = raw.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }
                        },
                        label = { Text("Ticker (ex: HGLG11)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(0.9f).testTag("goal_ticker_input")
                    )
                }
                OutlinedTextField(
                    value = targetValueInput,
                    onValueChange = { targetValueInput = it },
                    label = { Text("Alvo (${selectedType.unitSuffix})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag("goal_target_value_input")
                )
            }

            val currentHint = when (selectedType) {
                InvestmentGoalType.TOTAL_EQUITY -> "Patrimônio atual: ${formatBrl(totalEquityBrl)}"
                InvestmentGoalType.MONTHLY_DIVIDENDS -> "Renda mensal estimada: ${formatBrl(estimatedMonthlyDividendsBrl)}/mês"
                InvestmentGoalType.ASSET_POSITION_VALUE -> {
                    val v = realPositions.filter { it.ticker.equals(tickerInput.trim(), true) }.sumOf { it.totalCurrentBrl }
                    "Posição atual em ${tickerInput.ifBlank { "ativo" }}: ${formatBrl(v)}"
                }
                InvestmentGoalType.ASSET_QUANTITY -> {
                    val q = realPositions.filter { it.ticker.equals(tickerInput.trim(), true) }.sumOf { it.quantity }
                    "Quantidade atual em ${tickerInput.ifBlank { "ativo" }}: ${String.format(ptBr, "%.2f", q)}"
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(currentHint, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)

            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = { Text("Observação (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("goal_notes_input")
            )

            Spacer(Modifier.height(10.dp))
            val tickerValid = !selectedType.requiresTicker || tickerInput.trim().length >= 4
            Button(
                onClick = {
                    val target = parsedTarget ?: return@Button
                    onRequestPermissionIfNeeded()
                    onCreateGoal(titleInput, selectedType, tickerInput, target, notesInput)
                    titleInput = ""
                    targetValueInput = ""
                    notesInput = ""
                },
                enabled = tickerValid && parsedTarget != null && parsedTarget > 0.0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = EmeraldOnPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("create_investment_goal_button")
            ) {
                Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Criar Meta com Notificação Local", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (goals.isEmpty()) {
            FintechCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Flag, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Nenhuma meta cadastrada ainda", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Defina metas de patrimônio, renda mensal de dividendos ou posição em um ativo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            goals.forEach { goal ->
                val type = InvestmentGoalType.fromCode(goal.goalType)
                val progress = SmartAlertEvaluator.computeGoalProgress(
                    goal = goal,
                    totalEquityBrl = totalEquityBrl,
                    estimatedMonthlyDividendsBrl = estimatedMonthlyDividendsBrl,
                    realPositions = realPositions
                )
                val isDone = goal.hasTriggered || progress.isReached

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDone) EmeraldContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        if (isDone) EmeraldPrimary.copy(alpha = 0.7f) else SubtleBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("investment_goal_card_${goal.id}")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isDone) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Meta alcançada",
                                            tint = EmeraldPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                    }
                                    Text(
                                        goal.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isDone) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    buildString {
                                        append(type.label)
                                        if (goal.ticker.isNotBlank()) append(" • ${goal.ticker}")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (goal.hasTriggered) {
                                    OutlinedButton(
                                        onClick = { onRearmGoal(goal.id) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("rearm_goal_${goal.id}")
                                    ) {
                                        Text("Reativar", maxLines = 1)
                                    }
                                } else {
                                    Switch(
                                        checked = goal.isEnabled,
                                        onCheckedChange = { onToggleGoal(goal.id, it) },
                                        modifier = Modifier.testTag("toggle_goal_${goal.id}")
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteGoal(goal.id) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("delete_goal_${goal.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.cd_delete_investment_goal),
                                        tint = CoralRed
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress.progressFraction },
                            color = if (isDone) EmeraldPrimary else ElectricCyan,
                            trackColor = SubtleBorder.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${SmartAlertEvaluator.formatGoalValue(type, progress.currentValue)} / ${SmartAlertEvaluator.formatGoalValue(type, progress.targetValue)}",
                                style = MonoValueStyle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                String.format(ptBr, "%.1f%%", progress.progressPercent),
                                style = MonoValueStyle.copy(fontWeight = FontWeight.Bold),
                                color = if (isDone) EmeraldPrimary else ElectricCyan
                            )
                        }
                        goal.triggeredAt?.let { ts ->
                            Text(
                                "Alcançada e notificada em ${dateFmt.format(Date(ts))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldPrimary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (goal.notes.isNotBlank()) {
                            Text(
                                goal.notes,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertHistorySection(
    history: List<AlertNotificationHistoryEntity>,
    onClearHistory: () -> Unit
) {
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", ptBr) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Alertas Disparados (${history.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary
                )
                OutlinedButton(
                    onClick = onClearHistory,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("clear_alert_history_button")
                ) {
                    Text("Limpar Histórico")
                }
            }
        }

        if (history.isEmpty()) {
            FintechCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Nenhum alerta disparado ainda", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Sempre que um ativo sofrer variação brusca ou uma meta for atingida, o histórico ficará registrado aqui.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            history.forEach { entry ->
                val (badgeLabel, badgeColor) = when (entry.category.uppercase(Locale.ROOT)) {
                    "SWING" -> "VARIAÇÃO BRUSCA" to AmberGold
                    "GOAL" -> "META ALCANÇADA" to EmeraldPrimary
                    else -> "PREÇO ALVO" to ElectricCyan
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alert_history_item_${entry.id}")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(badgeLabel, style = MaterialTheme.typography.labelSmall, color = badgeColor)
                            Text(dateFmt.format(Date(entry.createdAt)), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(entry.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(2.dp))
                        Text(entry.message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (entry.deliveredAsPush) "Notificação local entregue no dispositivo" else "Registrado no app (permissão push desativada)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.deliveredAsPush) EmeraldPrimary else AmberGold
                        )
                    }
                }
            }
        }
    }
}

package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.PriceTargetAlertEntity
import com.example.domain.model.MarketAsset
import com.example.domain.model.PriceAlertCondition
import com.example.domain.model.PriceTargetEvaluator
import com.example.notifications.PriceAlertNotificationHelper
import com.example.ui.screens.parseBrlDecimal
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralRed
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
fun PriceTargetAlertsCard(
    alerts: List<PriceTargetAlertEntity>,
    marketAssets: List<MarketAsset>,
    suggestedTicker: String,
    isCheckingAlerts: Boolean,
    onCreateAlert: (ticker: String, targetPriceBrl: Double, condition: PriceAlertCondition, notes: String) -> Unit,
    onToggleAlert: (id: Long, enabled: Boolean) -> Unit,
    onRearmAlert: (id: Long) -> Unit,
    onDeleteAlert: (id: Long) -> Unit,
    onCheckAlertsNow: () -> Unit,
    modifier: Modifier = Modifier,
    onNotificationPermissionGranted: () -> Unit = {}
) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(PriceAlertNotificationHelper.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val enabledNow = granted || PriceAlertNotificationHelper.hasNotificationPermission(context)
        hasNotificationPermission = enabledNow
        if (enabledNow) {
            onNotificationPermissionGranted()
        }
    }

    var tickerInput by remember { mutableStateOf(suggestedTicker.ifBlank { "VALE3" }) }
    var targetPriceInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var selectedCondition by remember { mutableStateOf(PriceAlertCondition.AT_OR_BELOW) }
    var userLockedCondition by remember { mutableStateOf(false) }

    LaunchedEffect(suggestedTicker) {
        val clean = suggestedTicker.trim().uppercase()
        if (clean.isNotBlank()) {
            tickerInput = clean
        }
    }

    val matchedAsset = remember(tickerInput, marketAssets) {
        marketAssets.firstOrNull { it.ticker.equals(tickerInput.trim(), ignoreCase = true) }
    }
    val referencePrice = matchedAsset?.price
    val parsedTarget = remember(targetPriceInput) { parseBrlDecimal(targetPriceInput) }

    LaunchedEffect(parsedTarget, referencePrice, userLockedCondition) {
        if (!userLockedCondition && parsedTarget != null && parsedTarget > 0.0) {
            selectedCondition = PriceTargetEvaluator.suggestCondition(parsedTarget, referencePrice)
        }
    }

    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val dateFmt = remember { SimpleDateFormat("dd/MM HH:mm", ptBr) }

    FintechCard(
        modifier = modifier.testTag("price_target_alerts_card"),
        borderColor = AmberGold.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Notificações Push de Meta de Preço",
                    tint = AmberGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Alertas Push de Meta de Preço", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Salvo localmente no Room • Reenvio automático offline (15 min / WorkManager)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CoralContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().testTag("notification_permission_banner")
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Permita notificações para receber avisos push quando o preço atingir sua meta.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("request_notification_permission_button")
                    ) {
                        Text("Ativar Push")
                    }
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
                label = { Text("Ticker (ex: VALE3)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f).testTag("alert_ticker_input")
            )
            OutlinedTextField(
                value = targetPriceInput,
                onValueChange = { targetPriceInput = it },
                label = { Text("Meta (R$)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("alert_target_price_input")
            )
        }

        if (referencePrice != null && referencePrice > 0.0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Preço atual de ${matchedAsset.ticker}: ${formatBrl(referencePrice)}",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan
            )
        }

        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PriceAlertCondition.entries.forEach { condition ->
                val isSelected = selectedCondition == condition
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedCondition = condition
                        userLockedCondition = true
                    },
                    label = { Text(condition.label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldContainer,
                        selectedLabelColor = EmeraldPrimary
                    ),
                    modifier = Modifier.testTag(
                        if (condition == PriceAlertCondition.AT_OR_BELOW) "alert_condition_below" else "alert_condition_above"
                    )
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = notesInput,
            onValueChange = { notesInput = it },
            label = { Text("Observação (opcional, ex: Ponto de compra Bazin)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("alert_notes_input")
        )

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val target = parsedTarget ?: return@Button
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    onCreateAlert(tickerInput, target, selectedCondition, notesInput)
                    targetPriceInput = ""
                    notesInput = ""
                    userLockedCondition = false
                },
                enabled = tickerInput.trim().length >= 4 && parsedTarget != null && parsedTarget > 0.0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = EmeraldOnPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("create_price_alert_button")
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Criar Alerta Push")
            }

            OutlinedButton(
                onClick = onCheckAlertsNow,
                enabled = !isCheckingAlerts && alerts.any { it.isEnabled && !it.hasTriggered },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("check_price_alerts_now_button")
            ) {
                if (isCheckingAlerts) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Verificar")
                }
            }
        }

        if (alerts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Metas Definidas (${alerts.size})",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                alerts.forEach { alert ->
                    val cond = PriceAlertCondition.fromCode(alert.condition)
                    val border = when {
                        alert.hasTriggered -> EmeraldPrimary.copy(alpha = 0.65f)
                        alert.isEnabled -> SubtleBorder
                        else -> SubtleBorder.copy(alpha = 0.4f)
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (alert.hasTriggered) EmeraldContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("price_alert_item_${alert.ticker}")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        "${alert.ticker} ${cond.shortSymbol} ${formatBrl(alert.targetPriceBrl)}",
                                        style = MonoValueStyle.copy(fontWeight = FontWeight.Bold),
                                        color = if (alert.hasTriggered) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    val statusText = when {
                                        alert.hasTriggered && alert.pendingNotification -> "PUSH PENDENTE (ROOM)"
                                        alert.hasTriggered -> "META ATINGIDA"
                                        alert.offlineQueuedCheck -> "FILA OFFLINE (ROOM)"
                                        alert.isEnabled -> "Ativo (Room)"
                                        else -> "Pausado"
                                    }
                                    val statusColor = when {
                                        alert.hasTriggered && alert.pendingNotification -> AmberGold
                                        alert.hasTriggered -> EmeraldPrimary
                                        alert.offlineQueuedCheck -> AmberGold
                                        alert.isEnabled -> ElectricCyan
                                        else -> TextSecondary
                                    }
                                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                                }
                                alert.lastEvaluatedPriceBrl?.let { lastPrice ->
                                    val evalTime = alert.triggeredAt ?: alert.lastEvaluatedAt
                                    val timeStr = evalTime?.let { " (${dateFmt.format(Date(it))})" }.orEmpty()
                                    Text(
                                        if (alert.hasTriggered) {
                                            "Push ${if (alert.notificationDelivered) "entregue" else "pendente no Room"} a ${formatBrl(lastPrice)}$timeStr"
                                        } else {
                                            "Última cotação: ${formatBrl(lastPrice)}$timeStr"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (alert.hasTriggered) EmeraldPrimary else TextSecondary
                                    )
                                }
                                if (alert.offlineQueuedCheck && alert.lastCheckStatus.isNotBlank()) {
                                    Text(alert.lastCheckStatus, style = MaterialTheme.typography.labelSmall, color = AmberGold)
                                }
                                if (alert.notes.isNotBlank()) {
                                    Text(alert.notes, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (alert.hasTriggered) {
                                    OutlinedButton(
                                        onClick = { onRearmAlert(alert.id) },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("rearm_price_alert_${alert.ticker}")
                                    ) {
                                        Text("Reativar")
                                    }
                                } else {
                                    Switch(
                                        checked = alert.isEnabled,
                                        onCheckedChange = { onToggleAlert(alert.id, it) },
                                        modifier = Modifier.testTag("toggle_price_alert_${alert.ticker}")
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteAlert(alert.id) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("delete_price_alert_${alert.ticker}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remover alerta de ${alert.ticker}",
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
}

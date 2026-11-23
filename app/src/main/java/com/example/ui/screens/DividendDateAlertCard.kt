package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AppPreferencesState
import com.example.domain.model.DividendDateEventType
import com.example.domain.model.UpcomingDividendDateAlert
import com.example.notifications.DividendDateNotificationHelper
import com.example.ui.components.FintechCard
import com.example.ui.components.formatBrl
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DividendDateAlertCard(
    preferences: AppPreferencesState,
    nextEvents: List<UpcomingDividendDateAlert>,
    onUpdateConfig: (enabled: Boolean, leadDays: Int, notifyDataCom: Boolean, notifyDataEx: Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onSendTestNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingActionAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingActionAfterPermission
        pendingActionAfterPermission = null
        if (granted) {
            pending?.invoke()
        }
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        if (DividendDateNotificationHelper.hasNotificationPermission(context)) {
            action()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingActionAfterPermission = action
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    val dueSoonCount = remember(nextEvents, preferences.dividendAlertLeadDays) {
        nextEvents.count { it.daysUntil <= preferences.dividendAlertLeadDays }
    }
    val leadDayOptions = listOf(1, 2, 3, 5, 7)

    FintechCard(
        modifier = modifier.testTag("dividend_date_alert_card"),
        borderColor = if (preferences.dividendAlertsEnabled) {
            AmberGold.copy(alpha = 0.45f)
        } else {
            SubtleBorder
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (preferences.dividendAlertsEnabled) {
                        Icons.Default.NotificationsActive
                    } else {
                        Icons.Default.NotificationsOff
                    },
                    contentDescription = stringResource(R.string.dividend_alerts_title),
                    tint = if (preferences.dividendAlertsEnabled) AmberGold else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.dividend_alerts_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (preferences.dividendAlertsEnabled) {
                            "Offline • Avisa até ${preferences.dividendAlertLeadDays} dia(s) antes • $dueSoonCount próximo(s)"
                        } else {
                            "Notificações locais pausadas"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dueSoonCount > 0 && preferences.dividendAlertsEnabled) AmberGold else TextSecondary
                    )
                }
            }
            Switch(
                checked = preferences.dividendAlertsEnabled,
                onCheckedChange = { wantEnabled ->
                    if (wantEnabled) {
                        runWithNotificationPermission {
                            onUpdateConfig(
                                true,
                                preferences.dividendAlertLeadDays,
                                preferences.dividendAlertDataCom,
                                preferences.dividendAlertDataEx
                            )
                        }
                    } else {
                        onUpdateConfig(
                            false,
                            preferences.dividendAlertLeadDays,
                            preferences.dividendAlertDataCom,
                            preferences.dividendAlertDataEx
                        )
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = EmeraldOnPrimary,
                    checkedTrackColor = EmeraldPrimary
                ),
                modifier = Modifier.testTag("dividend_date_alerts_switch")
            )
        }

        AnimatedVisibility(visible = preferences.dividendAlertsEnabled) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Avisar sobre:",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = preferences.dividendAlertDataCom,
                        onClick = {
                            val nextCom = !preferences.dividendAlertDataCom
                            if (!nextCom && !preferences.dividendAlertDataEx) return@FilterChip
                            onUpdateConfig(true, preferences.dividendAlertLeadDays, nextCom, preferences.dividendAlertDataEx)
                        },
                        label = { Text("Data-Com") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldContainer,
                            selectedLabelColor = EmeraldPrimary
                        ),
                        modifier = Modifier.testTag("dividend_alert_toggle_datacom")
                    )
                    FilterChip(
                        selected = preferences.dividendAlertDataEx,
                        onClick = {
                            val nextEx = !preferences.dividendAlertDataEx
                            if (!nextEx && !preferences.dividendAlertDataCom) return@FilterChip
                            onUpdateConfig(true, preferences.dividendAlertLeadDays, preferences.dividendAlertDataCom, nextEx)
                        },
                        label = { Text("Data-Ex") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.16f),
                            selectedLabelColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("dividend_alert_toggle_dataex")
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Antecedência do alerta:",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    leadDayOptions.forEach { days ->
                        FilterChip(
                            selected = preferences.dividendAlertLeadDays == days,
                            onClick = {
                                onUpdateConfig(
                                    true,
                                    days,
                                    preferences.dividendAlertDataCom,
                                    preferences.dividendAlertDataEx
                                )
                            },
                            label = { Text(if (days == 1) "1 dia" else "$days dias") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberGold.copy(alpha = 0.18f),
                                selectedLabelColor = AmberGold
                            ),
                            modifier = Modifier.testTag("dividend_alert_lead_$days")
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = SubtleBorder.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Próximas datas na Carteira Real",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))

                if (nextEvents.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsPaused,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Importe ou cadastre ativos com Data-Com na Carteira Real para ativar os lembretes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        nextEvents.take(4).forEach { event ->
                            UpcomingDividendDateRow(
                                event = event,
                                isWithinLeadWindow = event.daysUntil <= preferences.dividendAlertLeadDays
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { runWithNotificationPermission(onCheckNow) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = EmeraldOnPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("dividend_alert_check_now_button")
                    ) {
                        Text(stringResource(R.string.dividend_alerts_check_now))
                    }
                    OutlinedButton(
                        onClick = { runWithNotificationPermission(onSendTestNotification) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("dividend_alert_test_button")
                    ) {
                        Text(stringResource(R.string.dividend_alerts_test_push))
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingDividendDateRow(
    event: UpcomingDividendDateAlert,
    isWithinLeadWindow: Boolean
) {
    val typeColor = if (event.eventType == DividendDateEventType.DATA_COM) EmeraldPrimary else ElectricCyan
    val countdownLabel = when (event.daysUntil) {
        0 -> "HOJE"
        1 -> "AMANHÃ"
        else -> "em ${event.daysUntil}d"
    }
    val countdownColor = when {
        event.daysUntil <= 1 -> AmberGold
        isWithinLeadWindow -> EmeraldPrimary
        else -> TextSecondary
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isWithinLeadWindow) typeColor.copy(alpha = 0.10f) else SubtleBorder.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, if (isWithinLeadWindow) typeColor.copy(alpha = 0.35f) else SubtleBorder.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("upcoming_dividend_event_${event.ticker}_${event.eventType.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = event.ticker,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = typeColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = event.eventType.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                val projTxt = if (event.projectedDividendBrl > 0.0) {
                    " • Proj. ${formatBrl(event.projectedDividendBrl)}"
                } else {
                    ""
                }
                Text(
                    text = "${event.eventDateFormatted}$projTxt",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = countdownColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = countdownLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = countdownColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

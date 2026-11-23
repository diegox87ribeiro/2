package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.DividendMonitorCalculator
import com.example.domain.model.InvestmentGoalType
import com.example.domain.model.PriceAlertCondition
import com.example.domain.model.SwingDirection
import com.example.notifications.SmartAlertNotificationHelper
import com.example.ui.components.AlertHistorySection
import com.example.ui.components.FintechCard
import com.example.ui.components.InvestmentGoalsSection
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.PriceTargetAlertsCard
import com.example.ui.components.SharpSwingAlertsSection
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.InvestDashboardState
import com.example.ui.viewmodel.SmartAlertsUiState

@Composable
fun AlertsAndGoalsScreen(
    state: InvestDashboardState,
    smartState: SmartAlertsUiState,
    onCreateSwingAlert: (ticker: String, thresholdPercent: Double, direction: SwingDirection, notes: String) -> Unit,
    onToggleSwingAlert: (id: Long, enabled: Boolean) -> Unit,
    onResetSwingAlert: (id: Long) -> Unit,
    onDeleteSwingAlert: (id: Long) -> Unit,
    onCreateInvestmentGoal: (title: String, goalType: InvestmentGoalType, ticker: String, targetValue: Double, notes: String) -> Unit,
    onToggleInvestmentGoal: (id: Long, enabled: Boolean) -> Unit,
    onRearmInvestmentGoal: (id: Long) -> Unit,
    onDeleteInvestmentGoal: (id: Long) -> Unit,
    onCreatePriceAlert: (ticker: String, targetPriceBrl: Double, condition: PriceAlertCondition, notes: String) -> Unit,
    onTogglePriceAlert: (id: Long, enabled: Boolean) -> Unit,
    onRearmPriceAlert: (id: Long) -> Unit,
    onDeletePriceAlert: (id: Long) -> Unit,
    onCheckPriceAlertsNow: () -> Unit,
    onCheckAllSmartAlertsNow: () -> Unit,
    onSendTestNotification: () -> Unit,
    onClearAlertHistory: () -> Unit,
    onNotificationPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(SmartAlertNotificationHelper.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val enabledNow = granted || SmartAlertNotificationHelper.hasNotificationPermission(context)
        hasPermission = enabledNow
        if (enabledNow) {
            onNotificationPermissionGranted()
        }
    }

    fun requestPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Variações Bruscas, 1 = Metas, 2 = Histórico

    val portfolioTickers = remember(state.realPositions) {
        state.realPositions.map { it.ticker.trim().uppercase() }.filter { it.isNotBlank() }.distinct()
    }
    val dividendSummary = remember(state.realPositions, state.marketAssets, state.operations) {
        DividendMonitorCalculator.compute(
            realPositions = state.realPositions,
            marketAssets = state.marketAssets,
            operations = state.operations
        )
    }
    val estimatedMonthlyDividends = maxOf(
        dividendSummary.projectedMonthlyBrl,
        dividendSummary.receivedMonthlyBrl
    )

    val activeSwingsCount = smartState.swingAlerts.count { it.isEnabled }
    val activeGoalsCount = smartState.investmentGoals.count { it.isEnabled && !it.hasTriggered } +
        state.priceTargetAlerts.count { it.isEnabled && !it.hasTriggered }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("alerts_and_goals_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FintechCard(borderColor = EmeraldPrimary.copy(alpha = 0.45f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Central de Notificações Locais", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Monitoramento automático em segundo plano (WorkManager 15 min) + checagem imediata para variações bruscas e metas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiMetricCard(
                        title = "Variações Ativas",
                        value = "$activeSwingsCount",
                        subtitle = "${smartState.swingAlerts.size} cadastrada(s)",
                        icon = Icons.Default.Bolt,
                        accentColor = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Metas Ativas",
                        value = "$activeGoalsCount",
                        subtitle = "${smartState.investmentGoals.size + state.priceTargetAlerts.size} total",
                        icon = Icons.Default.Flag,
                        accentColor = EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            requestPermissionIfNeeded()
                            onCheckAllSmartAlertsNow()
                        },
                        enabled = !smartState.isCheckingSmartAlerts,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = EmeraldOnPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("check_all_smart_alerts_button")
                    ) {
                        if (smartState.isCheckingSmartAlerts) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = EmeraldOnPrimary
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.smart_alerts_check_all_cta),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            requestPermissionIfNeeded()
                            onSendTestNotification()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("send_test_smart_alert_button")
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.smart_alerts_test_cta),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CoralContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth().testTag("smart_alerts_permission_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Permita notificações no Android para receber alertas locais de variações bruscas e metas alcançadas.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("smart_alerts_request_permission_button")
                        ) {
                            Text("Ativar")
                        }
                    }
                }
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = EmeraldContainer,
                        activeContentColor = EmeraldPrimary
                    ),
                    modifier = Modifier.testTag("smart_alerts_tab_swings")
                ) {
                    Text("Variações", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = { Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = EmeraldContainer,
                        activeContentColor = EmeraldPrimary
                    ),
                    modifier = Modifier.testTag("smart_alerts_tab_goals")
                ) {
                    Text("Metas", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SegmentedButton(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = EmeraldContainer,
                        activeContentColor = EmeraldPrimary
                    ),
                    modifier = Modifier.testTag("smart_alerts_tab_history")
                ) {
                    Text("Histórico", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        when (selectedTab) {
            0 -> {
                item {
                    SharpSwingAlertsSection(
                        alerts = smartState.swingAlerts,
                        portfolioTickers = portfolioTickers,
                        liveQuotesByTicker = state.liveQuotesByTicker,
                        onCreateSwingAlert = onCreateSwingAlert,
                        onToggleSwingAlert = onToggleSwingAlert,
                        onResetSwingAlert = onResetSwingAlert,
                        onDeleteSwingAlert = onDeleteSwingAlert,
                        onRequestPermissionIfNeeded = ::requestPermissionIfNeeded
                    )
                }
            }

            1 -> {
                item {
                    InvestmentGoalsSection(
                        goals = smartState.investmentGoals,
                        totalEquityBrl = state.realHhi.totalEquityBrl,
                        estimatedMonthlyDividendsBrl = estimatedMonthlyDividends,
                        realPositions = state.realPositions,
                        onCreateGoal = onCreateInvestmentGoal,
                        onToggleGoal = onToggleInvestmentGoal,
                        onRearmGoal = onRearmInvestmentGoal,
                        onDeleteGoal = onDeleteInvestmentGoal,
                        onRequestPermissionIfNeeded = ::requestPermissionIfNeeded
                    )
                }
                item {
                    PriceTargetAlertsCard(
                        alerts = state.priceTargetAlerts,
                        marketAssets = state.marketAssets,
                        suggestedTicker = portfolioTickers.firstOrNull() ?: "VALE3",
                        isCheckingAlerts = state.isCheckingPriceAlerts,
                        onCreateAlert = onCreatePriceAlert,
                        onToggleAlert = onTogglePriceAlert,
                        onRearmAlert = onRearmPriceAlert,
                        onDeleteAlert = onDeletePriceAlert,
                        onCheckAlertsNow = onCheckPriceAlertsNow,
                        onNotificationPermissionGranted = onNotificationPermissionGranted
                    )
                }
            }

            else -> {
                item {
                    AlertHistorySection(
                        history = smartState.history,
                        onClearHistory = onClearAlertHistory
                    )
                }
            }
        }
    }
}

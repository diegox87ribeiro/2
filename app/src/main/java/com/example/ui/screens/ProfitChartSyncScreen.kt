package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.OperationType
import com.example.domain.model.ProfitChartConnectionState
import com.example.ui.components.DataDisclaimerBanner
import com.example.ui.components.FintechCard
import com.example.ui.components.formatBrl
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.InvestDashboardState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfitChartSyncScreen(
    state: InvestDashboardState,
    onConnectAndSync: (endpointUrl: String, authToken: String, autoSync: Boolean, applyToPositions: Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onImportExportedFile: (android.net.Uri, applyToPositions: Boolean) -> Unit,
    onDisconnect: () -> Unit
) {
    val prefs = state.preferences
    var endpointUrl by remember(prefs.profitEndpointUrl) { mutableStateOf(prefs.profitEndpointUrl) }
    var authToken by remember(prefs.profitAuthToken) { mutableStateOf(prefs.profitAuthToken) }
    var autoSyncEnabled by remember(prefs.profitAutoSyncEnabled) { mutableStateOf(prefs.profitAutoSyncEnabled) }
    var applyToPositions by remember(prefs.profitApplyToPositions) { mutableStateOf(prefs.profitApplyToPositions) }
    var showGuideDialog by remember { mutableStateOf(false) }

    val profitFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportExportedFile(uri, applyToPositions)
        }
    }

    val profitOperations = remember(state.operations) {
        state.operations.filter {
            it.externalRefId.startsWith("PROFIT:") ||
                it.notes.contains("ProfitChart", ignoreCase = true)
        }
    }

    val fmtDate = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")) }
    val isSyncing = state.profitConnectionState is ProfitChartConnectionState.Syncing

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profitchart_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DataDisclaimerBanner(
                text = "Conector ProfitChart (Nelogica): recebe atualizações de operações executadas via Bridge/Webhook HTTP/HTTPS (ProfitDLL/RTD) ou importação direta da Lista de Ordens (.CSV / .XLSX), com deduplicação automática."
            )
        }

        item {
            val (badgeText, badgeColor) = when (state.profitConnectionState) {
                is ProfitChartConnectionState.Connected -> "CONECTADO" to EmeraldPrimary
                is ProfitChartConnectionState.Syncing -> "SINCRONIZANDO" to ElectricCyan
                is ProfitChartConnectionState.Error -> "FALHA DE CONEXÃO" to CoralRed
                is ProfitChartConnectionState.Disconnected ->
                    if (prefs.profitEndpointUrl.isNotBlank()) "PRONTO P/ SINCRONIZAR" to AmberGold
                    else "DESCONECTADO" to TextSecondary
            }

            FintechCard(borderColor = badgeColor.copy(alpha = 0.45f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (state.profitConnectionState is ProfitChartConnectionState.Connected) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.Link
                            },
                            contentDescription = "Status ProfitChart",
                            tint = badgeColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Conexão ProfitChart", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (prefs.profitLastSyncEpochMs > 0L) {
                                    "Última sync: ${fmtDate.format(Date(prefs.profitLastSyncEpochMs))}"
                                } else {
                                    "Nenhuma sincronização realizada"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = badgeColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                when (val conn = state.profitConnectionState) {
                    is ProfitChartConnectionState.Connected -> {
                        Spacer(Modifier.height(8.dp))
                        Text(conn.summary.message, style = MaterialTheme.typography.bodySmall, color = EmeraldPrimary)
                    }
                    is ProfitChartConnectionState.Error -> {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CoralContainer.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, CoralRed.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                conn.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    else -> if (prefs.profitLastStatusMessage.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(prefs.profitLastStatusMessage, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
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
                    Text("Bridge / Webhook do ProfitChart", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showGuideDialog = true }, modifier = Modifier.testTag("profitchart_guide_button")) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Como configurar")
                    }
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                    label = { Text("URL do ProfitChart Bridge (JSON ou CSV)") },
                    placeholder = { Text("http://192.168.1.10:8080/profit/executions") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profitchart_endpoint_input")
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = authToken,
                    onValueChange = { authToken = it },
                    label = { Text("Token de Autenticação (opcional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profitchart_token_input")
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Atualização automática (20s)", style = MaterialTheme.typography.bodyMedium)
                        Text("Busca novas execuções continuamente enquanto o app estiver aberto", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = { autoSyncEnabled = it },
                        modifier = Modifier.testTag("profitchart_autosync_switch")
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Atualizar posições e PM na Carteira Real", style = MaterialTheme.typography.bodyMedium)
                        Text("Recalcula quantidade e preço médio automaticamente a cada execução", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = applyToPositions,
                        onCheckedChange = { applyToPositions = it },
                        modifier = Modifier.testTag("profitchart_apply_positions_switch")
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onConnectAndSync(endpointUrl, authToken, autoSyncEnabled, applyToPositions)
                        },
                        enabled = !isSyncing && endpointUrl.trim().isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = EmeraldOnPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("profitchart_connect_sync_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = EmeraldOnPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("Sincronizando...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Conectar e Sincronizar")
                        }
                    }

                    if (state.profitConnectionState is ProfitChartConnectionState.Connected || prefs.profitAutoSyncEnabled) {
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("profitchart_disconnect_button")
                        ) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Desconectar")
                        }
                    }
                }

                if (prefs.profitEndpointUrl.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("profitchart_sync_now_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Buscar Execuções Agora")
                    }
                }
            }
        }

        item {
            FintechCard(borderColor = ElectricCyan.copy(alpha = 0.35f)) {
                Text("Importação Direta de Arquivo ProfitChart", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Exporte sua Lista de Ordens ou Relatório de Operações no ProfitChart (.CSV / .XLSX / .TXT) e importe aqui. Apenas ordens executadas serão importadas, sem duplicar ordens anteriores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        profitFilePicker.launch(
                            arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "*/*"
                            )
                        )
                    },
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("profitchart_import_file_button")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Importar Operações do ProfitChart (.CSV / .XLSX)")
                }
            }
        }

        item {
            Text(
                "Operações Executadas Sincronizadas (${profitOperations.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (profitOperations.isEmpty()) {
            item {
                FintechCard {
                    Text(
                        "Nenhuma operação do ProfitChart sincronizada ainda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(profitOperations, key = { it.id }) { op ->
                val isBuy = op.operationType == OperationType.COMPRA.name
                val sideColor = if (isBuy) EmeraldPrimary else CoralRed
                FintechCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(op.ticker, style = MaterialTheme.typography.titleMedium, color = sideColor)
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = sideColor.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        if (isBuy) "COMPRA" else "VENDA",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = sideColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                "${"%.2f".format(op.quantity)} x ${formatBrl(op.unitPrice)} • ${op.dateIso}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(op.notes, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Text(formatBrl(op.totalValue), style = MonoValueStyle, color = sideColor)
                    }
                }
            }
        }
    }

    if (showGuideDialog) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            title = { Text("Como integrar o ProfitChart") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "1. Bridge Online / Rede Local (ProfitDLL ou RTD): exponha um endpoint GET retornando JSON ou CSV das execuções. Exemplo JSON:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        color = EmeraldContainer.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = """
                                {"executions":[{
                                  "orderId":"ORD-9812",
                                  "ticker":"VALE3",
                                  "side":"COMPRA",
                                  "status":"EXECUTADA",
                                  "executedQty":100,
                                  "avgPrice":65.40,
                                  "executedAt":"19/11/2026 14:15",
                                  "broker":"XP"
                                }]}
                            """.trimIndent(),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Text(
                        "2. Sem servidor: no ProfitChart desktop, abra Roteamento > Lista de Ordens (ou Relatório de Performance > Operações), clique em Exportar CSV/XLSX e use o botão Importar Operações acima.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuideDialog = false }) { Text("Entendi") }
            }
        )
    }
}

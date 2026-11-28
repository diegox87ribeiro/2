package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Edit
import com.example.data.repository.OrderScreenshotRecognizer
import com.example.domain.model.AssetDetailCalculator
import com.example.domain.model.AssetDetailTab
import com.example.ui.components.PremiumPortfolioAssetCard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.importexport.ImportParseResult
import com.example.data.importexport.SpreadsheetImporter
import com.example.domain.model.AssetClass
import com.example.domain.model.OperationType
import com.example.domain.model.OrderScreenshotRecognitionState
import com.example.domain.model.PortfolioItemView
import com.example.domain.model.PortfolioType
import com.example.domain.model.RecognizedOrderDraft
import com.example.ui.components.DataDisclaimerBanner
import com.example.ui.components.FintechCard
import com.example.ui.components.formatBrl
import com.example.ui.components.formatPct
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.InvestDashboardState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PortfolioScreen(
    state: InvestDashboardState,
    selectedType: PortfolioType,
    importFeedback: ImportParseResult?,
    pendingImportPreview: ImportParseResult?,
    onSelectType: (PortfolioType) -> Unit,
    onImportUri: (android.net.Uri) -> Unit,
    onConfirmPendingImport: () -> Unit,
    onDismissPendingImport: () -> Unit,
    onDismissImportFeedback: () -> Unit,
    onBuildTemplateCsv: () -> String,
    onBuildQuickUpdateCsv: () -> String = { SpreadsheetImporter.generateQuickUpdateCsvTemplate() },
    onBuildPortfolioCsv: (PortfolioType) -> String,
    onNotify: (String) -> Unit,
    onLoadDemoReal: () -> Unit,
    onClearPortfolio: (PortfolioType) -> Unit,
    onAddManualPosition: (PortfolioType, String, String, AssetClass, Double, Double, Double?, String, Int?, Boolean) -> Unit,
    onRecordOperation: (PortfolioType, String, OperationType, Double, Double, String) -> Unit,
    onDeletePosition: (Long) -> Unit,
    onUpdateCash: (PortfolioType, Double) -> Unit,
    onUpdatePosition: (
        Long,
        String,
        String,
        AssetClass,
        Double,
        Double,
        Double?,
        String,
        Int?,
        String,
        Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
    onOpenProfitChart: () -> Unit = {},
    onRefreshPortfolioLive: (PortfolioType) -> Unit = {},
    orderScreenshotState: OrderScreenshotRecognitionState = OrderScreenshotRecognitionState.Idle,
    onRecognizeOrderScreenshot: (android.net.Uri) -> Unit = {},
    onConfirmScreenshotOrders: (PortfolioType, List<RecognizedOrderDraft>, Boolean) -> Unit = { _, _, _ -> },
    onDismissScreenshotReview: () -> Unit = {},
    onUpdateOperation: (Long, String, OperationType, Double, Double, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteOperation: (Long) -> Unit = {},
    onRestoreAuditEntry: (Long) -> Unit = {},
    onOpenAssetDetail: (String, AssetDetailTab) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var showAddPositionDialog by remember { mutableStateOf(false) }
    var showOperationDialog by remember { mutableStateOf(false) }
    var showCashDialog by remember { mutableStateOf(false) }
    var showSpecDialog by remember { mutableStateOf(false) }
    var showAuditDialog by remember { mutableStateOf(false) }
    var selectedPositionForEdit by remember { mutableStateOf<PortfolioItemView?>(null) }
    var pendingDeletePosition by remember { mutableStateOf<PortfolioItemView?>(null) }
    var selectedOperationForEdit by remember { mutableStateOf<com.example.data.local.PortfolioOperationEntity?>(null) }
    var pendingDeleteOperation by remember { mutableStateOf<com.example.data.local.PortfolioOperationEntity?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportUri(uri)
    }

    val orderDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onRecognizeOrderScreenshot(uri)
        }
    }

    val templateSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(onBuildTemplateCsv().toByteArray(Charsets.UTF_8))
                }
                onNotify("Modelo completo de carteira CSV salvo com sucesso.")
            }.onFailure {
                onNotify("Erro ao salvar modelo CSV: ${it.localizedMessage ?: "falha de escrita"}")
            }
        }
    }

    val quickUpdateTemplateSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(onBuildQuickUpdateCsv().toByteArray(Charsets.UTF_8))
                }
                onNotify("Modelo de atualização (Ativo, Preço, Data, Dividendos) salvo com sucesso.")
            }.onFailure {
                onNotify("Erro ao salvar modelo de atualização: ${it.localizedMessage ?: "falha de escrita"}")
            }
        }
    }

    val portfolioExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(onBuildPortfolioCsv(selectedType).toByteArray(Charsets.UTF_8))
                }
                onNotify("Carteira (${selectedType.label}) exportada em CSV padrão.")
            }.onFailure {
                onNotify("Erro ao exportar carteira: ${it.localizedMessage ?: "falha de escrita"}")
            }
        }
    }

    val activePositions = if (selectedType == PortfolioType.REAL) state.realPositions else state.simPositions
    val activeHhi = if (selectedType == PortfolioType.REAL) state.realHhi else state.simHhi
    val activeOps = state.operations.filter { it.portfolioType == selectedType.name }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (selectedType == PortfolioType.REAL) 0 else 1) {
            Tab(
                selected = selectedType == PortfolioType.REAL,
                onClick = { onSelectType(PortfolioType.REAL) },
                text = { Text("Carteira Real (${state.realPositions.size})") },
                modifier = Modifier.testTag("tab_real_portfolio")
            )
            Tab(
                selected = selectedType == PortfolioType.SIMULATION,
                onClick = { onSelectType(PortfolioType.SIMULATION) },
                text = { Text("Simulação (${state.simPositions.size})") },
                modifier = Modifier.testTag("tab_sim_portfolio")
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("portfolio_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                DataDisclaimerBanner(
                    text = if (selectedType == PortfolioType.REAL) {
                        "Carteira Real separada da Simulação. A importação XLSX/CSV mais recente define a referência oficial. Posições sem cotação na planilha não têm preços inventados."
                    } else {
                        "Carteira de Simulação isolada da Carteira Real para testar alocações, aportes e rebalanceamentos."
                    }
                )
            }

            if (importFeedback != null) {
                item {
                    FintechCard(borderColor = ElectricCyan.copy(alpha = 0.5f)) {
                        Text("Resultado da Importação: ${importFeedback.fileName}", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                        val divTxt = if (importFeedback.dividends.isNotEmpty()) " e ${importFeedback.dividends.size} provento(s)" else ""
                        val summaryText = if (importFeedback.positions.isEmpty() && importFeedback.quickUpdates.isNotEmpty()) {
                            "${importFeedback.quickUpdates.size} atualização(ões) de ativo/preço/data$divTxt processadas (${importFeedback.fileType})."
                        } else {
                            "${importFeedback.positions.size} posições$divTxt importadas (${importFeedback.fileType})."
                        }
                        Text(summaryText, style = MaterialTheme.typography.bodySmall)
                        importFeedback.warnings.take(4).forEach { w ->
                            Text("• $w", style = MaterialTheme.typography.bodySmall, color = AmberGold)
                        }
                        TextButton(onClick = onDismissImportFeedback) { Text("Dispensar") }
                    }
                }
            }

            item {
                FintechCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Patrimônio (${selectedType.label})", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                            Text(formatBrl(activeHhi.totalEquityBrl), style = MonoValueStyle.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize))
                            Text("Investido: ${formatBrl(activeHhi.totalInvestedBrl)} • Caixa: ${formatBrl(activeHhi.cashBrl)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        TextButton(onClick = { showCashDialog = true }, modifier = Modifier.testTag("edit_cash_button")) {
                            Text("Editar Caixa")
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedType == PortfolioType.REAL) {
                            Button(
                                onClick = {
                                    filePicker.launch(
                                        arrayOf(
                                            "text/csv",
                                            "text/comma-separated-values",
                                            "text/plain",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "*/*"
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f).testTag("import_spreadsheet_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = EmeraldOnPrimary)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Importar XLSX/CSV")
                            }
                        }
                        OutlinedButton(
                            onClick = { showAddPositionDialog = true },
                            modifier = Modifier.weight(1f).testTag("add_position_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Nova Posição")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            orderDocumentPicker.launch(
                                OrderScreenshotRecognizer.PICKER_MIME_TYPES
                            )
                        },
                        enabled = orderScreenshotState !is OrderScreenshotRecognitionState.Analyzing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = EmeraldOnPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("recognize_order_screenshot_button")
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ler Nota (PDF) ou Print (IA)")
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { templateSaver.launch("invest_ai_modelo_carteira.csv") },
                            modifier = Modifier.weight(1f).testTag("download_csv_template_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Modelo Carteira")
                        }
                        OutlinedButton(
                            onClick = {
                                quickUpdateTemplateSaver.launch("invest_ai_atualizacao_ativo_preco_data_dividendos.csv")
                            },
                            modifier = Modifier.weight(1f).testTag("download_quick_update_template_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Modelo Atualização")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showSpecDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("spreadsheet_spec_button")
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ver Modelos e Padrão de Colunas (Carteira / Atualização)")
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showOperationDialog = true },
                            modifier = Modifier.weight(1f).testTag("record_operation_button")
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Operação / Provento")
                        }
                        if (activePositions.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val prefix = if (selectedType == PortfolioType.REAL) "real" else "simulacao"
                                    portfolioExporter.launch("invest_ai_carteira_$prefix.csv")
                                },
                                modifier = Modifier.weight(1f).testTag("export_portfolio_csv_button")
                            ) {
                                Text("Exportar CSV")
                            }
                        } else if (selectedType == PortfolioType.REAL) {
                            OutlinedButton(
                                onClick = onLoadDemoReal,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Demo Fictícia")
                            }
                        }
                    }

                    if (activePositions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onRefreshPortfolioLive(selectedType) },
                            enabled = !state.isLiveBatchRefreshing,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = EmeraldOnPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("portfolio_refresh_live_button")
                        ) {
                            if (state.isLiveBatchRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = EmeraldOnPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("Atualizando cotações da carteira...")
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Atualizar Cotações da Carteira (Tempo Real)")
                            }
                        }
                    }

                    if (selectedType == PortfolioType.REAL) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenProfitChart,
                            modifier = Modifier.fillMaxWidth().testTag("open_profitchart_button")
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Conectar ProfitChart (Operações Executadas)")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showAuditDialog = true },
                            modifier = Modifier.testTag("portfolio_open_audit_button")
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Auditoria / Restaurar (${state.financialAuditLogs.size})", color = ElectricCyan)
                        }
                        if (activePositions.isNotEmpty()) {
                            TextButton(onClick = { onClearPortfolio(selectedType) }) {
                                Text("Limpar ${selectedType.label}", color = CoralRed)
                            }
                        }
                    }
                }
            }

            if (activePositions.isEmpty()) {
                item {
                    FintechCard {
                        Text(
                            "Nenhuma posição cadastrada em ${selectedType.label}.",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Formato CSV/XLSX aceito: colunas Ticker, Nome, Classe, Quantidade, Preço Médio, Preço Atual (opc.), Setor, Data-Com, Dividendo Valor, Data Provento, Tipo Provento e linha CAIXA.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(activePositions, key = { it.id }) { pos ->
                    val catalogMatch = state.marketAssets.firstOrNull {
                        it.ticker.equals(pos.ticker, ignoreCase = true)
                    }
                    val liveMatch = state.liveQuotesByTicker[pos.ticker.trim().uppercase(Locale.ROOT)]
                    val indicators = remember(pos, catalogMatch, liveMatch, state.dividendEvents) {
                        AssetDetailCalculator.computeCardIndicators(
                            pos = pos,
                            catalogAsset = catalogMatch,
                            liveQuote = liveMatch,
                            dividendEvents = state.dividendEvents
                        )
                    }
                    PremiumPortfolioAssetCard(
                        pos = pos,
                        indicators = indicators,
                        onClickTicker = { onOpenAssetDetail(pos.ticker, AssetDetailTab.ORDERS) },
                        onOpenMetricDetail = { tab -> onOpenAssetDetail(pos.ticker, tab) },
                        onOpenCadastroAndEdit = { selectedPositionForEdit = pos },
                        onDelete = { pendingDeletePosition = pos }
                    )
                }
            }

            if (activeOps.isNotEmpty()) {
                item {
                    FintechCard {
                        Text("Operações e Proventos Recentes (${activeOps.size})", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Edite ou exclua qualquer lançamento com recálculo automático de quantidade e preço médio.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        activeOps.take(15).forEach { op ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${op.ticker} • ${op.operationType}", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${op.dateIso} • ${"%.2f".format(op.quantity)} × ${formatBrl(op.unitPrice)}" +
                                            if (op.notes.isNotBlank()) " • ${op.notes}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Text(formatBrl(op.totalValue), style = MonoValueStyle, color = EmeraldPrimary)
                                IconButton(
                                    onClick = { selectedOperationForEdit = op },
                                    modifier = Modifier.testTag("edit_operation_button_${op.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Editar operação ${op.ticker}",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { pendingDeleteOperation = op },
                                    modifier = Modifier.testTag("delete_operation_button_${op.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Excluir operação ${op.ticker}",
                                        tint = CoralRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedType == PortfolioType.REAL && state.importHistory.isNotEmpty()) {
                item {
                    FintechCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Histórico de Importações", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
                        state.importHistory.forEach { h ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        h.fileName + if (h.isActiveRealReference) " [REFERÊNCIA ATUAL]" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (h.isActiveRealReference) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "${h.fileType} • ${h.recordCount} ativos • ${fmt.format(Date(h.importedAt))}" +
                                            if (h.isDemoData) " • FICTÍCIO" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingImportPreview != null) {
        ImportPreviewDialog(
            preview = pendingImportPreview,
            onDismiss = onDismissPendingImport,
            onConfirm = onConfirmPendingImport
        )
    }

    if (showSpecDialog) {
        ImportStandardSpecDialog(
            onDismiss = { showSpecDialog = false },
            onDownloadTemplate = { templateSaver.launch("invest_ai_modelo_carteira.csv") },
            onDownloadQuickUpdateTemplate = {
                quickUpdateTemplateSaver.launch("invest_ai_atualizacao_ativo_preco_data_dividendos.csv")
            }
        )
    }

    if (showAddPositionDialog) {
        AddPositionDialog(
            portfolioType = selectedType,
            onDismiss = { showAddPositionDialog = false },
            onConfirm = { ticker, name, cls, qty, pm, cur, sec, dc, isCyclical ->
                onAddManualPosition(selectedType, ticker, name, cls, qty, pm, cur, sec, dc, isCyclical)
                showAddPositionDialog = false
            }
        )
    }

    if (showOperationDialog) {
        RecordOperationDialog(
            onDismiss = { showOperationDialog = false },
            onConfirm = { ticker, opType, qty, price, notes ->
                onRecordOperation(selectedType, ticker, opType, qty, price, notes)
                showOperationDialog = false
            }
        )
    }

    if (showCashDialog) {
        EditCashDialog(
            currentCash = activeHhi.cashBrl,
            onDismiss = { showCashDialog = false },
            onConfirm = { newVal ->
                onUpdateCash(selectedType, newVal)
                showCashDialog = false
            }
        )
    }

    selectedPositionForEdit?.let { editingPos ->
        val catalogMatch = state.marketAssets.firstOrNull {
            it.ticker.equals(editingPos.ticker, ignoreCase = true)
        }
        AssetPositionDetailAndEditSheet(
            position = editingPos,
            catalogAsset = catalogMatch,
            portfolioTotalInvestedBrl = activeHhi.totalInvestedBrl,
            onDismiss = { selectedPositionForEdit = null },
            onSave = { id, ticker, name, assetClass, qty, pm, cur, sector, dataComDay, existingSource, isCyclical ->
                onUpdatePosition(
                    id,
                    ticker,
                    name,
                    assetClass,
                    qty,
                    pm,
                    cur,
                    sector,
                    dataComDay,
                    existingSource,
                    isCyclical
                )
                selectedPositionForEdit = null
            },
            onDelete = { _ ->
                pendingDeletePosition = editingPos
                selectedPositionForEdit = null
            }
        )
    }

    pendingDeletePosition?.let { pos ->
        DeleteConfirmImpactDialog(
            title = "Excluir Ativo da Carteira?",
            entitySummary = "${pos.ticker} • ${"%.2f".format(pos.quantity)} unid. • ${formatBrl(pos.totalCurrentBrl)}",
            impactDescription = "O ativo será removido da carteira atual e os totais de patrimônio e HHI serão recalculados. O histórico de operações e proventos permanece preservado.",
            onDismiss = { pendingDeletePosition = null },
            onConfirm = {
                onDeletePosition(pos.id)
                pendingDeletePosition = null
            }
        )
    }

    selectedOperationForEdit?.let { op ->
        EditOperationDialog(
            operation = op,
            onDismiss = { selectedOperationForEdit = null },
            onConfirm = { opId, ticker, opType, qty, price, dateIso, notes ->
                onUpdateOperation(opId, ticker, opType, qty, price, dateIso, notes)
                selectedOperationForEdit = null
            }
        )
    }

    pendingDeleteOperation?.let { op ->
        DeleteConfirmImpactDialog(
            title = "Excluir Operação?",
            entitySummary = "${op.ticker} • ${op.operationType} • ${op.dateIso} • ${formatBrl(op.totalValue)}",
            impactDescription = "Ao excluir esta operação, a posição, o preço médio e os proventos vinculados serão recalculados automaticamente.",
            onDismiss = { pendingDeleteOperation = null },
            onConfirm = {
                onDeleteOperation(op.id)
                pendingDeleteOperation = null
            }
        )
    }

    if (showAuditDialog) {
        AuditAndRestoreDialog(
            auditLogs = state.financialAuditLogs,
            onDismiss = { showAuditDialog = false },
            onRestoreAuditEntry = onRestoreAuditEntry
        )
    }

    if (orderScreenshotState !is OrderScreenshotRecognitionState.Idle) {
        val lastErrorUri = (orderScreenshotState as? OrderScreenshotRecognitionState.Error)
            ?.lastImageUriString
            ?.takeIf { it.isNotBlank() }
        OrderScreenshotReviewDialog(
            state = orderScreenshotState,
            targetPortfolioType = selectedType,
            onDismiss = onDismissScreenshotReview,
            onPickAnotherImage = {
                orderDocumentPicker.launch(
                    OrderScreenshotRecognizer.PICKER_MIME_TYPES
                )
            },
            onConfirmLaunch = { orders, applyToPositions ->
                onConfirmScreenshotOrders(selectedType, orders, applyToPositions)
            },
            onRetryLastFile = lastErrorUri?.let { uriStr ->
                { onRecognizeOrderScreenshot(android.net.Uri.parse(uriStr)) }
            }
        )
    }
}

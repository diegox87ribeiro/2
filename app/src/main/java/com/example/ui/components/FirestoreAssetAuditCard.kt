package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AuditOpportunityItem
import com.example.domain.model.AuditPotential
import com.example.domain.model.AuditRiskItem
import com.example.domain.model.AuditRiskLevel
import com.example.domain.model.AuditSeverity
import com.example.domain.model.AuditVerdict
import com.example.domain.model.CloudAsset
import com.example.domain.model.FirestoreAssetAuditSummary
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VioletAccent
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AssetAuditRunState
import com.example.ui.viewmodel.CloudAssetAuditsUiState
import com.example.ui.viewmodel.CloudAssetsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FirestoreAssetAuditCard(
    cloudAssetsUiState: CloudAssetsUiState,
    assetAuditsUiState: CloudAssetAuditsUiState,
    activeAuditRunState: AssetAuditRunState,
    isSyncingAssets: Boolean,
    onSyncAssetsToFirestore: () -> Unit,
    onRunGeminiAudit: (String) -> Unit,
    onSelectSavedAudit: (FirestoreAssetAuditSummary) -> Unit,
    onDismissAuditState: () -> Unit
) {
    val availableAssets = remember(cloudAssetsUiState) {
        (cloudAssetsUiState as? CloudAssetsUiState.Success)?.assets.orEmpty()
    }
    var selectedAssetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(availableAssets) {
        if (availableAssets.isNotEmpty() && availableAssets.none { it.id == selectedAssetId }) {
            selectedAssetId = availableAssets.first().id
        }
    }

    val selectedAsset = remember(availableAssets, selectedAssetId) {
        availableAssets.firstOrNull { it.id == selectedAssetId } ?: availableAssets.firstOrNull()
    }

    FintechCard(
        borderColor = VioletAccent.copy(alpha = 0.55f),
        modifier = Modifier.testTag("firestore_asset_audit_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.FactCheck,
                    contentDescription = "Auditoria Executiva Gemini",
                    tint = VioletAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Auditoria Executiva Gemini (Firestore)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = VioletAccent.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, VioletAccent.copy(alpha = 0.45f))
            ) {
                Text(
                    text = "GEMINI + CLOUD",
                    style = MaterialTheme.typography.labelSmall,
                    color = VioletAccent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Seleciona um ativo armazenado no Cloud Firestore, audita indicadores, riscos e oportunidades via API Gemini (Firebase AI Logic) e persiste o resumo executivo na nuvem.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onSyncAssetsToFirestore,
            enabled = !isSyncingAssets,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("sync_assets_firestore_button")
        ) {
            if (isSyncingAssets) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Sincronizando Ativos no Firestore...")
            } else {
                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (availableAssets.isEmpty()) "Sincronizar Ativos da Carteira no Firestore"
                    else "Atualizar Ativos no Firestore (${availableAssets.size} na nuvem)"
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        when (cloudAssetsUiState) {
            is CloudAssetsUiState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ElectricCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Carregando ativos salvos no Firestore...", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            is CloudAssetsUiState.Error -> {
                Text(
                    text = "Firestore (Ativos): ${cloudAssetsUiState.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberGold
                )
            }
            is CloudAssetsUiState.Success -> {
                if (availableAssets.isEmpty()) {
                    Text(
                        text = "Nenhum ativo sincronizado no Firestore ainda. Toque no botão acima para enviar os ativos da carteira para a nuvem e iniciar a auditoria Gemini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    Text(
                        text = "1. Selecione o Ativo no Firestore",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(availableAssets, key = { it.id.ifBlank { it.ticker } }) { asset ->
                            val isSelected = selectedAsset?.id == asset.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedAssetId = asset.id },
                                label = { Text(asset.ticker, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VioletAccent.copy(alpha = 0.25f),
                                    selectedLabelColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("firestore_asset_chip_${asset.ticker}")
                            )
                        }
                    }

                    if (selectedAsset != null) {
                        Spacer(Modifier.height(8.dp))
                        SelectedCloudAssetPreview(asset = selectedAsset)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        val isAuditing = activeAuditRunState is AssetAuditRunState.Loading
        Button(
            onClick = { selectedAsset?.let { onRunGeminiAudit(it.id.ifBlank { it.ticker }) } },
            enabled = selectedAsset != null && !isAuditing,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("run_firestore_asset_audit_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldPrimary,
                contentColor = EmeraldOnPrimary
            )
        ) {
            if (isAuditing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = EmeraldOnPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Auditando ${(activeAuditRunState as AssetAuditRunState.Loading).assetId} com Gemini...")
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (selectedAsset != null) "Auditar ${selectedAsset.ticker} do Firestore (Gemini)"
                    else "Selecione um Ativo no Firestore para Auditar"
                )
            }
        }

        when (activeAuditRunState) {
            is AssetAuditRunState.Error -> {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CoralRed.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, CoralRed.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth().testTag("firestore_audit_error_banner")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Erro na Auditoria Gemini",
                            style = MaterialTheme.typography.titleSmall,
                            color = CoralRed
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = activeAuditRunState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                        if (activeAuditRunState.message.contains("app check", ignoreCase = true)) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Dica App Check: confirme que o app foi iniciado com token de depuração válido (ou build Release com reCAPTCHA Enterprise) e tente novamente.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = onDismissAuditState,
                            modifier = Modifier.testTag("dismiss_firestore_audit_error_button")
                        ) {
                            Text("Dispensar", color = CoralRed)
                        }
                    }
                }
            }
            is AssetAuditRunState.Success -> {
                Spacer(Modifier.height(12.dp))
                ExecutiveAuditSummarySection(summary = activeAuditRunState.summary)
            }
            else -> Unit
        }

        if (assetAuditsUiState is CloudAssetAuditsUiState.Success && assetAuditsUiState.audits.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = SubtleBorder)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Auditorias Executivas Salvas no Firestore (${assetAuditsUiState.audits.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan
                )
            }
            Spacer(Modifier.height(6.dp))
            val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")) }
            assetAuditsUiState.audits.take(5).forEach { audit ->
                val dateLabel = audit.createdAtMillis?.let { fmt.format(Date(it)) } ?: "Recente"
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DeepNavySurface,
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { onSelectSavedAudit(audit) }
                        .testTag("saved_firestore_audit_${audit.ticker}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${audit.ticker} • ${audit.verdict.label} (Score ${audit.auditScore}/100)",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "${audit.riskLevel.label} • ${audit.risks.size} riscos • ${audit.opportunities.size} oportunidades • $dateLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "Abrir",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedCloudAssetPreview(asset: CloudAsset) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DeepNavySurface,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${asset.ticker} — ${asset.name}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (asset.demoData) AmberGold.copy(alpha = 0.18f) else EmeraldContainer
                ) {
                    Text(
                        text = if (asset.demoData) "DEMO" else "REAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (asset.demoData) AmberGold else EmeraldPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "${asset.resolvedAssetClass().label} • Setor: ${asset.sector.ifBlank { "Não informado" }}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = "Preço: ${formatBrl(asset.currentPrice)} | Qtd: ${"%.2f".format(asset.quantity)} | DY 12m: ${"%.2f".format(asset.dy12m)}% | P/VP: ${"%.2f".format(asset.pvp)} | ROE: ${"%.1f".format(asset.roe)}%",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = ElectricCyan
            )
            Text(
                text = "Fonte no Firestore: ${asset.sourceLabel.ifBlank { "Não informada" }} (${asset.dataBaseDate.ifBlank { "Sem data-base" }})",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ExecutiveAuditSummarySection(summary: FirestoreAssetAuditSummary) {
    val riskColor = when (summary.riskLevel) {
        AuditRiskLevel.BAIXO -> EmeraldPrimary
        AuditRiskLevel.MODERADO -> ElectricCyan
        AuditRiskLevel.ALTO -> AmberGold
        AuditRiskLevel.CRITICO -> CoralRed
    }
    val verdictColor = when (summary.verdict) {
        AuditVerdict.FAVORAVEL -> EmeraldPrimary
        AuditVerdict.NEUTRO -> ElectricCyan
        AuditVerdict.CAUTELA -> AmberGold
        AuditVerdict.EVITAR -> CoralRed
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DeepNavySurface,
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().testTag("firestore_audit_result_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Resumo Executivo • ${summary.ticker}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = summary.assetName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = verdictColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, verdictColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = summary.verdict.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = verdictColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AuditBadgePill(
                    label = "Score Auditoria",
                    value = "${summary.auditScore}/100",
                    color = EmeraldPrimary,
                    modifier = Modifier.weight(1f)
                )
                AuditBadgePill(
                    label = "Classificação",
                    value = summary.riskLevel.label,
                    color = riskColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = summary.executiveSummary,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )

            if (summary.risks.isNotEmpty()) {
                HorizontalDivider(color = SubtleBorder)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReportProblem, contentDescription = null, tint = CoralRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Riscos Mapeados (${summary.risks.size})", style = MaterialTheme.typography.titleSmall, color = CoralRed)
                }
                summary.risks.forEach { risk ->
                    AuditRiskRow(risk)
                }
            }

            if (summary.opportunities.isNotEmpty()) {
                HorizontalDivider(color = SubtleBorder)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Oportunidades Mapeadas (${summary.opportunities.size})", style = MaterialTheme.typography.titleSmall, color = EmeraldPrimary)
                }
                summary.opportunities.forEach { opp ->
                    AuditOpportunityRow(opp)
                }
            }

            if (summary.inconsistencyAlerts.isNotEmpty()) {
                HorizontalDivider(color = SubtleBorder)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Alertas de Qualidade & Dados", style = MaterialTheme.typography.titleSmall, color = AmberGold)
                }
                summary.inconsistencyAlerts.forEach { alert ->
                    Text("• $alert", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            HorizontalDivider(color = SubtleBorder)
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Column {
                    Text("Próximo Passo de Auditoria", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                    Text(summary.auditRecommendation, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }
            }

            Text(
                text = "Modelo: ${summary.modelName} • Fonte Firestore: ${summary.sourceSnapshotLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun AuditBadgePill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                value,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                color = color
            )
        }
    }
}

@Composable
private fun AuditRiskRow(item: AuditRiskItem) {
    val sevColor = when (item.severity) {
        AuditSeverity.ALTA -> CoralRed
        AuditSeverity.MEDIA -> AmberGold
        AuditSeverity.BAIXA -> ElectricCyan
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(shape = RoundedCornerShape(6.dp), color = sevColor.copy(alpha = 0.18f)) {
                Text(
                    text = item.severity.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = sevColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(item.title, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        }
        Text(item.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text("Mitigação: ${item.mitigation}", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
    }
}

@Composable
private fun AuditOpportunityRow(item: AuditOpportunityItem) {
    val potColor = when (item.potential) {
        AuditPotential.ALTO -> EmeraldPrimary
        AuditPotential.MEDIO -> ElectricCyan
        AuditPotential.BAIXO -> TextSecondary
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(shape = RoundedCornerShape(6.dp), color = potColor.copy(alpha = 0.18f)) {
                Text(
                    text = item.potential.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = potColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(item.title, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        }
        Text(item.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text("Catalisador: ${item.catalyst}", style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary)
    }
}

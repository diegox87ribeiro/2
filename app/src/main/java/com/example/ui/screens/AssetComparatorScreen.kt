package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.LiveQuoteLookupState
import com.example.domain.model.ValuationAndComparisonCalculator
import com.example.ui.components.DataDisclaimerBanner
import com.example.ui.components.FintechCard
import com.example.ui.components.PillarRadarChart
import com.example.ui.components.formatBrl
import com.example.ui.components.formatPct
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ChartColors
import com.example.ui.theme.CoralRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.InvestDashboardState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetComparatorScreen(
    state: InvestDashboardState,
    onQueryLiveQuote: (String) -> Unit = {}
) {
    val comparableAssets = remember(state.marketAssets) {
        state.marketAssets.filter { it.assetClass != AssetClass.RENDA_FIXA }
    }
    var targetBazinDy by remember { mutableFloatStateOf(6.0f) }

    val defaultTickers = remember(comparableAssets, state.realPositions) {
        val ownedMatches = state.realPositions
            .mapNotNull { pos -> comparableAssets.firstOrNull { it.ticker.equals(pos.ticker, ignoreCase = true) }?.ticker }
            .distinct()
        (ownedMatches + comparableAssets.map { it.ticker }).distinct().take(3)
    }

    var selectedTickers by remember { mutableStateOf<List<String>>(emptyList()) }
    var hasSeededDefaults by remember { mutableStateOf(false) }
    var tickerInput by remember { mutableStateOf("") }
    var pendingTickers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lookupFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(comparableAssets, defaultTickers) {
        if (!hasSeededDefaults && defaultTickers.isNotEmpty()) {
            selectedTickers = defaultTickers
            hasSeededDefaults = true
        } else {
            val validMap = comparableAssets.associateBy { it.ticker.uppercase() }
            var nextSelection = selectedTickers.filter { it.uppercase() in validMap }
            val resolved = mutableSetOf<String>()
            pendingTickers.forEach { pending ->
                val match = validMap[pending.uppercase()]
                if (match != null) {
                    resolved.add(pending)
                    if (match.ticker !in nextSelection) {
                        nextSelection = if (nextSelection.size < 3) {
                            nextSelection + match.ticker
                        } else {
                            nextSelection.drop(1) + match.ticker
                        }
                    }
                }
            }
            if (resolved.isNotEmpty()) {
                pendingTickers = pendingTickers - resolved
                lookupFeedback = "Adicionado ao comparador: ${resolved.joinToString(", ")}"
            }
            selectedTickers = nextSelection.ifEmpty { defaultTickers }
        }
    }

    LaunchedEffect(state.liveLookupState) {
        val err = state.liveLookupState as? LiveQuoteLookupState.Error
        if (err != null && err.ticker.uppercase() in pendingTickers) {
            pendingTickers = pendingTickers - err.ticker.uppercase()
            lookupFeedback = err.message
        }
    }

    fun submitTypedTickers() {
        val tokens = tickerInput
            .split(',', ';', ' ')
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return

        var updated = selectedTickers
        val missing = mutableListOf<String>()
        val matched = mutableListOf<String>()
        tokens.forEach { token ->
            val assetMatch = comparableAssets.firstOrNull { it.ticker.equals(token, ignoreCase = true) }
            if (assetMatch != null) {
                if (assetMatch.ticker !in updated) {
                    updated = if (updated.size < 3) updated + assetMatch.ticker else updated.drop(1) + assetMatch.ticker
                }
                matched.add(assetMatch.ticker)
            } else {
                missing.add(token)
            }
        }
        selectedTickers = updated
        if (missing.isNotEmpty()) {
            pendingTickers = pendingTickers + missing.toSet()
            missing.forEach { onQueryLiveQuote(it) }
            lookupFeedback = "Buscando ${missing.joinToString(", ")} em tempo real (B3 / ADVFN / Yahoo)..."
        } else if (matched.isNotEmpty()) {
            lookupFeedback = "Comparando: ${matched.joinToString(", ")}"
        }
        tickerInput = ""
    }

    val activeAssets = remember(comparableAssets, selectedTickers) {
        selectedTickers.mapNotNull { t -> comparableAssets.firstOrNull { it.ticker.equals(t, ignoreCase = true) } }
            .ifEmpty { comparableAssets.take(2) }
    }

    val orderedChipAssets = remember(comparableAssets, selectedTickers) {
        val selectedFirst = comparableAssets.filter { asset ->
            selectedTickers.any { it.equals(asset.ticker, ignoreCase = true) }
        }
        (selectedFirst + comparableAssets).distinctBy { it.ticker }
    }

    val valuations = remember(activeAssets, targetBazinDy) {
        activeAssets.map { it to ValuationAndComparisonCalculator.computeValuation(it, targetBazinDy.toDouble()) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("comparator_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DataDisclaimerBanner(
                text = "Comparador Lado a Lado + Valuation Bazin/Graham. Digite ou selecione de 2 a 3 ativos para comparar os 5 pilares (Dividendos, Valuation, Rentabilidade, Solidez e Liquidez)."
            )
        }

        item {
            FintechCard {
                Text("Selecionar ou Digitar Ativos (2 a 3 para o Radar)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tickerInput,
                        onValueChange = { tickerInput = it.uppercase() },
                        label = { Text("Digitar ticker (ex: PETR4, WEGE3)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { submitTypedTickers() }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("comparator_ticker_input")
                    )
                    Button(
                        onClick = { submitTypedTickers() },
                        enabled = tickerInput.isNotBlank(),
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("comparator_add_ticker_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = EmeraldOnPrimary
                        )
                    ) {
                        Text("Comparar")
                    }
                }
                if (lookupFeedback != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (pendingTickers.isNotEmpty() && state.liveLookupState is LiveQuoteLookupState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                        Text(
                            lookupFeedback.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (lookupFeedback.orEmpty().contains("Não foi possível") || lookupFeedback.orEmpty().contains("Erro")) {
                                CoralRed
                            } else {
                                ElectricCyan
                            },
                            modifier = Modifier.testTag("comparator_lookup_feedback")
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(orderedChipAssets, key = { it.ticker }) { asset ->
                        val isSelected = selectedTickers.any { it.equals(asset.ticker, ignoreCase = true) }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTickers = when {
                                    isSelected && selectedTickers.size > 2 -> selectedTickers.filterNot { it.equals(asset.ticker, ignoreCase = true) }
                                    !isSelected && selectedTickers.size < 3 -> selectedTickers + asset.ticker
                                    !isSelected -> selectedTickers.drop(1) + asset.ticker
                                    else -> selectedTickers
                                }
                            },
                            label = { Text(asset.ticker, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldPrimary
                            ),
                            modifier = Modifier.testTag("comparator_chip_${asset.ticker}")
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "DY-Alvo Bazin (Teto): ${"%.1f".format(targetBazinDy)}% a.a.",
                    style = MonoValueStyle,
                    color = AmberGold
                )
                Slider(
                    value = targetBazinDy,
                    onValueChange = { targetBazinDy = it },
                    valueRange = 4.0f..10.0f
                )
            }
        }

        item {
            FintechCard(modifier = Modifier.testTag("comparator_radar_card")) {
                Text("Radar 5 Pilares (0 a 100 pts)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val series = valuations.mapIndexed { idx, (asset, valResult) ->
                    Triple(asset.ticker, ChartColors[idx % ChartColors.size], valResult.pillarScores.asList())
                }
                PillarRadarChart(
                    series = series,
                    axisLabels = ValuationAndComparisonCalculator.PILLAR_LABELS
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    valuations.forEachIndexed { idx, (asset, valResult) ->
                        val color = ChartColors[idx % ChartColors.size]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${asset.ticker} (${valResult.overallScore.toInt()} pts)",
                                style = MaterialTheme.typography.labelLarge,
                                color = color
                            )
                        }
                    }
                }
            }
        }

        item {
            FintechCard(modifier = Modifier.testTag("comparator_table_card")) {
                Text("Comparação Lado a Lado & Valuation", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))

                // Cabeçalho de tickers
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Indicador", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.weight(1.2f))
                    valuations.forEachIndexed { idx, (asset, _) ->
                        Text(
                            asset.ticker,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = ChartColors[idx % ChartColors.size],
                            modifier = Modifier.weight(1f).testTag("comparator_table_header_${asset.ticker}")
                        )
                    }
                }
                HorizontalDivider(color = SubtleBorder, modifier = Modifier.padding(vertical = 6.dp))

                val rows: List<Pair<String, List<Pair<String, Boolean>>>> = listOf(
                    "Preço Atual" to valuations.map { (a, _) -> formatBrl(a.price) to false },
                    "DY 12m" to valuations.let { list ->
                        val best = list.maxOfOrNull { it.first.dy12m } ?: 0.0
                        list.map { (a, _) -> formatPct(a.dy12m, false) to (a.dy12m == best && best > 0.0) }
                    },
                    "P/VP" to valuations.let { list ->
                        val best = list.filter { it.first.pvp > 0 }.minOfOrNull { it.first.pvp } ?: 0.0
                        list.map { (a, _) -> "${"%.2f".format(a.pvp)}x" to (a.pvp == best && best > 0.0) }
                    },
                    "Teto Bazin" to valuations.let { list ->
                        val best = list.mapNotNull { it.second.bazinMarginPercent }.maxOrNull()
                        list.map { (_, v) ->
                            val lbl = v.bazinCeilingPriceBrl?.let { formatBrl(it) } ?: "N/A"
                            lbl to (v.bazinMarginPercent != null && v.bazinMarginPercent == best)
                        }
                    },
                    "Margem Bazin" to valuations.let { list ->
                        val best = list.mapNotNull { it.second.bazinMarginPercent }.maxOrNull()
                        list.map { (_, v) ->
                            val lbl = v.bazinMarginPercent?.let { formatPct(it) } ?: "N/A"
                            lbl to (v.bazinMarginPercent != null && v.bazinMarginPercent == best)
                        }
                    },
                    "Justo Graham/VP" to valuations.let { list ->
                        val best = list.mapNotNull { it.second.grahamMarginPercent }.maxOrNull()
                        list.map { (_, v) ->
                            val lbl = v.grahamFairPriceBrl?.let { formatBrl(it) } ?: "N/A"
                            lbl to (v.grahamMarginPercent != null && v.grahamMarginPercent == best)
                        }
                    },
                    "Liquidez / dia" to valuations.let { list ->
                        val best = list.maxOfOrNull { it.first.liquidezDiariaMilhoes } ?: 0.0
                        list.map { (a, _) -> "R$${"%.1f".format(a.liquidezDiariaMilhoes)}M" to (a.liquidezDiariaMilhoes == best && best > 0.0) }
                    },
                    "Score 5 Pilares" to valuations.let { list ->
                        val best = list.maxOfOrNull { it.second.overallScore } ?: 0.0
                        list.map { (_, v) -> "${v.overallScore.toInt()} pts" to (v.overallScore == best) }
                    }
                )

                rows.forEach { (metricName, values) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(metricName, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1.2f))
                        values.forEach { (valStr, isWinner) ->
                            Text(
                                text = valStr,
                                style = MonoValueStyle.copy(
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                    fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isWinner) EmeraldPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    HorizontalDivider(color = SubtleBorder.copy(alpha = 0.45f))
                }
            }
        }
    }
}

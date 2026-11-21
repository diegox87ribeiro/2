package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.domain.model.DataComGroup
import com.example.domain.model.LiveMarketQuote
import com.example.domain.model.LiveQuoteLookupState
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveMarketLookupPanel(
    lookupState: LiveQuoteLookupState,
    quickTickers: List<String>,
    ownedTickers: Set<String>,
    onQueryTicker: (String) -> Unit,
    onApplyPriceToPortfolio: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialTicker: String = "VALE3"
) {
    var inputTicker by remember(initialTicker) { mutableStateOf(initialTicker) }
    val uriHandler = LocalUriHandler.current
    val ptBr = remember { Locale("pt", "BR") }

    FintechCard(
        modifier = modifier,
        borderColor = ElectricCyan.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = "Consulta ao vivo B3 e ADVFN",
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Consulta em Tempo Real", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "B3 Oficial, ADVFN Brasil & Yahoo B3 (.SA)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = EmeraldContainer.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "AO VIVO",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = EmeraldPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputTicker,
                onValueChange = { raw ->
                    inputTicker = raw.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() || it == '.' }
                },
                label = { Text("Ticker B3 (ex: VALE3, VULC3)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onQueryTicker(inputTicker) }
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("live_ticker_input")
            )

            Button(
                onClick = { onQueryTicker(inputTicker) },
                enabled = lookupState !is LiveQuoteLookupState.Loading && inputTicker.trim().length >= 4,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = EmeraldOnPrimary
                ),
                modifier = Modifier
                    .heightIn(min = 52.dp)
                    .testTag("live_lookup_button")
            ) {
                if (lookupState is LiveQuoteLookupState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = EmeraldOnPrimary
                    )
                } else {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Consultar")
                }
            }
        }

        if (quickTickers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickTickers.forEach { t ->
                    val isOwned = ownedTickers.contains(t.uppercase())
                    FilterChip(
                        selected = inputTicker.equals(t, ignoreCase = true),
                        onClick = {
                            inputTicker = t
                            onQueryTicker(t)
                        },
                        label = {
                            Text(
                                if (isOwned) "$t ★" else t,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldContainer,
                            selectedLabelColor = EmeraldPrimary
                        ),
                        modifier = Modifier.testTag("live_quick_chip_$t")
                    )
                }
            }
        }

        when (lookupState) {
            is LiveQuoteLookupState.Idle -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Digite ou toque em um ativo acima para consultar cotação e indicadores ao vivo diretamente da B3 / ADVFN / mercado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            is LiveQuoteLookupState.Loading -> {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ElectricCyan)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Consultando ${lookupState.ticker} em tempo real (B3 / ADVFN / Yahoo B3)...",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan
                    )
                }
            }

            is LiveQuoteLookupState.Error -> {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CoralContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, CoralRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("live_quote_error_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(lookupState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onQueryTicker(lookupState.ticker) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Tentar novamente")
                            }
                            OutlinedButton(
                                onClick = {
                                    uriHandler.openUri("https://br.advfn.com/p.php?pid=qkquote&symbol=BOV%3A${lookupState.ticker}")
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Abrir ADVFN")
                            }
                        }
                    }
                }
            }

            is LiveQuoteLookupState.Success -> {
                Spacer(Modifier.height(12.dp))
                LiveQuoteResultDetails(
                    quote = lookupState.quote,
                    isOwnedInPortfolio = ownedTickers.contains(lookupState.quote.ticker.uppercase()),
                    ptBr = ptBr,
                    onApplyToPortfolio = { onApplyPriceToPortfolio(lookupState.quote.ticker) },
                    onOpenB3 = { uriHandler.openUri(lookupState.quote.b3PageUrl) },
                    onOpenAdvfn = { uriHandler.openUri(lookupState.quote.advfnPageUrl) },
                    onOpenInvestidor10 = {
                        val invUrl = lookupState.quote.investidor10PageUrl.ifBlank {
                            "https://investidor10.com.br/fiis/${lookupState.quote.ticker.lowercase(Locale.ROOT)}/"
                        }
                        uriHandler.openUri(invUrl)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveQuoteResultDetails(
    quote: LiveMarketQuote,
    isOwnedInPortfolio: Boolean,
    ptBr: Locale,
    onApplyToPortfolio: () -> Unit,
    onOpenB3: () -> Unit,
    onOpenAdvfn: () -> Unit,
    onOpenInvestidor10: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = EmeraldContainer.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_quote_result_card")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(quote.ticker, style = MaterialTheme.typography.titleLarge, color = EmeraldPrimary)
                    Text(quote.companyName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Fontes: ${quote.primarySourceLabel} • ${quote.marketTimestampLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatBrl(quote.price),
                        style = MonoValueStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        formatPct(quote.changePercent1d),
                        style = MonoValueStyle,
                        color = if (quote.changePercent1d >= 0) EmeraldPrimary else CoralRed
                    )
                }
            }

            val metrics = buildList {
                quote.openPrice?.let { add("Abertura" to formatBrl(it)) }
                if (quote.dayLow != null && quote.dayHigh != null) {
                    add("Mín / Máx Dia" to "${formatBrl(quote.dayLow)} – ${formatBrl(quote.dayHigh)}")
                }
                quote.dividendYield12m?.takeIf { it in 0.01..80.0 }?.let { add("DY (12m)" to formatPct(it, false)) }
                quote.priceToBook?.takeIf { it in 0.05..30.0 }?.let { add("P/VP" to String.format(ptBr, "%.2fx", it)) }
                quote.lastDividendBrl?.let { add("Últ. Provento" to formatBrl(it)) }
                quote.dataComDay?.let { day ->
                    val grp = DataComGroup.fromDay(day)
                    val label = if (grp != null) "Dia $day (Grupo ${grp.code})" else "Dia $day"
                    add("Data-Com (Investidor10)" to label)
                }
                quote.dataExDay?.let { exDay ->
                    add("Data-Ex (Yahoo)" to "Dia $exDay")
                }
                quote.paymentDay?.let { add("Pagamento" to "Dia $it") }
                quote.dailyVolumeBrlMillions?.let {
                    add("Volume Dia" to "R$ ${String.format(ptBr, "%.1f", it)}M")
                }
                quote.mm9?.let { add("MM9" to formatBrl(it)) }
                quote.mm21?.let { add("MM21" to formatBrl(it)) }
                quote.rsi14?.let { add("IFR (14)" to String.format(ptBr, "%.1f", it)) }
                if (quote.supportPrice != null && quote.resistancePrice != null) {
                    add("Suporte / Resist." to "${formatBrl(quote.supportPrice)} / ${formatBrl(quote.resistancePrice)}")
                }
                quote.priceToEarnings?.takeIf { it in 0.1..500.0 }?.let { add("P/L" to String.format(ptBr, "%.2fx", it)) }
                if (quote.low52w != null && quote.high52w != null) {
                    add("52 Semanas" to "${formatBrl(quote.low52w)} – ${formatBrl(quote.high52w)}")
                }
            }

            metrics.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (label, value) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(value, style = MonoValueStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize))
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            if (quote.dataExDay != null) {
                val dataComDay = quote.dataComDay
                val dataComGroup = DataComGroup.fromDay(dataComDay)
                val noteText = if (dataComDay != null && dataComGroup != null) {
                    "Regra B3: a Data-Com (Dia $dataComDay • Grupo ${dataComGroup.code}, Investidor10) define o direito ao dividendo e o grupo no app. " +
                        "A Data-Ex (Dia ${quote.dataExDay}, Yahoo Finance) é o primeiro pregão seguinte sem direito ao provento."
                } else {
                    "Nota: o Yahoo Finance informa apenas a Data-Ex (Dia ${quote.dataExDay}, primeiro pregão sem direito ao dividendo), " +
                        "que não é usada para classificar o grupo Data-Com (G1–G4). Confira a Data-Com oficial no Investidor10 ou no cadastro do ativo."
                }
                Text(
                    text = noteText,
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    modifier = Modifier.testTag("live_quote_datacom_vs_dataex_note")
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenB3,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("open_b3_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Site da B3")
                }
                OutlinedButton(
                    onClick = onOpenAdvfn,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("open_advfn_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Site ADVFN")
                }
                OutlinedButton(
                    onClick = onOpenInvestidor10,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("open_investidor10_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Investidor10")
                }
                if (isOwnedInPortfolio) {
                    Button(
                        onClick = onApplyToPortfolio,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = EmeraldOnPrimary),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("apply_live_price_portfolio_button")
                    ) {
                        Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Atualizar na Carteira")
                    }
                }
            }
        }
    }
}

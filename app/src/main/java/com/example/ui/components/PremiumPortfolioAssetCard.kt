package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AssetCardIndicators
import com.example.domain.model.AssetClass
import com.example.domain.model.AssetDetailTab
import com.example.domain.model.DividendStatus
import com.example.domain.model.PortfolioItemView
import com.example.ui.theme.CancelledRed
import com.example.ui.theme.ConfirmedBlue
import com.example.ui.theme.DarkPremiumBg
import com.example.ui.theme.DarkPremiumBorder
import com.example.ui.theme.DarkPremiumCard
import com.example.ui.theme.DarkPremiumCardSecondary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.PremiumTextPrimary
import com.example.ui.theme.PremiumTextSecondary
import com.example.ui.theme.ProjectedAmber
import com.example.ui.theme.ReceivedGreen
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.VioletAccent
import java.util.Locale

/**
 * Card Dark Premium interativo de ativo na Carteira de Investimentos do INVEST AI.
 * - Cabeçalho: ícone da classe, ticker clicável (histórico de ordens), badge G1–G4 (ou Não cíclico),
 *   sparkline de rentabilidade, percentual de retorno, editar cadastro e excluir.
 * - Grade 3x2 com 6 indicadores financeiros independentes e clicáveis.
 */
@Composable
fun PremiumPortfolioAssetCard(
    pos: PortfolioItemView,
    indicators: AssetCardIndicators,
    onClickTicker: () -> Unit,
    onOpenMetricDetail: (AssetDetailTab) -> Unit,
    onOpenCadastroAndEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPositiveReturn = pos.profitLossPercent >= 0.0
    val returnColor = when {
        !pos.hasMarketPrice -> ProjectedAmber
        isPositiveReturn -> ReceivedGreen
        else -> CancelledRed
    }

    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            DarkPremiumCard,
            Color(0xFF0A1730),
            DarkPremiumBg
        )
    )
    val borderGlow = Brush.linearGradient(
        colors = listOf(
            ConfirmedBlue.copy(alpha = 0.72f),
            TechBluePrimary.copy(alpha = 0.48f),
            DarkPremiumBorder,
            ConfirmedBlue.copy(alpha = 0.55f)
        )
    )

    val (classIcon, classAccent) = assetClassVisual(pos.assetClass)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardGradient)
            .border(1.2.dp, borderGlow, RoundedCornerShape(18.dp))
            .padding(12.dp)
            .testTag("premium_asset_card_${pos.ticker}")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ==================== CABEÇALHO (TICKER POR CIMA + GRÁFICO DE FUNDO) ====================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkPremiumCardSecondary.copy(alpha = 0.32f))
            ) {
                // Camada de fundo: gráfico sparkline de rentabilidade ao longo do cabeçalho
                CompactReturnSparkline(
                    points = indicators.sparklinePoints,
                    lineColor = returnColor,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(start = 46.dp, top = 8.dp, bottom = 4.dp, end = 6.dp)
                )

                // Véu sutil à esquerda para garantir contraste máximo do ticker sobre o gráfico
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                0.0f to DarkPremiumCard.copy(alpha = 0.88f),
                                0.42f to DarkPremiumCard.copy(alpha = 0.56f),
                                0.72f to Color.Transparent,
                                1.0f to DarkPremiumBg.copy(alpha = 0.52f)
                            )
                        )
                )

                // Camada superior: conteúdo interativo (Ícone + Ticker/Badge + Rentabilidade + Ações)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lado esquerdo: Ícone da classe + Ticker em destaque por cima + Badge G1-G4 / Não cíclico + Classe
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            classAccent.copy(alpha = 0.28f),
                                            DarkPremiumCardSecondary
                                        )
                                    )
                                )
                                .border(1.dp, classAccent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .clickable(onClick = onClickTicker),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = classIcon,
                                contentDescription = pos.assetClass.label,
                                tint = classAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(onClick = onClickTicker)
                                        .padding(end = 2.dp)
                                        .testTag("asset_card_ticker_${pos.ticker}"),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = pos.ticker + if (pos.isDemo) " *" else "",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.4.sp
                                        ),
                                        color = PremiumTextPrimary,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val groupBadge = if (pos.isCyclical) pos.dataComGroup else null
                                if (groupBadge != null) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = TechBluePrimary.copy(alpha = 0.28f),
                                        border = BorderStroke(1.dp, ConfirmedBlue.copy(alpha = 0.85f)),
                                        modifier = Modifier.testTag("position_group_badge_${pos.ticker}")
                                    ) {
                                        Text(
                                            text = groupBadge.code,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = ConfirmedBlue,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = pos.assetClass.shortLabel.uppercase(Locale.ROOT),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 1.2.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = PremiumTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable(onClick = onClickTicker)
                                )
                            }
                        }
                    }

                    // Lado direito: Ações compactas em cima + Rentabilidade abaixo (sem roubar espaço do Ticker)
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = onOpenCadastroAndEdit,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("ticker_edit_button_${pos.ticker}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Acessar cadastro e editar ${pos.ticker}",
                                    tint = ConfirmedBlue,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("delete_position_button_${pos.ticker}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Remover ${pos.ticker}",
                                    tint = PremiumTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onClickTicker)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Rentabilidade",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                color = PremiumTextSecondary,
                                maxLines = 1
                            )
                            Text(
                                text = if (pos.hasMarketPrice) {
                                    val arrow = if (isPositiveReturn) "▲ " else "▼ "
                                    arrow + formatPct(pos.profitLossPercent)
                                } else {
                                    "Sem cotação"
                                },
                                style = MonoValueStyle.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = returnColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // ==================== GRADE 3x2 DE INDICADORES CLICÁVEIS ====================
            // Primeira linha: Preço Atual | Dividendos | Data Pagamento
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val dayVarText = indicators.dayChangePercent?.let { ch ->
                    val prefix = if (ch >= 0.0) "+" else ""
                    "$prefix${String.format(Locale("pt", "BR"), "%.2f%%", ch)} hoje"
                }
                val dayVarColor = when {
                    indicators.dayChangePercent == null -> PremiumTextSecondary
                    indicators.dayChangePercent >= 0.0 -> ReceivedGreen
                    else -> CancelledRed
                }

                ClickableMetricTile(
                    icon = Icons.Default.ShowChart,
                    iconTint = ConfirmedBlue,
                    title = "Preço Atual",
                    value = if (pos.hasMarketPrice && pos.currentPrice > 0.0) formatBrl(pos.currentPrice) else "—",
                    subLabel = dayVarText,
                    subLabelColor = dayVarColor,
                    onClick = { onOpenMetricDetail(AssetDetailTab.QUOTES) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("asset_metric_price_${pos.ticker}")
                )

                val divBadgeColor = when (indicators.dividendStatus) {
                    DividendStatus.RECEIVED -> ReceivedGreen
                    DividendStatus.CONFIRMED -> ConfirmedBlue
                    DividendStatus.PROJECTED -> ProjectedAmber
                    else -> PremiumTextSecondary
                }
                ClickableMetricTile(
                    icon = Icons.Default.Paid,
                    iconTint = ProjectedAmber,
                    title = "Dividendos",
                    value = indicators.dividendValueBrl?.let { formatBrl(it) } ?: "—",
                    subLabel = indicators.dividendStatusLabel,
                    subLabelColor = divBadgeColor,
                    onClick = { onOpenMetricDetail(AssetDetailTab.DIVIDENDS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("asset_metric_dividends_${pos.ticker}")
                )

                ClickableMetricTile(
                    icon = Icons.Default.CalendarMonth,
                    iconTint = ConfirmedBlue,
                    title = "Data Pgto",
                    value = indicators.dividendDateLabel,
                    subLabel = if (indicators.dividendDateLabel == "—") null
                    else if (indicators.dividendDateIsEffective) "Efetiva" else "Prevista",
                    subLabelColor = if (indicators.dividendDateIsEffective) ReceivedGreen else ProjectedAmber,
                    onClick = { onOpenMetricDetail(AssetDetailTab.DIVIDEND_CALENDAR) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("asset_metric_div_date_${pos.ticker}")
                )
            }

            // Segunda linha: Quantidade | Preço Médio | Valor Atual
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val formattedQty = if (pos.quantity % 1.0 == 0.0) {
                    String.format(Locale("pt", "BR"), "%.0f", pos.quantity)
                } else {
                    String.format(Locale("pt", "BR"), "%.2f", pos.quantity)
                }
                ClickableMetricTile(
                    icon = Icons.Default.Inventory2,
                    iconTint = ConfirmedBlue,
                    title = "Quantidade",
                    value = formattedQty,
                    subLabel = "Moviment.",
                    subLabelColor = PremiumTextSecondary,
                    onClick = { onOpenMetricDetail(AssetDetailTab.POSITION_MOVEMENTS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("asset_metric_quantity_${pos.ticker}")
                )

                ClickableMetricTile(
                    icon = Icons.Default.Calculate,
                    iconTint = ConfirmedBlue,
                    title = "Preço Médio",
                    value = formatBrl(pos.averagePrice),
                    subLabel = "Composição",
                    subLabelColor = PremiumTextSecondary,
                    onClick = { onOpenMetricDetail(AssetDetailTab.AVERAGE_PRICE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("asset_metric_avg_price_${pos.ticker}")
                )

                ClickableMetricTile(
                    icon = Icons.Default.AccountBalanceWallet,
                    iconTint = ConfirmedBlue,
                    title = "Valor Atual",
                    value = formatBrl(pos.totalCurrentBrl),
                    subLabel = "Evolução",
                    subLabelColor = returnColor,
                    onClick = { onOpenMetricDetail(AssetDetailTab.EQUITY_EVOLUTION) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("asset_metric_current_value_${pos.ticker}")
                )
            }
        }
    }
}

@Composable
private fun ClickableMetricTile(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    subLabel: String?,
    subLabelColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (pressed) 0.96f else 1f, label = "metric_tile_scale")

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkPremiumCardSecondary.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, DarkPremiumBorder.copy(alpha = 0.95f)),
        modifier = modifier
            .scale(scale)
            .heightIn(min = 66.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = ConfirmedBlue),
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = PremiumTextSecondary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = value,
                    style = MonoValueStyle.copy(
                        fontSize = 11.sp,
                        letterSpacing = (-0.3).sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = PremiumTextPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Abrir $title",
                    tint = ConfirmedBlue.copy(alpha = 0.78f),
                    modifier = Modifier.size(12.dp)
                )
            }

            if (!subLabel.isNullOrBlank()) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = subLabelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CompactReturnSparkline(
    points: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val minVal = points.minOrNull() ?: 0.0
        val maxVal = points.maxOrNull() ?: 1.0
        val range = (maxVal - minVal).takeIf { it > 0.0001 } ?: 1.0
        val pad = 6f
        val usableW = (size.width - pad * 2f).coerceAtLeast(1f)
        val usableH = (size.height - pad * 2f).coerceAtLeast(1f)
        val stepX = usableW / (points.size - 1).toFloat()

        val linePath = Path()
        val fillPath = Path()
        var lastOffset = Offset.Zero

        points.forEachIndexed { idx, value ->
            val x = pad + idx * stepX
            val normalized = ((value - minVal) / range).toFloat().coerceIn(0f, 1f)
            val y = pad + usableH * (1f - normalized)
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height - pad)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            lastOffset = Offset(x, y)
        }
        fillPath.lineTo(lastOffset.x, size.height - pad)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                startY = pad,
                endY = size.height
            )
        )
        drawPath(
            path = linePath,
            color = lineColor.copy(alpha = 0.72f),
            style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Ponto luminoso na extremidade direita
        drawCircle(
            color = lineColor.copy(alpha = 0.42f),
            radius = 6f,
            center = lastOffset
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = 2.8f,
            center = lastOffset
        )
    }
}

private fun assetClassVisual(assetClass: AssetClass): Pair<ImageVector, Color> = when (assetClass) {
    AssetClass.FIAGRO -> Icons.Default.Agriculture to ReceivedGreen
    AssetClass.FII -> Icons.Default.Apartment to ConfirmedBlue
    AssetClass.ACOES_BR -> Icons.Default.ShowChart to TechBluePrimary
    AssetClass.ETF -> Icons.Default.PieChart to VioletAccent
    AssetClass.RENDA_FIXA -> Icons.Default.AccountBalance to ProjectedAmber
    AssetClass.CRIPTO -> Icons.Default.CurrencyBitcoin to ProjectedAmber
}

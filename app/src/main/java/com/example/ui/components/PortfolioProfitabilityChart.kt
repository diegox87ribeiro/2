package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PortfolioOperationEntity
import com.example.domain.model.BenchmarkSeries
import com.example.domain.model.MarketAsset
import com.example.domain.model.PortfolioItemView
import com.example.domain.model.PortfolioProfitabilityCalculator
import com.example.domain.model.PortfolioProfitabilitySeries
import com.example.domain.model.ProfitabilityEvolutionPoint
import com.example.domain.model.ProfitabilityRange
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CardElevated
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Componente interativo de Dashboard (equivalente nativo em Jetpack Compose ao AreaChart/LineChart
 * do Recharts) para acompanhar a evolução da rentabilidade acumulada da carteira ao longo do tempo,
 * com filtro de período (3M, 6M, 1A, Tudo), tooltip interativo ao tocar/arrastar, linha de referência 0%
 * e comparação com Rentabilidade Total (+ Dividendos), CDI e Ibovespa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortfolioProfitabilityEvolutionCard(
    realPositions: List<PortfolioItemView>,
    marketAssets: List<MarketAsset>,
    operations: List<PortfolioOperationEntity>,
    benchmarks: List<BenchmarkSeries>,
    onGoImport: () -> Unit,
    onLoadDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedRange by remember { mutableStateOf(ProfitabilityRange.Y1) }

    val series = remember(realPositions, marketAssets, operations, benchmarks, selectedRange) {
        PortfolioProfitabilityCalculator.compute(
            realPositions = realPositions,
            marketAssets = marketAssets,
            operations = operations,
            benchmarks = benchmarks,
            range = selectedRange
        )
    }

    FintechCard(
        modifier = modifier.testTag("dashboard_profitability_evolution_card"),
        borderColor = if (series.points.isNotEmpty()) EmeraldPrimary.copy(alpha = 0.28f) else SubtleBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Evolução da Rentabilidade da Carteira",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (series.isDemoData) {
                        "Retorno acumulado (%) vs. CDI e Ibovespa • Base demonstrativa"
                    } else {
                        "Retorno acumulado (%) ao longo do tempo vs. CDI e Ibovespa"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ShowChart,
                contentDescription = "Gráfico de evolução da rentabilidade",
                tint = EmeraldPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        if (series.points.isEmpty()) {
            ProfitabilityEmptyState(
                onGoImport = onGoImport,
                onLoadDemo = onLoadDemo
            )
            return@FintechCard
        }

        // Seletor de horizonte temporal (3M, 6M, 1A, Tudo)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProfitabilityRange.entries.forEach { range ->
                val isSelected = range == selectedRange
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) EmeraldPrimary.copy(alpha = 0.18f) else CardElevated.copy(alpha = 0.6f),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) EmeraldPrimary.copy(alpha = 0.7f) else SubtleBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .testTag("profitability_range_${range.name}")
                        .clickable { selectedRange = range }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = range.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) EmeraldPrimary else TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // KPIs rápidos de rentabilidade do período
        ProfitabilitySummaryStrip(series = series)

        Spacer(Modifier.height(12.dp))

        // Gráfico de Área + Linhas estilo Recharts com Tooltip interativo, Legenda clicável e Crosshair
        InteractiveProfitabilityAreaChart(series = series)
    }
}

@Composable
private fun ProfitabilityEmptyState(
    onGoImport: () -> Unit,
    onLoadDemo: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DeepNavySurface,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profitability_empty_state")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Importe ou cadastre seus ativos para visualizar a curva de rentabilidade.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGoImport,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = EmeraldOnPrimary
                    )
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Importar Carteira")
                }
                OutlinedButton(
                    onClick = onLoadDemo,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Carregar Demo")
                }
            }
        }
    }
}

@Composable
private fun ProfitabilitySummaryStrip(series: PortfolioProfitabilitySeries) {
    val totalColor = if (series.totalReturnWithDividendsPct >= 0.0) EmeraldPrimary else CoralRed
    val alphaPrefix = if (series.alphaVsCdiPct >= 0.0) "+" else ""
    val alphaColor = if (series.alphaVsCdiPct >= 0.0) EmeraldPrimary else AmberGold
    val ddColor = if (series.maxDrawdownPct <= -8.0) CoralRed else AmberGold

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryStatPill(
            label = "Retorno Total",
            value = formatPct(series.totalReturnWithDividendsPct),
            subLabel = "$alphaPrefix${String.format(Locale("pt", "BR"), "%.1f", series.alphaVsCdiPct)} pp vs CDI",
            valueColor = totalColor,
            subColor = alphaColor,
            modifier = Modifier.weight(1f)
        )
        SummaryStatPill(
            label = "Resultado + Div.",
            value = formatCompactBrlSigned(series.totalProfitBrl),
            subLabel = "Div: ${formatCompactBrl(series.totalDividendsBrl)}",
            valueColor = if (series.totalProfitBrl >= 0.0) ElectricCyan else CoralRed,
            subColor = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        SummaryStatPill(
            label = "Max Drawdown",
            value = String.format(Locale("pt", "BR"), "%.1f%%", series.maxDrawdownPct),
            subLabel = "CDI: ${formatPct(series.cdiReturnPct)}",
            valueColor = ddColor,
            subColor = TextSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryStatPill(
    label: String,
    value: String,
    subLabel: String,
    valueColor: Color,
    subColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = DeepNavySurface,
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1
            )
            Text(
                text = value,
                style = MonoValueStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = valueColor,
                maxLines = 1
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = subColor,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveProfitabilityAreaChart(
    series: PortfolioProfitabilitySeries,
    modifier: Modifier = Modifier
) {
    val points = series.points
    if (points.size < 2) return

    var selectedIdx by remember(points) { mutableIntStateOf(points.lastIndex) }
    val safeIdx = selectedIdx.coerceIn(0, points.lastIndex)
    val selectedPoint = points[safeIdx]

    var showPortfolio by remember { mutableStateOf(true) }
    var showTotalReturn by remember { mutableStateOf(true) }
    var showCdi by remember { mutableStateOf(true) }
    var showIbov by remember { mutableStateOf(true) }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(points, showPortfolio, showTotalReturn, showCdi, showIbov) {
        animProgress.snapTo(0.15f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
        )
    }

    val (axisMin, axisMax) = remember(points, showPortfolio, showTotalReturn, showCdi, showIbov) {
        val vals = mutableListOf(0.0)
        points.forEach { p ->
            if (showPortfolio) vals.add(p.portfolioReturnPct)
            if (showTotalReturn) vals.add(p.portfolioWithDividendsPct)
            if (showCdi) vals.add(p.cdiReturnPct)
            if (showIbov) vals.add(p.ibovReturnPct)
        }
        val rawMin = vals.minOrNull() ?: -2.0
        val rawMax = vals.maxOrNull() ?: 5.0
        val span = (rawMax - rawMin).coerceAtLeast(4.0)
        val pad = span * 0.14
        val niceMin = floor((rawMin - pad) / 2.0) * 2.0
        val niceMax = ceil((rawMax + pad) / 2.0) * 2.0
        niceMin to niceMax.coerceAtLeast(niceMin + 2.0)
    }

    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier.fillMaxWidth()) {
        // Tooltip interativo estilo Recharts
        ProfitabilityTooltipCard(
            point = selectedPoint,
            showPortfolio = showPortfolio,
            showTotalReturn = showTotalReturn,
            showCdi = showCdi,
            showIbov = showIbov
        )

        Spacer(Modifier.height(10.dp))

        // Legenda clicável interativa
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProfitabilityLegendChip(
                label = "Total (+Div.)",
                color = EmeraldPrimary,
                active = showTotalReturn,
                onClick = {
                    if (showTotalReturn && !showPortfolio && !showCdi && !showIbov) return@ProfitabilityLegendChip
                    showTotalReturn = !showTotalReturn
                },
                modifier = Modifier.testTag("profitability_legend_total")
            )
            ProfitabilityLegendChip(
                label = "Carteira",
                color = ElectricCyan,
                active = showPortfolio,
                onClick = {
                    if (showPortfolio && !showTotalReturn && !showCdi && !showIbov) return@ProfitabilityLegendChip
                    showPortfolio = !showPortfolio
                },
                modifier = Modifier.testTag("profitability_legend_portfolio")
            )
            ProfitabilityLegendChip(
                label = "CDI",
                color = AmberGold,
                active = showCdi,
                isDashed = true,
                onClick = {
                    if (showCdi && !showPortfolio && !showTotalReturn && !showIbov) return@ProfitabilityLegendChip
                    showCdi = !showCdi
                },
                modifier = Modifier.testTag("profitability_legend_cdi")
            )
            ProfitabilityLegendChip(
                label = "IBOV",
                color = VioletAccent,
                active = showIbov,
                isDashed = true,
                onClick = {
                    if (showIbov && !showPortfolio && !showTotalReturn && !showCdi) return@ProfitabilityLegendChip
                    showIbov = !showIbov
                },
                modifier = Modifier.testTag("profitability_legend_ibov")
            )
        }

        Spacer(Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(228.dp)
                .testTag("profitability_evolution_canvas")
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val leftPad = 46.dp.toPx()
                        val rightPad = 10.dp.toPx()
                        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                        val ratio = ((offset.x - leftPad) / plotW).coerceIn(0f, 1f)
                        selectedIdx = (ratio * (points.size - 1) + 0.5f).toInt().coerceIn(0, points.lastIndex)
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val leftPad = 46.dp.toPx()
                        val rightPad = 10.dp.toPx()
                        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                        val ratio = ((change.position.x - leftPad) / plotW).coerceIn(0f, 1f)
                        selectedIdx = (ratio * (points.size - 1) + 0.5f).toInt().coerceIn(0, points.lastIndex)
                    }
                }
        ) {
            val leftPad = 46.dp.toPx()
            val rightPad = 10.dp.toPx()
            val topPad = 14.dp.toPx()
            val bottomPad = 26.dp.toPx()

            val plotLeft = leftPad
            val plotRight = (size.width - rightPad).coerceAtLeast(plotLeft + 24f)
            val plotTop = topPad
            val plotBottom = (size.height - bottomPad).coerceAtLeast(plotTop + 24f)
            val plotW = plotRight - plotLeft
            val plotH = plotBottom - plotTop
            val rangeVal = (axisMax - axisMin).coerceAtLeast(0.1)

            fun xAt(idx: Int): Float = plotLeft + (idx.toFloat() / (points.size - 1).toFloat()) * plotW
            fun yAt(pct: Double): Float {
                val animatedPct = pct * animProgress.value
                val norm = ((animatedPct - axisMin) / rangeVal).toFloat().coerceIn(0f, 1f)
                return plotBottom - (norm * plotH)
            }
            fun yAtStatic(pct: Double): Float {
                val norm = ((pct - axisMin) / rangeVal).toFloat().coerceIn(0f, 1f)
                return plotBottom - (norm * plotH)
            }

            val axisStyle = TextStyle(
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            val dashGrid = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            val dashBench = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

            // 1) CartesianGrid horizontal + labels do Eixo Y (%)
            val yTicks = 4
            for (step in 0..yTicks) {
                val frac = step / yTicks.toFloat()
                val tickVal = axisMin + (axisMax - axisMin) * frac
                val y = plotBottom - frac * plotH
                drawLine(
                    color = SubtleBorder.copy(alpha = 0.45f),
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1f,
                    pathEffect = dashGrid
                )
                val sign = if (tickVal > 0.05) "+" else ""
                val tickLabel = "$sign${String.format(Locale("pt", "BR"), "%.0f%%", tickVal)}"
                val measured = textMeasurer.measure(tickLabel, axisStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = (plotLeft - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                        y = (y - measured.size.height / 2f).coerceIn(0f, size.height - measured.size.height)
                    )
                )
            }

            // 2) ReferenceLine de 0% (ponto de equilíbrio)
            if (0.0 in axisMin..axisMax) {
                val zeroY = yAtStatic(0.0)
                drawLine(
                    color = TextSecondary.copy(alpha = 0.55f),
                    start = Offset(plotLeft, zeroY),
                    end = Offset(plotRight, zeroY),
                    strokeWidth = 1.4.dp.toPx()
                )
            }

            // Função auxiliar para construir caminho suave monotônico (estilo Recharts type="monotone")
            fun buildSmoothLinePath(values: List<Double>): Path {
                val path = Path()
                values.forEachIndexed { idx, v ->
                    val x = xAt(idx)
                    val y = yAt(v)
                    if (idx == 0) {
                        path.moveTo(x, y)
                    } else {
                        val prevX = xAt(idx - 1)
                        val prevY = yAt(values[idx - 1])
                        val midX = (prevX + x) / 2f
                        path.cubicTo(midX, prevY, midX, y, x, y)
                    }
                }
                return path
            }

            // 3) Preenchimento em gradiente sob a curva principal (Total + Div. ou Carteira)
            val primaryAreaValues = when {
                showTotalReturn -> points.map { it.portfolioWithDividendsPct }
                showPortfolio -> points.map { it.portfolioReturnPct }
                else -> null
            }
            val primaryAreaColor = if (showTotalReturn) EmeraldPrimary else ElectricCyan
            if (primaryAreaValues != null) {
                val areaPath = buildSmoothLinePath(primaryAreaValues).apply {
                    lineTo(xAt(points.lastIndex), plotBottom)
                    lineTo(xAt(0), plotBottom)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryAreaColor.copy(alpha = 0.28f),
                            primaryAreaColor.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        startY = plotTop,
                        endY = plotBottom
                    )
                )
            }

            // 4) Curvas de Benchmark (CDI e IBOV tracejadas)
            if (showCdi) {
                val cdiPath = buildSmoothLinePath(points.map { it.cdiReturnPct })
                drawPath(
                    path = cdiPath,
                    color = AmberGold.copy(alpha = 0.85f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = dashBench
                    )
                )
            }

            if (showIbov) {
                val ibovPath = buildSmoothLinePath(points.map { it.ibovReturnPct })
                drawPath(
                    path = ibovPath,
                    color = VioletAccent.copy(alpha = 0.85f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = dashBench
                    )
                )
            }

            // 5) Curva Carteira (apenas valorização)
            if (showPortfolio) {
                val portPath = buildSmoothLinePath(points.map { it.portfolioReturnPct })
                drawPath(
                    path = portPath,
                    color = ElectricCyan,
                    style = Stroke(
                        width = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 6) Curva Rentabilidade Total (+ Dividendos) em destaque
            if (showTotalReturn) {
                val totalPath = buildSmoothLinePath(points.map { it.portfolioWithDividendsPct })
                drawPath(
                    path = totalPath,
                    color = EmeraldPrimary,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 7) Crosshair vertical interativo no ponto selecionado
            val selX = xAt(safeIdx)
            drawLine(
                color = EmeraldPrimary.copy(alpha = 0.55f),
                start = Offset(selX, plotTop),
                end = Offset(selX, plotBottom),
                strokeWidth = 1.4.dp.toPx(),
                pathEffect = dashGrid
            )

            // ActiveDots estilo Recharts no ponto selecionado
            fun drawActiveDot(value: Double, color: Color, radiusDp: Float) {
                val cy = yAt(value)
                drawCircle(
                    color = color.copy(alpha = 0.24f),
                    radius = (radiusDp + 4f).dp.toPx(),
                    center = Offset(selX, cy)
                )
                drawCircle(
                    color = ObsidianBg,
                    radius = (radiusDp + 1.5f).dp.toPx(),
                    center = Offset(selX, cy)
                )
                drawCircle(
                    color = color,
                    radius = radiusDp.dp.toPx(),
                    center = Offset(selX, cy)
                )
            }

            if (showIbov) drawActiveDot(selectedPoint.ibovReturnPct, VioletAccent, 3.8f)
            if (showCdi) drawActiveDot(selectedPoint.cdiReturnPct, AmberGold, 3.8f)
            if (showPortfolio) drawActiveDot(selectedPoint.portfolioReturnPct, ElectricCyan, 4.4f)
            if (showTotalReturn) drawActiveDot(selectedPoint.portfolioWithDividendsPct, EmeraldPrimary, 5.2f)

            // 8) Rótulos do Eixo X (Mês/Ano) evitando sobreposição
            val labelStep = when {
                points.size <= 4 -> 1
                points.size <= 7 -> 1
                else -> 2
            }
            points.forEachIndexed { idx, pt ->
                val isSelected = idx == safeIdx
                val shouldDraw = isSelected || idx == 0 || idx == points.lastIndex || (idx % labelStep == 0 && abs(idx - safeIdx) > 0)
                if (!shouldDraw) return@forEachIndexed

                val xText = if (points.size > 6 && !isSelected) pt.label.substringBefore("/") else pt.label
                val xStyle = TextStyle(
                    color = if (isSelected) EmeraldPrimary else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                val measured = textMeasurer.measure(xText, xStyle)
                val cx = xAt(idx)
                val labelX = (cx - measured.size.width / 2f).coerceIn(plotLeft, plotRight - measured.size.width)
                val labelY = plotBottom + 6.dp.toPx()

                if (isSelected) {
                    drawRoundRect(
                        color = EmeraldPrimary.copy(alpha = 0.16f),
                        topLeft = Offset(labelX - 5.dp.toPx(), labelY - 2.dp.toPx()),
                        size = Size(measured.size.width + 10.dp.toPx(), measured.size.height + 4.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(labelX, labelY)
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Toque ou deslize o dedo sobre o gráfico para inspecionar cada mês • Toque na legenda para filtrar séries",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfitabilityTooltipCard(
    point: ProfitabilityEvolutionPoint,
    showPortfolio: Boolean,
    showTotalReturn: Boolean,
    showCdi: Boolean,
    showIbov: Boolean
) {
    val diffCdi = point.portfolioWithDividendsPct - point.cdiReturnPct
    val badgeColor = if (diffCdi >= 0.0) EmeraldPrimary else AmberGold
    val badgeText = if (diffCdi >= 0.0) {
        "+${String.format(Locale("pt", "BR"), "%.1f", diffCdi)} pp vs CDI"
    } else {
        "${String.format(Locale("pt", "BR"), "%.1f", diffCdi)} pp vs CDI"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profitability_chart_tooltip"),
        shape = RoundedCornerShape(12.dp),
        color = DeepNavySurface,
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = point.fullLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Patrimônio est.: ${formatBrl(point.equityBrl)}" +
                            if (point.accumulatedDividendsBrl > 0.0) " • Div. acum.: ${formatBrl(point.accumulatedDividendsBrl)}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TooltipMetricRow("Total (+Div.)", formatPct(point.portfolioWithDividendsPct), EmeraldPrimary, showTotalReturn)
                TooltipMetricRow("Carteira", formatPct(point.portfolioReturnPct), ElectricCyan, showPortfolio)
                TooltipMetricRow("CDI", formatPct(point.cdiReturnPct), AmberGold, showCdi)
                TooltipMetricRow("IBOV", formatPct(point.ibovReturnPct), VioletAccent, showIbov)
            }
        }
    }
}

@Composable
private fun TooltipMetricRow(
    label: String,
    value: String,
    color: Color,
    enabled: Boolean
) {
    val alpha = if (enabled) 1f else 0.35f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha))
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary.copy(alpha = alpha)
        )
        Text(
            text = value,
            style = MonoValueStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = color.copy(alpha = alpha)
        )
    }
}

@Composable
private fun ProfitabilityLegendChip(
    label: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDashed: Boolean = false
) {
    Surface(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (active) color.copy(alpha = 0.14f) else Color.Transparent,
        border = BorderStroke(1.dp, if (active) color.copy(alpha = 0.45f) else SubtleBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDashed) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(2.5.dp)
                        .background(if (active) color else TextSecondary.copy(alpha = 0.45f))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (active) color else TextSecondary.copy(alpha = 0.45f))
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) MaterialTheme.colorScheme.onSurface else TextSecondary
            )
        }
    }
}

private fun formatCompactBrl(value: Double): String {
    val absVal = abs(value)
    return when {
        absVal >= 1_000_000.0 -> String.format(Locale("pt", "BR"), "R$ %.1fM", value / 1_000_000.0)
        absVal >= 10_000.0 -> String.format(Locale("pt", "BR"), "R$ %.1fk", value / 1_000.0)
        else -> formatBrl(value)
    }
}

private fun formatCompactBrlSigned(value: Double): String {
    val prefix = if (value > 0.005) "+" else ""
    return "$prefix${formatCompactBrl(value)}"
}

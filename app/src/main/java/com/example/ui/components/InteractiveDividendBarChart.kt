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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DividendMonitorCalculator
import com.example.domain.model.MonthlyDividendPoint
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyDividendBarChart(
    points: List<MonthlyDividendPoint>,
    modifier: Modifier = Modifier,
    selectedMonthIndex: Int? = null,
    onSelectMonth: ((Int) -> Unit)? = null
) {
    if (points.isEmpty()) return

    var internalIdx by remember(points) { mutableIntStateOf(points.lastIndex.coerceAtLeast(0)) }
    val safeIdx = (selectedMonthIndex ?: internalIdx).coerceIn(0, points.lastIndex)
    val selected = points[safeIdx]

    var showProjected by remember { mutableStateOf(true) }
    var showReceived by remember { mutableStateOf(true) }
    var showAverageLine by remember { mutableStateOf(true) }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(points, showProjected, showReceived) {
        animProgress.snapTo(0.15f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
        )
    }

    val rawMax = remember(points, showProjected, showReceived) {
        points.maxOfOrNull { pt ->
            maxOf(
                if (showProjected) pt.projectedBrl else 0.0,
                if (showReceived) pt.receivedBrl else 0.0
            )
        } ?: 0.0
    }
    val axisMax = remember(rawMax) { computeNiceAxisMax(rawMax) }

    val avgReferenceBrl = remember(points, showProjected, showReceived) {
        val values = when {
            showProjected -> points.map { it.projectedBrl }
            showReceived -> points.map { it.receivedBrl }
            else -> emptyList()
        }
        if (values.isEmpty()) 0.0 else values.sum() / values.size.toDouble()
    }

    val selectIndex: (Int) -> Unit = { idx ->
        val clamped = idx.coerceIn(0, points.lastIndex)
        internalIdx = clamped
        onSelectMonth?.invoke(clamped)
    }

    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier.fillMaxWidth()) {
        // Tooltip interativo estilo Recharts para o mês selecionado
        DividendChartTooltipCard(
            point = selected,
            showProjected = showProjected,
            showReceived = showReceived,
            averageBrl = if (showAverageLine) avgReferenceBrl else null
        )

        Spacer(Modifier.height(10.dp))

        // Legenda clicável interativa (liga/desliga séries Projetado, Recebido e Média)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChartLegendToggleChip(
                label = "Projetado",
                color = ElectricCyan,
                active = showProjected,
                onClick = {
                    if (showProjected && !showReceived) return@ChartLegendToggleChip
                    showProjected = !showProjected
                },
                modifier = Modifier.testTag("dividend_chart_legend_projected")
            )
            ChartLegendToggleChip(
                label = "Recebido",
                color = EmeraldPrimary,
                active = showReceived,
                onClick = {
                    if (showReceived && !showProjected) return@ChartLegendToggleChip
                    showReceived = !showReceived
                },
                modifier = Modifier.testTag("dividend_chart_legend_received")
            )
            ChartLegendToggleChip(
                label = "Média (${formatAxisCurrency(avgReferenceBrl)})",
                color = AmberGold,
                active = showAverageLine,
                isDashed = true,
                onClick = { showAverageLine = !showAverageLine },
                modifier = Modifier.testTag("dividend_chart_legend_average")
            )
        }

        Spacer(Modifier.height(10.dp))

        // Área do gráfico cartesiano com eixo Y, grade tracejada, barras arredondadas com gradiente e toque/arraste
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(216.dp)
                .testTag("dividend_interactive_bar_canvas")
                .pointerInput(points, onSelectMonth) {
                    detectTapGestures { offset ->
                        val plotLeft = 50.dp.toPx()
                        val plotWidth = (size.width - plotLeft - 6.dp.toPx()).coerceAtLeast(1f)
                        val relX = (offset.x - plotLeft).coerceIn(0f, plotWidth)
                        val idx = ((relX / plotWidth) * points.size).toInt().coerceIn(0, points.lastIndex)
                        selectIndex(idx)
                    }
                }
                .pointerInput(points, onSelectMonth) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val plotLeft = 50.dp.toPx()
                        val plotWidth = (size.width - plotLeft - 6.dp.toPx()).coerceAtLeast(1f)
                        val relX = (change.position.x - plotLeft).coerceIn(0f, plotWidth)
                        val idx = ((relX / plotWidth) * points.size).toInt().coerceIn(0, points.lastIndex)
                        selectIndex(idx)
                    }
                }
        ) {
            val leftPad = 50.dp.toPx()
            val rightPad = 6.dp.toPx()
            val topPad = 14.dp.toPx()
            val bottomPad = 26.dp.toPx()

            val plotLeft = leftPad
            val plotRight = (size.width - rightPad).coerceAtLeast(plotLeft + 24f)
            val plotTop = topPad
            val plotBottom = (size.height - bottomPad).coerceAtLeast(plotTop + 24f)
            val plotWidth = plotRight - plotLeft
            val plotHeight = plotBottom - plotTop

            val axisTextStyle = TextStyle(
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

            // 1) CartesianGrid horizontal + ticks do Eixo Y (0%, 33%, 66%, 100%)
            val yTicks = 3
            for (step in 0..yTicks) {
                val fraction = step / yTicks.toFloat()
                val y = plotBottom - (fraction * plotHeight)
                val tickVal = axisMax * fraction

                drawLine(
                    color = if (step == 0) SubtleBorder else SubtleBorder.copy(alpha = 0.45f),
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = if (step == 0) 1.5f else 1f,
                    pathEffect = if (step == 0) null else dashEffect
                )

                val tickLabel = formatAxisCurrency(tickVal)
                val measured = textMeasurer.measure(tickLabel, axisTextStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = (plotLeft - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                        y = (y - measured.size.height / 2f).coerceIn(0f, size.height - measured.size.height)
                    )
                )
            }

            // 2) Colunas e barras agrupadas (Projetado vs Recebido) com animação e cantos superiores arredondados
            val colW = plotWidth / points.size.toFloat()
            val bothVisible = showProjected && showReceived
            val barW = if (bothVisible) {
                (colW * 0.34f).coerceIn(4.dp.toPx(), 14.dp.toPx())
            } else {
                (colW * 0.52f).coerceIn(6.dp.toPx(), 20.dp.toPx())
            }
            val barGap = if (bothVisible) 2.dp.toPx() else 0f
            val cornerRadiusPx = 4.dp.toPx()
            val progress = animProgress.value

            points.forEachIndexed { idx, pt ->
                val colLeft = plotLeft + idx * colW
                val centerX = colLeft + colW / 2f
                val isSelected = idx == safeIdx

                // Highlight vertical sutil na coluna ativa (como Cursor do Recharts)
                if (isSelected) {
                    drawRoundRect(
                        color = EmeraldPrimary.copy(alpha = 0.09f),
                        topLeft = Offset(colLeft + 1.dp.toPx(), plotTop),
                        size = Size((colW - 2.dp.toPx()).coerceAtLeast(2f), plotHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }

                val projH = if (showProjected && pt.projectedBrl > 0.0) {
                    ((pt.projectedBrl / axisMax) * plotHeight * progress).toFloat().coerceAtLeast(3.dp.toPx())
                } else 0f
                val recH = if (showReceived && pt.receivedBrl > 0.0) {
                    ((pt.receivedBrl / axisMax) * plotHeight * progress).toFloat().coerceAtLeast(3.dp.toPx())
                } else 0f

                if (projH > 0f) {
                    val left = if (bothVisible) centerX - barW - barGap / 2f else centerX - barW / 2f
                    val top = (plotBottom - projH).coerceAtLeast(plotTop)
                    val topAlpha = if (isSelected) 1.0f else 0.72f
                    val bottomAlpha = if (isSelected) 0.55f else 0.30f
                    val barPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(left, top, left + barW, plotBottom),
                                topLeft = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                                topRight = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                                bottomLeft = CornerRadius.Zero,
                                bottomRight = CornerRadius.Zero
                            )
                        )
                    }
                    drawPath(
                        path = barPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ElectricCyan.copy(alpha = topAlpha),
                                ElectricCyan.copy(alpha = bottomAlpha)
                            ),
                            startY = top,
                            endY = plotBottom
                        )
                    )
                }

                if (recH > 0f) {
                    val left = if (bothVisible) centerX + barGap / 2f else centerX - barW / 2f
                    val top = (plotBottom - recH).coerceAtLeast(plotTop)
                    val topAlpha = if (isSelected) 1.0f else 0.78f
                    val bottomAlpha = if (isSelected) 0.62f else 0.35f
                    val barPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(left, top, left + barW, plotBottom),
                                topLeft = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                                topRight = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                                bottomLeft = CornerRadius.Zero,
                                bottomRight = CornerRadius.Zero
                            )
                        )
                    }
                    drawPath(
                        path = barPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                EmeraldPrimary.copy(alpha = topAlpha),
                                EmeraldPrimary.copy(alpha = bottomAlpha)
                            ),
                            startY = top,
                            endY = plotBottom
                        )
                    )
                }

                // Rótulo do Eixo X (Mês) alinhado sob cada coluna
                val xLabel = if (colW >= 26.dp.toPx()) pt.monthLabel else pt.monthLabel.take(1)
                val xStyle = TextStyle(
                    color = if (isSelected) EmeraldPrimary else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                val xMeasured = textMeasurer.measure(xLabel, xStyle)
                val labelX = (centerX - xMeasured.size.width / 2f).coerceIn(plotLeft, plotRight - xMeasured.size.width)
                val labelY = plotBottom + 6.dp.toPx()

                if (isSelected) {
                    drawRoundRect(
                        color = EmeraldPrimary.copy(alpha = 0.16f),
                        topLeft = Offset(labelX - 4.dp.toPx(), labelY - 2.dp.toPx()),
                        size = Size(xMeasured.size.width + 8.dp.toPx(), xMeasured.size.height + 4.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
                drawText(
                    textLayoutResult = xMeasured,
                    topLeft = Offset(labelX, labelY)
                )
            }

            // 3) ReferenceLine de Média Mensal
            if (showAverageLine && avgReferenceBrl > 0.0 && axisMax > 0.0) {
                val avgY = (plotBottom - ((avgReferenceBrl / axisMax) * plotHeight).toFloat())
                    .coerceIn(plotTop, plotBottom)
                drawLine(
                    color = AmberGold.copy(alpha = 0.85f),
                    start = Offset(plotLeft, avgY),
                    end = Offset(plotRight, avgY),
                    strokeWidth = 1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Toque ou deslize o dedo sobre as barras para comparar cada mês • Toque na legenda para filtrar séries",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun DividendChartTooltipCard(
    point: MonthlyDividendPoint,
    showProjected: Boolean,
    showReceived: Boolean,
    averageBrl: Double?
) {
    val fullMonth = DividendMonitorCalculator.monthFullNamesPt.getOrElse(point.monthIndex) { point.monthLabel }
    val diff = point.receivedBrl - point.projectedBrl
    val pct = if (point.projectedBrl > 0.0) (point.receivedBrl / point.projectedBrl) * 100.0 else 0.0

    val (badgeText, badgeColor) = when {
        point.receivedBrl > 0.0 && point.projectedBrl > 0.0 && diff >= 0.0 ->
            "${"%.0f".format(pct)}% da meta" to EmeraldPrimary
        point.receivedBrl > 0.0 && point.projectedBrl > 0.0 ->
            "${"%.0f".format(pct)}% realizado" to AmberGold
        point.receivedBrl > 0.0 ->
            "Recebido" to EmeraldPrimary
        point.projectedBrl > 0.0 ->
            "Projetado" to ElectricCyan
        else ->
            "Sem lançamentos" to TextSecondary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dividend_chart_tooltip"),
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
                Text(
                    text = fullMonth,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
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

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = if (showProjected) 1f else 0.3f))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Projetado: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = formatBrl(point.projectedBrl),
                        style = MonoValueStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = ElectricCyan.copy(alpha = if (showProjected) 1f else 0.4f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = if (showReceived) 1f else 0.3f))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Recebido: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = formatBrl(point.receivedBrl),
                        style = MonoValueStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = EmeraldPrimary.copy(alpha = if (showReceived) 1f else 0.4f)
                    )
                }
            }

            if (point.projectedBrl > 0.0 || point.receivedBrl > 0.0) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val diffPrefix = if (diff >= 0) "+" else ""
                    val diffColor = when {
                        diff > 0.01 -> EmeraldPrimary
                        diff < -0.01 && point.receivedBrl > 0.0 -> CoralRed
                        else -> TextSecondary
                    }
                    Text(
                        text = "Diferença: $diffPrefix${formatBrl(diff)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = diffColor
                    )
                    if (averageBrl != null && averageBrl > 0.0) {
                        Text(
                            text = "Média/mês: ${formatBrl(averageBrl)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberGold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLegendToggleChip(
    label: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDashed: Boolean = false
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (active) color.copy(alpha = 0.14f) else Color.Transparent,
        border = BorderStroke(1.dp, if (active) color.copy(alpha = 0.45f) else SubtleBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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

internal fun computeNiceAxisMax(rawMax: Double): Double {
    if (rawMax <= 0.0) return 100.0
    val exponent = floor(log10(rawMax))
    val magnitude = 10.0.pow(exponent)
    val fraction = rawMax / magnitude
    val niceFraction = when {
        fraction <= 1.0 -> 1.0
        fraction <= 1.5 -> 1.5
        fraction <= 2.0 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 3.0 -> 3.0
        fraction <= 4.0 -> 4.0
        fraction <= 5.0 -> 5.0
        fraction <= 6.0 -> 6.0
        fraction <= 8.0 -> 8.0
        else -> 10.0
    }
    return ceil(niceFraction * magnitude)
}

internal fun formatAxisCurrency(value: Double): String {
    return when {
        value <= 0.0 -> "R$ 0"
        value >= 1_000_000.0 -> String.format(Locale("pt", "BR"), "R$ %.1fM", value / 1_000_000.0)
        value >= 1_000.0 -> String.format(Locale("pt", "BR"), "R$ %.1fk", value / 1_000.0)
        value >= 100.0 -> String.format(Locale("pt", "BR"), "R$ %.0f", value)
        else -> String.format(Locale("pt", "BR"), "R$ %.0f", value)
    }
}

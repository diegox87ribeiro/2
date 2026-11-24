package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.MonthlyThreeSeriesPoint
import com.example.ui.theme.ConfirmedBlue
import com.example.ui.theme.DarkPremiumBorder
import com.example.ui.theme.DarkPremiumCard
import com.example.ui.theme.DarkPremiumCardSecondary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.PremiumTextPrimary
import com.example.ui.theme.PremiumTextSecondary
import com.example.ui.theme.ProjectedAmber
import com.example.ui.theme.ReceivedGreen
import java.util.Locale
import kotlin.math.max

/**
 * Gráfico de Evolução de Proventos com 3 séries independentes:
 * - Recebido (#00D68F)
 * - Confirmado (#28B9FF)
 * - Projetado (#FFC247)
 * + Linha tracejada de Média Mensal Recebida + separador visual entre histórico e futuro.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreeSeriesDividendChart(
    points: List<MonthlyThreeSeriesPoint>,
    monthlyAverageReceivedBrl: Double,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = DarkPremiumCard,
            border = BorderStroke(1.dp, DarkPremiumBorder)
        ) {
            Text(
                text = "Sem eventos de proventos no período filtrado para exibir no gráfico.",
                style = MaterialTheme.typography.bodySmall,
                color = PremiumTextSecondary,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    var selectedIndex by remember(points) {
        val defaultIdx = points.indexOfLast { it.receivedBrl > 0.0 }
            .takeIf { it >= 0 }
            ?: points.indexOfFirst { it.confirmedBrl > 0.0 || it.projectedBrl > 0.0 }
                .takeIf { it >= 0 }
            ?: 0
        mutableIntStateOf(defaultIdx)
    }
    val safeIndex = selectedIndex.coerceIn(0, points.lastIndex)
    val selectedPoint = points[safeIndex]

    val maxVal = remember(points, monthlyAverageReceivedBrl) {
        val maxSeries = points.maxOfOrNull { it.maxSingleSeries } ?: 0.0
        max(max(maxSeries, monthlyAverageReceivedBrl), 1.0) * 1.15
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("three_series_dividend_chart"),
        shape = RoundedCornerShape(16.dp),
        color = DarkPremiumCard,
        border = BorderStroke(1.dp, DarkPremiumBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Evolução dos Proventos",
                        style = MaterialTheme.typography.titleMedium,
                        color = PremiumTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Séries independentes • toque em um mês para inspecionar",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumTextSecondary
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Legenda com as 3 séries + Média Mensal
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChartLegendItem(color = ReceivedGreen, label = "Recebido")
                ChartLegendItem(color = ConfirmedBlue, label = "Confirmado")
                ChartLegendItem(color = ProjectedAmber, label = "Projetado")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(2.dp)
                            .background(PremiumTextPrimary.copy(alpha = 0.8f))
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Média Rec. (${formatBrl(monthlyAverageReceivedBrl)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumTextSecondary
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Card interativo do mês selecionado (valores estritamente separados!)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkPremiumCardSecondary,
                border = BorderStroke(1.dp, DarkPremiumBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chart_selected_month_tooltip")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = selectedPoint.label + if (selectedPoint.isFutureMonth) " (Futuro)" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = PremiumTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Categorias não somadas",
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumTextSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniSeriesValue("Rec.", selectedPoint.receivedBrl, ReceivedGreen)
                        MiniSeriesValue("Conf.", selectedPoint.confirmedBrl, ConfirmedBlue)
                        MiniSeriesValue("Proj.", selectedPoint.projectedBrl, ProjectedAmber)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            val slotWidthDp = 56.dp
            val totalWidthDp = (points.size * 56).coerceAtLeast(310).dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.width(totalWidthDp)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(172.dp)
                            .pointerInput(points) {
                                detectTapGestures { offset ->
                                    if (points.isNotEmpty() && size.width > 0) {
                                        val slotW = size.width.toFloat() / points.size.toFloat()
                                        val tapped = (offset.x / slotW).toInt().coerceIn(0, points.lastIndex)
                                        selectedIndex = tapped
                                    }
                                }
                            }
                    ) {
                        val count = points.size
                        if (count == 0) return@Canvas
                        val slotW = size.width / count.toFloat()
                        val topPad = 14f
                        val bottomPad = 8f
                        val usableH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                        // Linhas de grade horizontais
                        val gridColor = DarkPremiumBorder.copy(alpha = 0.55f)
                        for (step in 0..3) {
                            val y = topPad + (usableH * step / 3f)
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.2f
                            )
                        }

                        // Divisor vertical sutil entre histórico/mês atual e meses futuros
                        val firstFutureIdx = points.indexOfFirst { it.isFutureMonth }
                        if (firstFutureIdx in 1 until count) {
                            val dividerX = firstFutureIdx * slotW
                            drawLine(
                                color = ProjectedAmber.copy(alpha = 0.45f),
                                start = Offset(dividerX, topPad),
                                end = Offset(dividerX, size.height - bottomPad),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            )
                        }

                        // Barras agrupadas independentes por mês (Recebido | Confirmado | Projetado)
                        points.forEachIndexed { idx, pt ->
                            val slotLeft = idx * slotW
                            val isSelected = idx == safeIndex
                            if (isSelected) {
                                drawRoundRect(
                                    color = ConfirmedBlue.copy(alpha = 0.12f),
                                    topLeft = Offset(slotLeft + 3f, topPad),
                                    size = Size(slotW - 6f, usableH),
                                    cornerRadius = CornerRadius(10f, 10f)
                                )
                            }

                            val activeBars = buildList {
                                if (pt.receivedBrl > 0.0) add(pt.receivedBrl to ReceivedGreen)
                                if (pt.confirmedBrl > 0.0) add(pt.confirmedBrl to ConfirmedBlue)
                                if (pt.projectedBrl > 0.0) add(pt.projectedBrl to ProjectedAmber)
                            }
                            if (activeBars.isNotEmpty()) {
                                val groupPad = slotW * 0.14f
                                val gap = 3.5f
                                val availW = (slotW - groupPad * 2f - gap * (activeBars.size - 1)).coerceAtLeast(6f)
                                val barW = (availW / activeBars.size.toFloat()).coerceIn(6f, 22f)
                                val totalBarsW = barW * activeBars.size + gap * (activeBars.size - 1)
                                var cursorX = slotLeft + (slotW - totalBarsW) / 2f

                                activeBars.forEach { (value, color) ->
                                    val ratio = (value / maxVal).toFloat().coerceIn(0f, 1f)
                                    val barH = (usableH * ratio).coerceAtLeast(5f)
                                    val topY = topPad + (usableH - barH)
                                    drawRoundRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(color, color.copy(alpha = 0.58f)),
                                            startY = topY,
                                            endY = topY + barH
                                        ),
                                        topLeft = Offset(cursorX, topY),
                                        size = Size(barW, barH),
                                        cornerRadius = CornerRadius(6f, 6f)
                                    )
                                    cursorX += barW + gap
                                }
                            }
                        }

                        // Linha tracejada da Média Mensal Recebida
                        if (monthlyAverageReceivedBrl > 0.0) {
                            val avgRatio = (monthlyAverageReceivedBrl / maxVal).toFloat().coerceIn(0f, 1f)
                            val avgY = topPad + usableH * (1f - avgRatio)
                            drawLine(
                                color = PremiumTextPrimary.copy(alpha = 0.78f),
                                start = Offset(0f, avgY),
                                end = Offset(size.width, avgY),
                                strokeWidth = 2.4f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Rótulos dos meses abaixo das barras
                    Row(modifier = Modifier.fillMaxWidth()) {
                        points.forEachIndexed { idx, pt ->
                            val isSelected = idx == safeIndex
                            Text(
                                text = pt.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isSelected -> ConfirmedBlue
                                    pt.isFutureMonth -> ProjectedAmber.copy(alpha = 0.85f)
                                    else -> PremiumTextSecondary
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.width(slotWidthDp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Escala máx.: ${formatCompactAxisBrl(maxVal)} • Tracejado vertical separa meses futuros projetados",
                style = MaterialTheme.typography.labelSmall,
                color = PremiumTextSecondary
            )
        }
    }
}

@Composable
private fun ChartLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PremiumTextPrimary
        )
    }
}

@Composable
private fun MiniSeriesValue(label: String, value: Double, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(
            text = if (value > 0.0) formatBrl(value) else "—",
            style = MonoValueStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
            color = if (value > 0.0) PremiumTextPrimary else PremiumTextSecondary
        )
    }
}

private fun formatCompactAxisBrl(value: Double): String {
    return if (value >= 1000.0) {
        String.format(Locale("pt", "BR"), "R$ %.1fk", value / 1000.0)
    } else {
        String.format(Locale("pt", "BR"), "R$ %.0f", value)
    }
}

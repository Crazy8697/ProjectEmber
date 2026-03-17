package com.projectember.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Format a Y-axis tick value compactly. */
private fun yLabel(value: Float): String = when {
    value >= 10_000f -> "%.0fk".format(value / 1000f)
    value >= 1_000f  -> "%.1fk".format(value / 1000f)
    value >= 10f     -> "%.0f".format(value)
    else             -> "%.1f".format(value)
}

/** Compute [count] evenly-spaced Y-axis tick values between 0 and [maxVal]. */
private fun yTicks(maxVal: Float, count: Int = 4): List<Float> {
    if (maxVal <= 0f) return listOf(0f)
    return (0 until count).map { i -> maxVal * i.toFloat() / (count - 1).coerceAtLeast(1) }
}

/**
 * Bar chart with Y-axis labels and horizontal reference grid lines.
 *
 * When [showYAxis] is true a ~38 dp left gutter is reserved for tick labels.
 * Four equally-spaced horizontal grid lines (0 %, 33 %, 67 %, 100 % of max) are
 * always drawn so the chart is interpretable at a glance.
 */
@Composable
fun TrendBarChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    barColor: Color,
    targetLine: Float? = null,
    targetLineColor: Color = Color(0xFFFFAA00),
    showYAxis: Boolean = true,
) {
    if (data.isEmpty()) return
    val maxValue = maxOf(data.maxOf { it.second }, targetLine ?: 0f, 1f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val textMeasurer = rememberTextMeasurer()
    val ticks = yTicks(maxValue, 4)

    Canvas(modifier = modifier) {
        val yAxisPx = if (showYAxis) 38.dp.toPx() else 0f
        val chartLeft = yAxisPx
        val chartRight = size.width
        val chartWidth = chartRight - chartLeft
        val chartHeight = size.height

        // Grid lines + Y-axis tick labels
        ticks.forEach { tickVal ->
            val lineY = chartHeight - (tickVal / maxValue) * chartHeight
            drawLine(
                color = gridColor.copy(alpha = 0.4f),
                start = Offset(chartLeft, lineY),
                end = Offset(chartRight, lineY),
                strokeWidth = 0.8.dp.toPx()
            )
            if (showYAxis) {
                val label = yLabel(tickVal)
                val measured = textMeasurer.measure(label, style = labelStyle)
                val lx = (yAxisPx - measured.size.width.toFloat() - 4.dp.toPx()).coerceAtLeast(0f)
                val ly = (lineY - measured.size.height / 2f)
                    .coerceIn(0f, chartHeight - measured.size.height)
                drawText(measured, topLeft = Offset(lx, ly))
            }
        }

        // Bars
        val barCount = data.size
        val spacing = 4.dp.toPx()
        val totalSpacing = spacing * (barCount + 1)
        val barWidth = ((chartWidth - totalSpacing) / barCount).coerceAtLeast(2f)

        data.forEachIndexed { index, (_, value) ->
            val barHeight = (value / maxValue) * chartHeight
            val left = chartLeft + spacing + index * (barWidth + spacing)
            val top = chartHeight - barHeight
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }

        // Target reference line
        if (targetLine != null && targetLine > 0f) {
            val lineY = chartHeight - (targetLine / maxValue) * chartHeight
            drawLine(
                color = targetLineColor,
                start = Offset(chartLeft, lineY),
                end = Offset(chartRight, lineY),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}

/**
 * Smoothed area/line chart for trend-first metrics (weight, health vitals).
 *
 * Features:
 *  - Y-axis labels on the left (auto-scaled to data range)
 *  - Horizontal grid reference lines
 *  - Cubic-bezier smoothed line
 *  - Optional gradient area fill under the line
 *  - Hollow circle dots at each data point
 *  - X-axis date labels drawn inside the canvas (first, middle(s), last)
 */
@Composable
fun TrendLineChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    lineColor: Color,
    showArea: Boolean = true,
    targetLine: Float? = null,
    targetLineColor: Color = Color(0xFFFFAA00),
    showYAxis: Boolean = true,
    showXLabels: Boolean = true,
) {
    // Guard: line chart requires at least 2 points to draw a connecting line.
    // With only 1 point we fall through to a single-dot rendering below.
    if (data.isEmpty()) return

    val values = data.map { it.second }
    val rawMin = values.min()
    val rawMax = maxOf(values.max(), targetLine ?: 0f)
    val range = (rawMax - rawMin).coerceAtLeast(1f)
    // Pad 10 % top and bottom so extreme points aren't clipped at the canvas edge
    val minVal = (rawMin - range * 0.10f).coerceAtLeast(0f)
    val maxVal = rawMax + range * 0.10f

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()
    // Capture surface color here (MaterialTheme is not available inside Canvas)
    val surfaceColor = MaterialTheme.colorScheme.surface

    // 4 evenly-spaced ticks across the actual value range
    val ticks = (0..3).map { i -> minVal + (maxVal - minVal) * i / 3f }

    val yAxisPx = if (showYAxis) 38.dp else 0.dp
    val xLabelHeight = if (showXLabels) 18.dp else 0.dp

    Canvas(modifier = modifier) {
        val yAxisW = yAxisPx.toPx()
        val xLabH = xLabelHeight.toPx()

        val chartLeft = yAxisW
        val chartRight = size.width
        val chartTop = 4.dp.toPx()
        val chartBottom = size.height - xLabH
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        fun valueToY(v: Float): Float =
            chartTop + chartHeight * (1f - (v - minVal) / (maxVal - minVal))

        fun indexToX(i: Int): Float =
            chartLeft + chartWidth * i.toFloat() / (data.size - 1).coerceAtLeast(1)

        // Grid lines + Y-axis labels
        ticks.forEach { tickVal ->
            val lineY = valueToY(tickVal)
            drawLine(
                color = gridColor.copy(alpha = 0.4f),
                start = Offset(chartLeft, lineY),
                end = Offset(chartRight, lineY),
                strokeWidth = 0.8.dp.toPx()
            )
            if (showYAxis && yAxisW > 0f) {
                val label = yLabel(tickVal)
                val measured = textMeasurer.measure(label, style = labelStyle)
                val lx = (yAxisW - measured.size.width.toFloat() - 4.dp.toPx()).coerceAtLeast(0f)
                val ly = (lineY - measured.size.height / 2f)
                    .coerceIn(chartTop, chartBottom - measured.size.height)
                drawText(measured, topLeft = Offset(lx, ly))
            }
        }

        // Optional target reference line
        if (targetLine != null && targetLine >= minVal) {
            val lineY = valueToY(targetLine)
            drawLine(
                color = targetLineColor,
                start = Offset(chartLeft, lineY),
                end = Offset(chartRight, lineY),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        // Gradient area under the line (only when ≥2 points form a meaningful polygon)
        if (showArea && data.size >= 2) {
            val areaPath = Path().apply {
                moveTo(indexToX(0), chartBottom)
                lineTo(indexToX(0), valueToY(data[0].second))
                for (i in 1 until data.size) {
                    val x1 = indexToX(i - 1); val y1 = valueToY(data[i - 1].second)
                    val x2 = indexToX(i);     val y2 = valueToY(data[i].second)
                    val cx = (x1 + x2) / 2f
                    cubicTo(cx, y1, cx, y2, x2, y2)
                }
                lineTo(indexToX(data.size - 1), chartBottom)
                close()
            }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0.04f)),
                    startY = chartTop,
                    endY = chartBottom
                )
            )
        }

        // Smooth line
        for (i in 1 until data.size) {
            val x1 = indexToX(i - 1); val y1 = valueToY(data[i - 1].second)
            val x2 = indexToX(i);     val y2 = valueToY(data[i].second)
            val cx = (x1 + x2) / 2f
            val linePath = Path().apply {
                moveTo(x1, y1)
                cubicTo(cx, y1, cx, y2, x2, y2)
            }
            drawPath(linePath, color = lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }

        // Hollow dots at each data point (use surface color so they work on dark/light themes)
        for (i in data.indices) {
            val x = indexToX(i)
            val y = valueToY(data[i].second)
            drawCircle(color = surfaceColor, radius = 5.dp.toPx(), center = Offset(x, y))
            drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(x, y))
        }

        // X-axis labels: first, middle(s), last — avoiding crowding
        if (showXLabels && data.isNotEmpty()) {
            val labelIndices: List<Int> = when {
                data.size <= 4 -> data.indices.toList()
                data.size <= 7 -> listOf(0, data.size / 2, data.size - 1)
                else           -> listOf(0, data.size / 3, 2 * data.size / 3, data.size - 1)
            }.distinct().sorted()

            labelIndices.forEach { i ->
                val measured = textMeasurer.measure(data[i].first, style = labelStyle)
                val x = (indexToX(i) - measured.size.width / 2f)
                    .coerceIn(chartLeft, chartRight - measured.size.width)
                val y = chartBottom + 2.dp.toPx()
                drawText(measured, topLeft = Offset(x, y))
            }
        }
    }
}

@Composable
fun WeeklyTrendCard(
    title: String,
    data: List<Pair<String, Float>>,
    barColor: Color,
    unit: String,
    targetValue: Float? = null,
    modifier: Modifier = Modifier,
    useLineChart: Boolean = false,
) {
    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (useLineChart && data.isNotEmpty()) {
            // Line/area chart — X-axis labels are drawn inside the canvas
            TrendLineChart(
                data = data,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                lineColor = barColor,
                showArea = true,
                targetLine = targetValue,
                showYAxis = true,
                showXLabels = true,
            )
        } else {
            // Bar chart — X-axis labels in a separate Row below the canvas
            TrendBarChart(
                data = data,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                barColor = barColor,
                targetLine = targetValue,
                showYAxis = true,
            )
            if (data.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    data.forEach { (label, _) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

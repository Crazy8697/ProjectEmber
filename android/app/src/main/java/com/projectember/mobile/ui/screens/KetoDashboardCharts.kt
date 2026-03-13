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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TrendBarChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    barColor: Color,
    targetLine: Float? = null,
    targetLineColor: Color = Color(0xFFFFAA00)
) {
    if (data.isEmpty()) return
    val maxValue = maxOf(data.maxOf { it.second }, targetLine ?: 0f, 1f)

    Canvas(modifier = modifier) {
        val barCount = data.size
        val spacing = 4.dp.toPx()
        val totalSpacing = spacing * (barCount + 1)
        val barWidth = (size.width - totalSpacing) / barCount
        val chartHeight = size.height - 2.dp.toPx()

        data.forEachIndexed { index, (_, value) ->
            val barHeight = (value / maxValue) * chartHeight
            val left = spacing + index * (barWidth + spacing)
            val top = chartHeight - barHeight
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }

        if (targetLine != null && targetLine > 0f) {
            val lineY = chartHeight - (targetLine / maxValue) * chartHeight
            drawLine(
                color = targetLineColor,
                start = Offset(0f, lineY),
                end = Offset(size.width, lineY),
                strokeWidth = 1.5.dp.toPx()
            )
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        TrendBarChart(
            data = data,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            barColor = barColor,
            targetLine = targetValue
        )
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

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BodyMetricEntry
import com.example.data.TrackableMetric
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class ChartPoint(
    val entry: BodyMetricEntry,
    val value: Float,
    val dateMillis: Long,
    val dateLabel: String
)

@Composable
fun BodyMetricChart(
    entriesAscending: List<BodyMetricEntry>,
    selectedMetric: TrackableMetric,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale("ru")) }
    val fullDateFormat = remember { SimpleDateFormat("dd MMMM yyyy", Locale("ru")) }

    // Filter entries that have a valid value for the selected metric
    val points = remember(entriesAscending, selectedMetric) {
        entriesAscending.mapNotNull { entry ->
            val v = selectedMetric.extractValue(entry)
            if (v != null && v > 0f) {
                ChartPoint(
                    entry = entry,
                    value = v,
                    dateMillis = entry.dateMillis,
                    dateLabel = dateFormat.format(Date(entry.dateMillis))
                )
            } else null
        }
    }

    if (points.isEmpty()) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(240.dp)
                .testTag("chart_empty_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет данных по метрике «${selectedMetric.labelRu}» для графика.\nДобавьте замеры в дневник.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    // Min & max values with margin padding for nice rendering
    val rawMin = points.minOf { it.value }
    val rawMax = points.maxOf { it.value }
    val spread = if (rawMax - rawMin > 0.1f) rawMax - rawMin else 2f
    val padding = spread * 0.15f
    val yMin = (rawMin - padding).coerceAtLeast(0f)
    val yMax = rawMax + padding

    var activeSelectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val activePoint = activeSelectedIndex?.let { points.getOrNull(it) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("body_metric_chart_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Selected or Latest Value preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (activePoint != null) "Точка на графике" else "Текущий показатель",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val displayPoint = activePoint ?: points.last()
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.US, "%.1f", displayPoint.value),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = lineColor
                        )
                        if (selectedMetric.unit.isNotBlank()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = selectedMetric.unit,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }

                // Date badge
                val displayDate = activePoint?.let { fullDateFormat.format(Date(it.dateMillis)) }
                    ?: fullDateFormat.format(Date(points.last().dateMillis))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = displayDate,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(points) {
                        detectTapGestures(
                            onTap = { offset ->
                                val w = size.width
                                val step = if (points.size > 1) w / (points.size - 1) else w
                                val idx = ((offset.x + step / 2) / step).toInt().coerceIn(0, points.lastIndex)
                                activeSelectedIndex = if (activeSelectedIndex == idx) null else idx
                            }
                        )
                    }
                    .pointerInput(points) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val w = size.width
                                val step = if (points.size > 1) w / (points.size - 1) else w
                                val idx = ((offset.x + step / 2) / step).toInt().coerceIn(0, points.lastIndex)
                                activeSelectedIndex = idx
                            },
                            onDrag = { change, _ ->
                                val w = size.width
                                val step = if (points.size > 1) w / (points.size - 1) else w
                                val idx = ((change.position.x + step / 2) / step).toInt().coerceIn(0, points.lastIndex)
                                activeSelectedIndex = idx
                            },
                            onDragEnd = {
                                // Keep selected or clear after delay if wanted
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val bottomPadding = 24.dp.toPx()
                    val chartHeight = height - bottomPadding
                    val topPadding = 12.dp.toPx()
                    val usableHeight = chartHeight - topPadding

                    val gridLineColor = Color(0x1F7F7F7F)
                    val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                    // Draw 3 horizontal grid lines (max, mid, min)
                    for (i in 0..2) {
                        val y = topPadding + (usableHeight / 2f) * i
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashedEffect
                        )
                    }

                    if (points.size == 1) {
                        // Single point representation
                        val pt = points[0]
                        val cx = width / 2f
                        val cy = chartHeight / 2f
                        drawCircle(color = lineColor.copy(alpha = 0.2f), radius = 18.dp.toPx(), center = Offset(cx, cy))
                        drawCircle(color = lineColor, radius = 7.dp.toPx(), center = Offset(cx, cy))
                        drawCircle(color = Color.White, radius = 3.dp.toPx(), center = Offset(cx, cy))
                        return@Canvas
                    }

                    // Compute pixel coordinates for points
                    val stepX = width / (points.size - 1)
                    val coords = points.mapIndexed { index, p ->
                        val x = index * stepX
                        val normalized = (p.value - yMin) / (yMax - yMin)
                        val y = topPadding + usableHeight * (1f - normalized)
                        Offset(x, y)
                    }

                    // Build smooth cubic Bezier path for the line
                    val strokePath = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        for (i in 1 until coords.size) {
                            val prev = coords[i - 1]
                            val curr = coords[i]
                            val midX = (prev.x + curr.x) / 2f
                            cubicTo(
                                x1 = midX, y1 = prev.y,
                                x2 = midX, y2 = curr.y,
                                x3 = curr.x, y3 = curr.y
                            )
                        }
                    }

                    // Build area fill path
                    val fillPath = Path().apply {
                        addPath(strokePath)
                        lineTo(coords.last().x, chartHeight)
                        lineTo(coords.first().x, chartHeight)
                        close()
                    }

                    // Draw area gradient fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.35f),
                                lineColor.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            startY = topPadding,
                            endY = chartHeight
                        )
                    )

                    // Draw curve line
                    drawPath(
                        path = strokePath,
                        color = lineColor,
                        style = Stroke(
                            width = 3.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )

                    // Draw data points
                    coords.forEachIndexed { i, offset ->
                        val isSelected = (i == activeSelectedIndex)
                        val radius = if (isSelected) 8.dp.toPx() else 4.5.dp.toPx()
                        val ringRadius = if (isSelected) 14.dp.toPx() else 0.dp.toPx()

                        if (isSelected) {
                            // Vertical scrub indicator line
                            drawLine(
                                color = lineColor.copy(alpha = 0.5f),
                                start = Offset(offset.x, topPadding),
                                end = Offset(offset.x, chartHeight),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = dashedEffect
                            )
                            drawCircle(
                                color = lineColor.copy(alpha = 0.25f),
                                radius = ringRadius,
                                center = offset
                            )
                        }

                        drawCircle(
                            color = lineColor,
                            radius = radius,
                            center = offset
                        )
                        drawCircle(
                            color = Color.White,
                            radius = radius * 0.5f,
                            center = offset
                        )
                    }
                }
            }

            // X-Axis labels (start, middle, end)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = points.first().dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (points.size > 2) {
                    val midIdx = points.size / 2
                    Text(
                        text = points[midIdx].dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = points.last().dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

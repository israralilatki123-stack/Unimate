package com.example.unimate.ui.gpa

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.SemesterCalculatedMetrics
import com.example.unimate.ui.theme.UniMateBlueElectric
import com.example.unimate.ui.theme.UniMateEmerald
import com.example.unimate.ui.theme.UniMatePurplePrimary
import java.util.Locale

@Composable
fun GpaProgressChartCard(
    semesterMetrics: List<SemesterCalculatedMetrics>,
    maxScalePoints: Double,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val barColor = UniMateBlueElectric
    val cgpaLineColor = UniMatePurplePrimary
    val highlightDotColor = UniMateEmerald
    val maxScale = maxScalePoints.coerceAtLeast(1.0)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("gpa_progress_chart_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null, tint = UniMatePurplePrimary)
                    Text(
                        text = "GPA & CGPA Progress Chart",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Text(
                    text = "Max ${String.format(Locale.US, "%.1f", maxScale)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (semesterMetrics.isEmpty()) {
                Text(
                    text = "Add at least one current or previous semester record to plot your GPA bars and cumulative CGPA trend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChartLegendItem(color = barColor, label = "Semester GPA")
                    ChartLegendItem(color = cgpaLineColor, label = "Cumulative CGPA")
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .testTag("gpa_progress_canvas"),
                    ) {
                        val width = size.width
                        val height = size.height
                        if (width <= 0f || height <= 0f) return@Canvas

                        val topPad = 14.dp.toPx()
                        val bottomPad = 12.dp.toPx()
                        val sidePad = 12.dp.toPx()
                        val plotHeight = (height - topPad - bottomPad).coerceAtLeast(1f)
                        val plotWidth = (width - sidePad * 2f).coerceAtLeast(1f)

                        // Horizontal dashed reference lines (25%, 50%, 75%, 100%)
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        for (step in 0..4) {
                            val fraction = step / 4f
                            val y = topPad + plotHeight * (1f - fraction)
                            drawLine(
                                color = gridColor.copy(alpha = if (step == 0) 0.9f else 0.55f),
                                start = Offset(sidePad, y),
                                end = Offset(width - sidePad, y),
                                strokeWidth = if (step == 0) 1.5.dp.toPx() else 1.dp.toPx(),
                                pathEffect = if (step == 0) null else dashEffect,
                            )
                        }

                        val count = semesterMetrics.size
                        val slotWidth = plotWidth / count.coerceAtLeast(1)
                        val barWidth = (slotWidth * 0.44f).coerceIn(10.dp.toPx(), 40.dp.toPx())
                        val cgpaPoints = mutableListOf<Offset>()

                        semesterMetrics.forEachIndexed { index, item ->
                            val centerX = sidePad + slotWidth * (index + 0.5f)
                            val semFrac = (item.semesterGpa / maxScale).toFloat().coerceIn(0f, 1f)
                            val barHeight = plotHeight * semFrac
                            val barTop = topPad + (plotHeight - barHeight)

                            drawRoundRect(
                                color = barColor.copy(alpha = 0.30f),
                                topLeft = Offset(centerX - barWidth / 2f, barTop),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                            )

                            val cgpaFrac = (item.cumulativeCgpaAfter / maxScale).toFloat().coerceIn(0f, 1f)
                            val cgpaY = topPad + (plotHeight - plotHeight * cgpaFrac)
                            cgpaPoints += Offset(centerX, cgpaY)
                        }

                        if (cgpaPoints.size >= 2) {
                            val linePath = Path().apply {
                                moveTo(cgpaPoints.first().x, cgpaPoints.first().y)
                                for (i in 1 until cgpaPoints.size) {
                                    lineTo(cgpaPoints[i].x, cgpaPoints[i].y)
                                }
                            }
                            drawPath(
                                path = linePath,
                                color = cgpaLineColor,
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )
                        }

                        cgpaPoints.forEachIndexed { index, pt ->
                            val isLatest = index == cgpaPoints.lastIndex
                            drawCircle(
                                color = Color.White,
                                radius = if (isLatest) 6.dp.toPx() else 4.5.dp.toPx(),
                                center = pt,
                            )
                            drawCircle(
                                color = if (isLatest) highlightDotColor else cgpaLineColor,
                                radius = if (isLatest) 4.dp.toPx() else 3.dp.toPx(),
                                center = pt,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    semesterMetrics.forEach { item ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.2f", item.cumulativeCgpaAfter),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = cgpaLineColor,
                            )
                            Text(
                                text = item.semester.semesterName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            color = color,
            shape = CircleShape,
            modifier = Modifier.size(9.dp),
            content = {},
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

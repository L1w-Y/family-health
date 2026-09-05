// 契约：prototype/app.js svgLine/legend（折线图 + 上下极值标注）
package com.family.health.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.health.ui.theme.FhColors

val CHART_COLORS = listOf(FhColors.Primary, FhColors.Amber, FhColors.ChartBlue)

data class ChartSeries(
    val label: String,
    val points: List<Float>,
)

/** 简易折线图：多序列、自动缩放、左上下极值标注（prototype svgLine 同款） */
@Composable
fun LineChart(series: List<ChartSeries>, modifier: Modifier = Modifier) {
    val all = series.flatMap { it.points }
    if (all.isEmpty()) return
    val min = all.min()
    val max = all.max()
    val pad = ((max - min).takeIf { it > 0 } ?: 1f) * 0.2f
    val lo = min - pad
    val hi = max + pad
    val n = series.maxOf { it.points.size }

    Box(modifier = modifier.fillMaxWidth().height(120.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().padding(top = 14.dp, bottom = 4.dp)) {
            val left = 10.dp.toPx()
            val right = size.width - 10.dp.toPx()
            val top = 0f
            val bottom = size.height
            fun x(i: Int): Float =
                left + (right - left) * (if (n == 1) 0.5f else i.toFloat() / (n - 1))
            fun y(v: Float): Float =
                bottom - ((v - lo) / (hi - lo)) * (bottom - top)

            series.forEachIndexed { si, s ->
                val color = CHART_COLORS[si % CHART_COLORS.size]
                val path = Path()
                s.points.forEachIndexed { i, v ->
                    val px = x(i)
                    val py = y(v)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
                s.points.forEachIndexed { i, v ->
                    drawCircle(color, radius = 3.dp.toPx(), center = Offset(x(i), y(v)))
                }
            }
        }
        Text(
            formatTick(max), fontSize = 9.sp, color = FhColors.Tiny,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp),
        )
        Text(
            formatTick(min), fontSize = 9.sp, color = FhColors.Tiny,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp),
        )
    }
}

private fun formatTick(v: Float): String =
    if (v == Math.floor(v.toDouble()).toFloat()) v.toInt().toString() else "%.1f".format(v)

/** 图例（prototype legend） */
@Composable
fun ChartLegend(labels: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 6.dp),
    ) {
        labels.forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.width(10.dp).height(3.dp)) {
                    drawRect(CHART_COLORS[i % CHART_COLORS.size])
                }
                Spacer(Modifier.width(4.dp))
                Text(label, fontSize = 12.sp, color = FhColors.Text2)
            }
        }
    }
}

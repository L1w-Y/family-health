// 契约：docs/05-页面结构与交互.md §7 趋势与对比页（复查前集中看的仪表盘，无异常标注）
package com.family.health.feature.trends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.indicatorPoints
import com.family.health.ui.Routes
import com.family.health.ui.components.CardHead
import com.family.health.ui.components.ChartLegend
import com.family.health.ui.components.EmptyHint
import com.family.health.ui.components.FChip
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.LineChart
import com.family.health.ui.components.ChartSeries
import com.family.health.ui.components.PageTitle
import com.family.health.ui.theme.FhColors
import com.family.health.util.daysTo
import com.family.health.util.mmdd

@Composable
fun TrendsScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    val eventsAsc = member.events.sortedBy { it.checkupDate }

    Column(modifier = Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
        PageTitle("趋势与对比", onBack = { nav.popBackStack() })

        CardHead("重点指标对比", if (ui.trendsTableMode) "折线 ›" else "表格 ›") {
            vm.setTrendsTableMode(!ui.trendsTableMode)
        }
        FhCard(contentPadding = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (member.watchlist.isEmpty()) {
                EmptyHint("重点清单为空\n在报告里点某个指标即可\"加入重点清单\"")
            } else if (ui.trendsTableMode) {
                // 表格模式：行=清单项，列=复查日期（仅数值与日期，无异常标注）
                Column {
                    Row {
                        Spacer(Modifier.weight(1.2f))
                        eventsAsc.forEach { e ->
                            Text(
                                mmdd(e.checkupDate), fontSize = 12.sp, color = FhColors.Text2,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    member.watchlist.forEach { w ->
                        val (pts, _) = indicatorPoints(member, w.canonicalName)
                        val byDate = pts.associateBy { it.date }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { nav.navigate(Routes.indicator(w.canonicalName)) }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(w.canonicalName, fontSize = 13.5.sp, color = FhColors.Text, modifier = Modifier.weight(1.2f))
                            eventsAsc.forEach { e ->
                                Text(
                                    byDate[e.checkupDate]?.displayValue ?: "—",
                                    fontSize = 13.5.sp, color = FhColors.Text,
                                    modifier = Modifier.weight(1f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            } else {
                // 折线模式：每个清单项一条线
                member.watchlist.forEach { w ->
                    val (pts, _) = indicatorPoints(member, w.canonicalName)
                    val numeric = pts.filter { it.isNumeric }
                    Text(
                        "${w.canonicalName}  ${w.canonicalUnit}",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 4.dp),
                    )
                    if (numeric.size >= 2) {
                        LineChart(listOf(ChartSeries(w.canonicalName, numeric.map { it.valueNumeric!!.toFloat() })))
                    } else {
                        Text("记录不足两次，暂无折线", fontSize = 12.sp, color = FhColors.Text2,
                            modifier = Modifier.padding(6.dp))
                    }
                }
            }
        }
        Text(
            "仅展示数值与日期，无异常标注 · 清单行可点进单项历史",
            fontSize = 12.sp, color = FhColors.Text2,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        )

        // 日常测量趋势
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("日常测量趋势", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
            Spacer(Modifier.weight(1f))
            listOf(7, 30, 90).forEach { d ->
                FChip("${d}天", on = ui.trendDays == d) { vm.setTrendDays(d) }
                Spacer(Modifier.width(8.dp))
            }
        }
        val bp = member.measurements
            .filter { it.type == "bp" && !it.deleted && daysTo(it.date) >= -ui.trendDays }
            .sortedBy { it.measuredAt }
        val glu = member.measurements
            .filter { it.type == "glucose" && !it.deleted && daysTo(it.date) >= -ui.trendDays }
            .sortedBy { it.measuredAt }

        FhCard(contentPadding = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(modifier = Modifier.padding(bottom = 8.dp)) {
                Text("血压", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                Spacer(Modifier.weight(1f))
                Text("${bp.size} 条 · 流水见记录 Tab", fontSize = 12.sp, color = FhColors.Text2)
            }
            if (bp.size >= 2) {
                ChartLegend(listOf("高压", "低压"))
                LineChart(
                    listOf(
                        ChartSeries("高压", bp.map { it.systolic!!.toFloat() }),
                        ChartSeries("低压", bp.map { it.diastolic!!.toFloat() }),
                    )
                )
            } else {
                EmptyHint("近${ui.trendDays}天记录不足")
            }
        }
        FhCard(contentPadding = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(modifier = Modifier.padding(bottom = 8.dp)) {
                Text("血糖（空腹）", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                Spacer(Modifier.weight(1f))
                Text("${glu.size} 条", fontSize = 12.sp, color = FhColors.Text2)
            }
            if (glu.size >= 2) {
                LineChart(listOf(ChartSeries("血糖", glu.map { it.glucoseMmol!!.toFloat() })))
            } else {
                EmptyHint("近${ui.trendDays}天记录不足")
            }
        }
    }
}

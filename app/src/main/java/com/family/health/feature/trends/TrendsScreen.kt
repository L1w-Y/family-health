// 契约：docs/05-页面结构与交互.md §7 趋势与对比页（指标摘要卡 + 测量统计，紧凑数值，无折线/异常标注）
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.indicatorPoints
import com.family.health.data.model.Measurement
import com.family.health.ui.Routes
import com.family.health.ui.components.CardHead
import com.family.health.ui.components.EmptyHint
import com.family.health.ui.components.FChip
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.FhTextField
import com.family.health.ui.components.PageTitle
import com.family.health.ui.theme.FhColors
import com.family.health.util.daysTo
import com.family.health.util.mmdd
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    var showAdd by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle("趋势与对比", onBack = { nav.popBackStack() })

        CardHead("重点指标对比", "＋ 添加 ›") { showAdd = true }
        if (member.watchlist.isEmpty()) {
            FhCard { EmptyHint("重点清单为空，点右上角\"＋ 添加\"") }
        } else {
            // 每指标一张摘要卡：最新值 + 近几次横排（新→旧），点卡进单项历史
            member.watchlist.forEach { w ->
                val (pts, _) = indicatorPoints(member, w.canonicalName)
                val numeric = pts.filter { it.isNumeric }
                FhCard(
                    onClick = { nav.navigate(Routes.indicator(w.canonicalName)) },
                    contentPadding = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(w.canonicalName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text)
                        Spacer(Modifier.weight(1f))
                        if (numeric.isNotEmpty()) {
                            val last = numeric.last()
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(last.displayValue, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                                if (w.canonicalUnit.isNotEmpty()) {
                                    Text(" ${w.canonicalUnit}", fontSize = 11.sp, color = FhColors.Text2)
                                }
                            }
                            Text(mmdd(last.date), fontSize = 11.sp, color = FhColors.Text2,
                                modifier = Modifier.padding(start = 8.dp))
                        } else {
                            Text("暂无数值", fontSize = 12.sp, color = FhColors.Text2)
                        }
                    }
                    if (numeric.size >= 2) {
                        Text(
                            numeric.asReversed().take(5).joinToString("  ·  ") { it.displayValue },
                            fontSize = 12.sp, color = FhColors.Text2,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        }

        // 日常测量（统计摘要 + 按日数值）
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
            Text("日常测量", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
            Spacer(Modifier.weight(1f))
            listOf(3, 7, 30).forEach { d ->
                FChip("${d}天", on = ui.trendDays == d) { vm.setTrendDays(d) }
                Spacer(Modifier.width(8.dp))
            }
        }
        val bp = member.measurements
            .filter { it.type == "bp" && !it.deleted && daysTo(it.date) >= -ui.trendDays }
        val glu = member.measurements
            .filter { it.type == "glucose" && !it.deleted && daysTo(it.date) >= -ui.trendDays }

        MeasureSummaryCard(type = "bp", label = "血压", recs = bp, days = ui.trendDays)
        MeasureSummaryCard(type = "glucose", label = "血糖", recs = glu, days = ui.trendDays)
    }

    // 添加重点指标：候选 = 该成员全部报告指标名（去重），也可手输别名
    if (showAdd) {
        var manual by remember { mutableStateOf("") }
        val candidates = member.events
            .flatMap { it.reports }
            .flatMap { it.indicators }
            .map { it.itemName }
            .distinct()
            .filter { name -> member.watchlist.none { w -> name == w.canonicalName || name in w.aliases } }
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 26.dp)) {
                Text("加入重点清单", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text2,
                    modifier = Modifier.padding(bottom = 10.dp))
                FhTextField(manual, { manual = it }, "手输指标名（可填别名合并历史）")
                if (manual.isNotBlank()) {
                    Text(
                        "＋ 加入\"$manual\"",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
                        modifier = Modifier
                            .clickable {
                                vm.addWatch(manual.trim())
                                vm.toast("已加入重点清单")
                                showAdd = false
                            }
                            .padding(vertical = 10.dp),
                    )
                }
                Text("从报告指标中选择", fontSize = 12.sp, color = FhColors.Text2,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                if (candidates.isEmpty()) {
                    Text("暂无可选指标", fontSize = 13.sp, color = FhColors.Text2,
                        modifier = Modifier.padding(vertical = 8.dp))
                }
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                    candidates.forEach { name ->
                        Text(
                            name, fontSize = 14.sp, color = FhColors.Text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.addWatch(name)
                                    vm.toast("已加入重点清单")
                                    showAdd = false
                                }
                                .padding(vertical = 11.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasureSummaryCard(type: String, label: String, recs: List<Measurement>, days: Int) {
    FhCard(contentPadding = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        val sorted = recs.sortedBy { it.measuredAt }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 2.dp)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
            Spacer(Modifier.weight(1f))
            if (sorted.isNotEmpty()) {
                val last = sorted.last()
                Text(
                    if (type == "bp") "${last.systolic}/${last.diastolic}" else "${last.glucoseMmol}",
                    fontSize = 19.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
                )
                Text(mmdd(last.date), fontSize = 11.sp, color = FhColors.Text2,
                    modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (sorted.isEmpty()) {
            Text("近${days}天暂无记录", fontSize = 12.sp, color = FhColors.Text2,
                modifier = Modifier.padding(vertical = 4.dp))
            return@FhCard
        }
        // 统计行：均值 · 最高 · 最低（纯算术）
        val stat = if (type == "bp") {
            val sys = sorted.mapNotNull { it.systolic }
            val dia = sorted.mapNotNull { it.diastolic }
            "均值 ${sys.average().roundToInt()}/${dia.average().roundToInt()}" +
                " · 最高 ${sys.maxOrNull()}/${dia.maxOrNull()}" +
                " · 最低 ${sys.minOrNull()}/${dia.minOrNull()}"
        } else {
            val v = sorted.mapNotNull { it.glucoseMmol }
            "均值 ${"%.1f".format(v.average())} · 最高 ${v.maxOrNull()} · 最低 ${v.minOrNull()}"
        }
        Text("$stat（${sorted.size} 条）", fontSize = 11.sp, color = FhColors.Text2,
            modifier = Modifier.padding(top = 3.dp, bottom = 4.dp))
        // 按日数值行
        sorted.groupBy { it.date }.toSortedMap(compareByDescending { it }).forEach { (day, dayRecs) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Text(mmdd(day), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
                    modifier = Modifier.width(40.dp))
                androidx.compose.foundation.layout.FlowRow {
                    dayRecs.sortedBy { it.measuredAt }.forEach { x ->
                        Text(
                            if (type == "bp") {
                                buildString {
                                    append("${x.systolic}/${x.diastolic}")
                                    x.heartRateBpm?.let { append("·$it") }
                                    append(" ${x.time}")
                                }
                            } else {
                                "${x.glucoseMmol} ${com.family.health.data.model.Labels.sceneName(x.glucoseContext)} ${x.time}"
                            },
                            fontSize = 12.sp, color = FhColors.Text,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

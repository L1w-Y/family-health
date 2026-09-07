// 契约：docs/05-页面结构与交互.md §7 趋势与对比页（紧凑数值密度、无折线、无异常标注）
package com.family.health.feature.trends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.indicatorPoints
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    val eventsAsc = member.events.sortedBy { it.checkupDate }
    var showAdd by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
        PageTitle("趋势与对比", onBack = { nav.popBackStack() })

        CardHead("重点指标对比", "＋ 添加 ›") { showAdd = true }
        FhCard(contentPadding = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            if (member.watchlist.isEmpty()) {
                EmptyHint("重点清单为空，点右上角\"＋ 添加\"")
            } else {
                // 行=清单项，列=复查日期；日期多时整体横向滚动（仅数值与日期，无异常标注）
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Column {
                        Row {
                            Spacer(Modifier.width(88.dp))
                            eventsAsc.forEach { e ->
                                Text(
                                    mmdd(e.checkupDate), fontSize = 11.sp, color = FhColors.Text2,
                                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                                    modifier = Modifier.width(56.dp).padding(vertical = 5.dp),
                                )
                            }
                        }
                        member.watchlist.forEach { w ->
                            val (pts, _) = indicatorPoints(member, w.canonicalName)
                            val byDate = pts.associateBy { it.date }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { nav.navigate(Routes.indicator(w.canonicalName)) },
                            ) {
                                Text(
                                    w.canonicalName, fontSize = 12.sp, color = FhColors.Text,
                                    modifier = Modifier.width(88.dp).padding(vertical = 7.dp),
                                    maxLines = 2, lineHeight = 15.sp,
                                )
                                eventsAsc.forEach { e ->
                                    Text(
                                        byDate[e.checkupDate]?.displayValue ?: "—",
                                        fontSize = 12.sp, color = FhColors.Text, textAlign = TextAlign.Center,
                                        modifier = Modifier.width(56.dp).padding(vertical = 7.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 日常测量趋势（紧凑数值流水，无折线）
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
            Text("日常测量趋势", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
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

        FhCard(contentPadding = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("血压（${bp.size}）", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
                modifier = Modifier.padding(vertical = 4.dp))
            if (bp.isEmpty()) {
                Text("近${ui.trendDays}天暂无记录", fontSize = 12.sp, color = FhColors.Text2,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            bp.groupBy { it.date }.toSortedMap(compareByDescending { it }).forEach { (day, recs) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(mmdd(day), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
                        modifier = Modifier.width(44.dp))
                    androidx.compose.foundation.layout.FlowRow {
                        recs.sortedBy { it.measuredAt }.forEach { x ->
                            Text(
                                buildString {
                                    append("${x.systolic}/${x.diastolic}")
                                    x.heartRateBpm?.let { append("·$it") }
                                    append(" ${x.time}")
                                },
                                fontSize = 12.sp, color = FhColors.Text,
                                modifier = Modifier.padding(end = 14.dp),
                            )
                        }
                    }
                }
            }
        }
        FhCard(contentPadding = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("血糖（${glu.size}）", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
                modifier = Modifier.padding(vertical = 4.dp))
            if (glu.isEmpty()) {
                Text("近${ui.trendDays}天暂无记录", fontSize = 12.sp, color = FhColors.Text2,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            glu.groupBy { it.date }.toSortedMap(compareByDescending { it }).forEach { (day, recs) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(mmdd(day), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
                        modifier = Modifier.width(44.dp))
                    androidx.compose.foundation.layout.FlowRow {
                        recs.sortedBy { it.measuredAt }.forEach { x ->
                            Text(
                                "${x.glucoseMmol} ${com.family.health.data.model.Labels.sceneName(x.glucoseContext)} ${x.time}",
                                fontSize = 12.sp, color = FhColors.Text,
                                modifier = Modifier.padding(end = 14.dp),
                            )
                        }
                    }
                }
            }
        }
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

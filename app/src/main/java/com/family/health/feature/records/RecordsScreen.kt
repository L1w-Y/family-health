// 契约：docs/05-页面结构与交互.md §4 Tab 2 记录（复查段含重点指标表；测量段统计卡+当日明细）
package com.family.health.feature.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.family.health.data.model.Labels
import com.family.health.data.model.Measurement
import com.family.health.ui.Routes
import com.family.health.ui.components.CardHead
import com.family.health.ui.components.EmptyHint
import com.family.health.ui.components.FChip
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.FhTextField
import com.family.health.ui.components.RowCard
import com.family.health.ui.components.RowLine1
import com.family.health.ui.components.RowLine2
import com.family.health.ui.components.SegControl
import com.family.health.ui.theme.FhColors
import com.family.health.util.daysTo
import com.family.health.util.mmdd
import com.family.health.util.weekdayCn
import kotlin.math.roundToInt

@Composable
fun RecordsScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    var dayDetail by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Measurement?>(null) }
    var showAddWatch by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        SegControl(listOf("复查", "测量"), if (ui.recordsSeg == "checkup") 0 else 1) {
            vm.setRecordsSeg(if (it == 0) "checkup" else "measure")
        }

        if (ui.recordsSeg == "checkup") {
            // 重点指标对比（紧凑表格：行=清单项，列=最近 5 次复查）
            CardHead("重点指标对比", "＋ 添加 ›") { showAddWatch = true }
            val eventsDesc = member.events.sortedByDescending { it.checkupDate }.take(5)
            FhCard(contentPadding = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (member.watchlist.isEmpty()) {
                    EmptyHint("重点清单为空，点右上角\"＋ 添加\"")
                } else {
                    Row {
                        Spacer(Modifier.width(92.dp))
                        eventsDesc.forEach { e ->
                            Text(
                                mmdd(e.checkupDate), fontSize = 11.sp, color = FhColors.Text2,
                                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
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
                                modifier = Modifier.width(92.dp).padding(vertical = 6.dp),
                                maxLines = 2, lineHeight = 15.sp,
                            )
                            eventsDesc.forEach { e ->
                                Text(
                                    byDate[e.checkupDate]?.displayValue ?: "—",
                                    fontSize = 12.sp, color = FhColors.Text, textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 复查事件列表（契约 §4.1：日期+科室，摘要从简）
            CardHead("复查记录")
            if (member.events.isEmpty()) {
                EmptyHint("还没有复查记录\n从最近一次复查报告开始，历史可以慢慢补")
            }
            member.events.sortedByDescending { it.checkupDate }.forEach { e ->
                RowCard(onClick = { nav.navigate(Routes.event(e.id)) }) {
                    RowLine1 {
                        Text(e.checkupDate, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                        Text(e.department, fontSize = 12.sp, color = FhColors.Text2)
                    }
                    if (e.note.isNotEmpty()) {
                        RowLine2(e.note, color = androidx.compose.ui.graphics.Color(0xFF8A9089))
                    }
                }
            }
        } else {
            // 测量段：类型 + 周期 chips，统计卡（最新值/均值/按日行，点行看明细）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                FChip("血压", on = ui.measureType == "bp") { vm.setMeasureType("bp") }
                FChip("血糖", on = ui.measureType == "glucose") { vm.setMeasureType("glucose") }
                Spacer(Modifier.weight(1f))
                listOf(3, 7, 30).forEach { d ->
                    FChip("${d}天", on = ui.trendDays == d) { vm.setTrendDays(d) }
                }
            }
            val recs = member.measurements.filter {
                it.type == ui.measureType && !it.deleted && daysTo(it.date) >= -ui.trendDays
            }
            if (recs.isEmpty()) {
                EmptyHint("该周期内暂无记录，点底部 ＋ 记一条")
            } else {
                MeasureStatCard(recs, ui.measureType, ui.trendDays) { dayDetail = it }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    // 当日明细（契约 §4.2：逐条时刻、数值、署名；可编辑/删除）
    dayDetail?.let { day ->
        val recs = member.measurements
            .filter { it.date == day && !it.deleted }
            .sortedBy { it.measuredAt }
        DayDetailSheet(
            day = day,
            recs = recs,
            onEdit = { editing = it },
            onDelete = {
                vm.deleteMeasurement(it.id)
                vm.toast("已删除")
            },
            onDismiss = { dayDetail = null },
        )
    }

    editing?.let { m ->
        MeasurementEditDialog(
            initial = m,
            onSave = {
                vm.updateMeasurement(it)
                vm.toast("已保存")
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (showAddWatch) {
        AddWatchSheet(vm, onDone = { showAddWatch = false })
    }
}

/** 测量统计卡：最新值 + 均值/最高/最低 + 按日数值行（点行看当日明细） */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MeasureStatCard(
    recs: List<Measurement>, type: String, days: Int,
    onDayClick: (String) -> Unit,
) {
    val sorted = recs.sortedBy { it.measuredAt }
    FhCard(contentPadding = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
        val last = sorted.last()
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 2.dp)) {
            Text(
                if (type == "bp") "${last.systolic}/${last.diastolic}" else "${last.glucoseMmol}",
                fontSize = 19.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
            )
            Text(mmdd(last.date), fontSize = 11.sp, color = FhColors.Text2,
                modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            Text("${sorted.size} 条 · 近${days}天", fontSize = 11.sp, color = FhColors.Text2)
        }
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
        Text(stat, fontSize = 11.sp, color = FhColors.Text2,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
        sorted.groupBy { it.date }.toSortedMap(compareByDescending { it }).forEach { (day, dayRecs) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDayClick(day) }
                    .padding(vertical = 4.dp),
            ) {
                Column(modifier = Modifier.width(44.dp)) {
                    Text(mmdd(day), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2)
                    Text(weekdayCn(day), fontSize = 9.sp, color = FhColors.Tiny)
                }
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
                                "${x.glucoseMmol} ${Labels.sceneName(x.glucoseContext)} ${x.time}"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWatchSheet(vm: AppViewModel, onDone: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    var manual by remember { mutableStateOf("") }
    val candidates = member.events
        .flatMap { it.reports }
        .flatMap { it.indicators }
        .map { it.itemName }
        .distinct()
        .filter { name -> member.watchlist.none { w -> name == w.canonicalName || name in w.aliases } }
    ModalBottomSheet(onDismissRequest = onDone) {
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
                            onDone()
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
                                onDone()
                            }
                            .padding(vertical = 11.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(
    day: String,
    recs: List<Measurement>,
    onEdit: (Measurement) -> Unit,
    onDelete: (Measurement) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 26.dp)) {
            Text(
                "$day · ${recs.size} 条记录",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FhColors.Text2,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            recs.forEach { x ->
                RowCard {
                    RowLine1 {
                        Text(measurementSummary(x), fontSize = 14.sp, color = FhColors.Text)
                        Text(x.time, fontSize = 12.sp, color = FhColors.Text2)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 3.dp),
                    ) {
                        Text("由 ${x.createdBy} 录入", fontSize = 13.sp, color = FhColors.Text2)
                        Spacer(Modifier.weight(1f))
                        Text("编辑", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
                            modifier = Modifier.clickable { onEdit(x) }.padding(horizontal = 6.dp))
                        Text("删除", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Amber,
                            modifier = Modifier.clickable { onDelete(x) }.padding(horizontal = 6.dp))
                    }
                }
            }
        }
    }
}

private fun measurementSummary(m: Measurement): String = when (m.type) {
    "bp" -> "血压 ${m.systolic}/${m.diastolic} mmHg" + (m.heartRateBpm?.let { " · 心率$it" } ?: "")
    "glucose" -> "血糖 ${m.glucoseMmol} mmol/L · ${Labels.sceneName(m.glucoseContext)}"
    else -> "心率 ${m.heartRateBpm} 次/分"
}

/** 单条编辑（契约 §4.2：修正手误，保留原录入署名） */
@Composable
private fun MeasurementEditDialog(
    initial: Measurement,
    onSave: (Measurement) -> Unit,
    onDismiss: () -> Unit,
) {
    var sys by remember { mutableStateOf(initial.systolic?.toString() ?: "") }
    var dia by remember { mutableStateOf(initial.diastolic?.toString() ?: "") }
    var hr by remember { mutableStateOf(initial.heartRateBpm?.toString() ?: "") }
    var glu by remember { mutableStateOf(initial.glucoseMmol?.toString() ?: "") }
    var ctx by remember { mutableStateOf(initial.glucoseContext ?: "fasting") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑测量（署名保留：${initial.createdBy}）", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (initial.type == "bp") {
                    FhTextField(sys, { sys = it }, "高压 (mmHg)", number = true)
                    FhTextField(dia, { dia = it }, "低压 (mmHg)", number = true)
                    FhTextField(hr, { hr = it }, "心率", number = true)
                } else {
                    FhTextField(glu, { glu = it }, "血糖 (mmol/L)", number = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Labels.GLUCOSE_SCENES.take(3).forEach { (key, label) ->
                            FChip(label, on = ctx == key) { ctx = key }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Labels.GLUCOSE_SCENES.drop(3).forEach { (key, label) ->
                            FChip(label, on = ctx == key) { ctx = key }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val saved = if (initial.type == "bp") {
                    initial.copy(
                        systolic = sys.toIntOrNull() ?: return@TextButton,
                        diastolic = dia.toIntOrNull() ?: return@TextButton,
                        heartRateBpm = hr.toIntOrNull(),
                    )
                } else {
                    initial.copy(
                        glucoseMmol = glu.toDoubleOrNull() ?: return@TextButton,
                        glucoseContext = ctx,
                    )
                }
                onSave(saved)
            }) { Text("保存", color = FhColors.Primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = FhColors.Text2) }
        },
    )
}

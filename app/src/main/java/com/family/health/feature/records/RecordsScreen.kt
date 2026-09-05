// 契约：docs/05-页面结构与交互.md §4 Tab 2 记录（复查段/测量段、当日明细、编辑/软删）
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.MeasurePeriod
import com.family.health.data.model.Labels
import com.family.health.data.model.Measurement
import com.family.health.ui.Routes
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
import com.family.health.util.monthLabel
import com.family.health.util.shiftMonth
import com.family.health.util.todayStr
import com.family.health.util.weekdayCn

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RecordsScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    var dayDetail by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Measurement?>(null) }

    Column(modifier = Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
        SegControl(listOf("复查", "测量"), if (ui.recordsSeg == "checkup") 0 else 1) {
            vm.setRecordsSeg(if (it == 0) "checkup" else "measure")
        }

        if (ui.recordsSeg == "checkup") {
            // 复查段：事件倒序列表（契约 §4.1）
            if (member.events.isEmpty()) {
                EmptyHint("还没有复查记录\n从最近一次复查报告开始，历史可以慢慢补")
            }
            member.events.sortedByDescending { it.checkupDate }.forEach { e ->
                RowCard(onClick = { nav.navigate(Routes.event(e.id)) }) {
                    RowLine1 {
                        Text(e.checkupDate, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                        Text(e.department, fontSize = 12.sp, color = FhColors.Text2)
                    }
                    RowLine2(
                        buildString {
                            append("${e.hospital} · ${e.reports.size} 份报告")
                            if (e.medChangeSummary.isNotEmpty()) append(" · 用药变化")
                            e.nextCheckupDate?.let { append(" · 下次 ${mmdd(it)}") }
                        }
                    )
                    if (e.note.isNotEmpty()) {
                        RowLine2(e.note, color = androidx.compose.ui.graphics.Color(0xFF8A9089))
                    }
                }
            }
        } else {
            // 测量段（契约 §4.2）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                FChip("血压", on = ui.measureType == "bp") { vm.setMeasureType("bp") }
                FChip("血糖", on = ui.measureType == "glucose") { vm.setMeasureType("glucose") }
            }
            PeriodNav(vm, ui.measurePeriod)

            val list = member.measurements.filter { m ->
                m.type == ui.measureType && !m.deleted && when (val p = ui.measurePeriod) {
                    is MeasurePeriod.Month -> m.date.startsWith(p.month)
                    is MeasurePeriod.LastDays -> daysTo(m.date) >= -p.days
                }
            }
            val byDay = list.groupBy { it.date }.toSortedMap(compareByDescending { it })

            if (byDay.isEmpty()) {
                EmptyHint("该周期内暂无记录，点底部 ＋ 记一条")
            } else if (ui.measureType == "bp") {
                // 血压：日志表式，一行=一天（契约 §4.2）
                byDay.forEach { (day, recs) ->
                    RowCard(onClick = { dayDetail = day }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.width(54.dp)) {
                                Text(mmdd(day), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                                Text(weekdayCn(day), fontSize = 11.sp, color = FhColors.Text2)
                            }
                            Spacer(Modifier.width(14.dp))
                            // 当日条数超过一行宽度自动折行，不截断、不省略（契约 §4.2）
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                recs.sortedBy { it.measuredAt }.forEach { x ->
                                    Column {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text("${x.systolic}/${x.diastolic}", fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold, color = FhColors.Text)
                                            x.heartRateBpm?.let {
                                                Text(" ·$it", fontSize = 12.sp, color = FhColors.Text2)
                                            }
                                        }
                                        Text(x.time, fontSize = 10.sp, color = FhColors.Tiny)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 血糖：场景网格（契约 §4.2）
                FhCard(contentPadding = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Row {
                        Text("日期", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
                            modifier = Modifier.weight(1.1f).padding(vertical = 6.dp))
                        Labels.GLUCOSE_SCENES.forEach { (_, label) ->
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
                                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    byDay.forEach { (day, recs) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dayDetail = day }
                                .padding(vertical = 7.dp),
                        ) {
                            Text(mmdd(day), fontSize = 13.5.sp, color = FhColors.Text, modifier = Modifier.weight(1.1f))
                            Labels.GLUCOSE_SCENES.forEach { (key, _) ->
                                val cells = recs.filter { it.glucoseContext == key }.sortedBy { it.measuredAt }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (cells.isEmpty()) {
                                        Text("·", fontSize = 13.5.sp, color = FhColors.Text2)
                                    }
                                    cells.forEach { c ->
                                        Text("${c.glucoseMmol}", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                                        Text(c.time, fontSize = 10.sp, color = FhColors.Tiny)
                                    }
                                }
                            }
                        }
                    }
                }
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
}

/** 周期选择：自然月翻页 + 快捷周期（契约 §4.2） */
@Composable
private fun PeriodNav(vm: AppViewModel, period: MeasurePeriod) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 10.dp),
    ) {
        when (period) {
            is MeasurePeriod.Month -> {
                Text("‹", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FhColors.Primary,
                    modifier = Modifier
                        .clickable { vm.setMeasurePeriod(MeasurePeriod.Month(shiftMonth(period.month, -1))) }
                        .padding(horizontal = 10.dp))
                Text(monthLabel(period.month), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                Text("›", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FhColors.Primary,
                    modifier = Modifier
                        .clickable { vm.setMeasurePeriod(MeasurePeriod.Month(shiftMonth(period.month, 1))) }
                        .padding(horizontal = 10.dp))
                Spacer(Modifier.weight(1f))
                FChip("近7天") { vm.setMeasurePeriod(MeasurePeriod.LastDays(7)) }
                Spacer(Modifier.width(8.dp))
                FChip("近30天") { vm.setMeasurePeriod(MeasurePeriod.LastDays(30)) }
            }
            is MeasurePeriod.LastDays -> {
                Text("近 ${period.days} 天", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                Spacer(Modifier.weight(1f))
                FChip("回到本月") { vm.setMeasurePeriod(MeasurePeriod.Month(todayStr().substring(0, 7))) }
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
                        Text(
                            measurementSummary(x), fontSize = 14.sp, color = FhColors.Text,
                        )
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
                    FhTextField(hr, { hr = it }, "心率（可空）", number = true)
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

// 契约：docs/05-页面结构与交互.md §4 Tab 2 记录（复查段含重点指标表；测量段日期分组表+当日明细）
package com.family.health.feature.records

import com.family.health.ui.theme.FhType

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.family.health.data.MeasurePeriod
import com.family.health.data.indicatorPoints
import com.family.health.data.model.Labels
import com.family.health.data.model.Measurement
import com.family.health.data.model.WatchItem
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

import com.family.health.util.mmdd
import java.time.LocalDate



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordsScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    var dayDetail by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Measurement?>(null) }
    var showAddWatch by remember { mutableStateOf(false) }
    var removingWatch by remember { mutableStateOf<WatchItem?>(null) }

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        SegControl(listOf("复查", "测量"), if (ui.recordsSeg == "checkup") 0 else 1,
            compact = ui.recordsSeg != "checkup") {
            vm.setRecordsSeg(if (it == 0) "checkup" else "measure")
        }

        if (ui.recordsSeg == "checkup") {
            // 重点指标对比（紧凑表格：行=清单项，列=最近 5 次复查）
            CardHead("重点指标对比", "＋ 添加 ›") { showAddWatch = true }
            val eventsDesc = member.events.sortedByDescending { it.checkupDate }.take(5)
            FhCard(contentPadding = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                if (member.watchlist.isEmpty()) {
                    EmptyHint("重点清单为空，点右上角\"＋ 添加\"")
                } else {
                    // 表头
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(FhColors.SurfaceVariant)) {
                        Spacer(Modifier.width(84.dp))
                        eventsDesc.forEachIndexed { i, e ->
                            if (i > 0) ColDivider()
                            Text(
                                mmdd(e.checkupDate), fontSize = FhType.Caption, color = FhColors.Text2,
                                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f).padding(vertical = 3.dp),
                            )
                        }
                    }
                    RowDivider()
                    member.watchlist.forEachIndexed { wi, w ->
                        val (pts, _) = indicatorPoints(member, w.canonicalName)
                        val byDate = pts.associateBy { it.date }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.combinedClickable(
                                onClick = { nav.navigate(Routes.indicator(w.canonicalName)) },
                                onLongClick = { removingWatch = w },
                            ),
                        ) {
                            Text(
                                w.canonicalName, fontSize = FhType.Caption, color = FhColors.Text,
                                modifier = Modifier.width(84.dp).padding(vertical = 4.dp),
                                maxLines = 2, lineHeight = 14.sp,
                            )
                            eventsDesc.forEachIndexed { i, e ->
                                if (i > 0) ColDivider()
                                Text(
                                    byDate[e.checkupDate]?.displayValue ?: "—",
                                    fontSize = FhType.Caption, color = FhColors.Text, textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                                )
                            }
                        }
                        if (wi < member.watchlist.lastIndex) RowDivider()
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
                        Text(e.checkupDate, fontSize = FhType.Body, fontWeight = FontWeight.Bold, color = FhColors.Text)
                        Text(e.department, fontSize = FhType.Caption, color = FhColors.Text2)
                    }
                    if (e.note.isNotEmpty()) {
                        RowLine2(e.note, color = FhColors.Text2)
                    }
                }
            }
        } else {
            val today = LocalDate.now()
            val anchor = ui.measurementAnchor?.let(LocalDate::parse) ?: today
            MeasurementLog(
                measurements = member.measurements, memberId = member.id,
                type = ui.measureType, granularity = ui.measurementGranularity,
                anchor = anchor, today = today,
                onTypeChange = vm::setMeasureType,
                onGranularityChange = vm::setMeasurementGranularity,
                onShift = vm::shiftMeasurementWindow,
                onAnchorChange = vm::setMeasurementAnchor,
                onDayClick = { dayDetail = it },
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    // 当日明细（契约 §4.2：逐条时刻、数值、署名；可编辑/删除）
    dayDetail?.let { day ->
        val recs = member.measurements
            .filter { it.date == day && it.type == ui.measureType && !it.deleted }
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

    // 移出重点清单确认（契约 §4.1：长按行可移除）
    removingWatch?.let { w ->
        AlertDialog(
            onDismissRequest = { removingWatch = null },
            title = { Text("移出重点清单", fontSize = FhType.Body, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "将「${w.canonicalName}」从重点指标对比中移除？历史指标与报告不受影响。",
                    fontSize = FhType.Label, color = FhColors.Text, lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeWatch(w.id)
                    vm.toast("已移出重点清单")
                    removingWatch = null
                }) { Text("移除", color = FhColors.Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { removingWatch = null }) { Text("取消", color = FhColors.Text2) }
            },
        )
    }
}

@Composable
private fun ColDivider() = androidx.compose.foundation.layout.Box(
    Modifier.width(1.dp).height(12.dp).background(FhColors.Line)
)

@Composable
private fun RowDivider() = androidx.compose.foundation.layout.Box(
    Modifier.fillMaxWidth().height(1.dp).background(FhColors.Line)
)

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
            Text("加入重点清单", fontSize = FhType.Body, fontWeight = FontWeight.Bold, color = FhColors.Text2,
                modifier = Modifier.padding(bottom = 10.dp))
            FhTextField(manual, { manual = it }, "手输指标名（可填别名合并历史）")
            if (manual.isNotBlank()) {
                Text(
                    "＋ 加入\"$manual\"",
                    fontSize = FhType.Label, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
                    modifier = Modifier
                        .clickable {
                            vm.addWatch(manual.trim())
                            vm.toast("已加入重点清单")
                            onDone()
                        }
                        .padding(vertical = 10.dp),
                )
            }
            Text("从报告指标中选择", fontSize = FhType.Caption, color = FhColors.Text2,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            if (candidates.isEmpty()) {
                Text("暂无可选指标", fontSize = FhType.Label, color = FhColors.Text2,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                candidates.forEach { name ->
                    Text(
                        name, fontSize = FhType.Label, color = FhColors.Text,
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

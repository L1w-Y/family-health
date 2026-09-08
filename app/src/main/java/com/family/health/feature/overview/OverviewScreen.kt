// 契约：docs/05-页面结构与交互.md §3 Tab 1 概览（复查气泡 / 便签 / 今日测量 / 今日用药）
package com.family.health.feature.overview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.model.Measurement
import com.family.health.data.nextCheckup
import com.family.health.feature.meds.DailyMedList
import com.family.health.ui.Routes
import com.family.health.ui.components.CardHead
import com.family.health.ui.components.FhCard
import com.family.health.ui.theme.FhColors
import com.family.health.util.todayStr

@Composable
fun OverviewScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    vm.checkTick.collectAsStateWithLifecycle() // 勾选变化驱动重组
    val member = ui.currentMember
    var showDatePicker = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showTimePicker = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        // 下次复查气泡（置顶，无图标；点时间可改期）
        val next = nextCheckup(member, todayStr())
        val targetEvent = next ?: member.events.maxByOrNull { it.checkupDate }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .shadow(2.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(FhColors.Card)
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            Text("下次复查", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
            Spacer(Modifier.weight(1f))
            Text(
                next?.nextCheckupDate ?: "未设置",
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (next != null) FhColors.Primary else FhColors.Text2,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (targetEvent == null) {
                            vm.toast("先录入一次复查")
                        } else {
                            showDatePicker.value = true
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (showDatePicker.value && targetEvent != null) {
            com.family.health.ui.components.FhDateTimePickerDialog(
                initialDate = next?.nextCheckupDate ?: todayStr(),
                initialTime = null,
                needDate = true,
                needTime = false,
                title = "下次复查日期",
                onConfirm = { date, _ ->
                    if (date != null) {
                        vm.setNextCheckup(targetEvent.id, date)
                        vm.toast("已更新下次复查日期")
                    }
                    showDatePicker.value = false
                },
                onDismiss = { showDatePicker.value = false },
            )
        }

        // 便签卡（紧凑行：每行一条，有提醒的行尾黄色 ⏰）
        FhCard(onClick = { nav.navigate(Routes.NOTES) }) {
            CardHead("便签", "全部 ›") { nav.navigate(Routes.NOTES) }
            if (member.notes.isEmpty()) {
                Text("暂无便签，点这里写一条 ›", fontSize = 13.sp, color = FhColors.Text2)
            }
            member.notes.filter { !it.done }.take(5).forEachIndexed { i, n ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                ) {
                    // 无提醒也占位，保持文本对齐
                    Text(
                        if (n.remindAt != null) "⏰" else "",
                        fontSize = 12.sp,
                        modifier = Modifier.width(20.dp).padding(top = 1.dp),
                    )
                    Text(
                        n.text, fontSize = 13.5.sp, color = FhColors.Text, lineHeight = 20.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (i < member.notes.filter { !it.done }.take(5).lastIndex) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth().height(1.dp)
                            .background(androidx.compose.ui.graphics.Color(0xFFF0F1EE)),
                    )
                }
            }
        }

        // 今日测量（两行四列占位气泡：点气泡记一条，长按空气泡设提醒）
        TodayMeasureGrid(
            recs = member.measurements.filter { it.date == todayStr() && !it.deleted },
            onCellClick = { type -> nav.navigate(Routes.measureForm(type)) },
            onCellLongPress = { showTimePicker.value = true },
        )
        if (showTimePicker.value) {
            com.family.health.ui.components.FhDateTimePickerDialog(
                initialDate = todayStr(),
                initialTime = null,
                needDate = false,
                needTime = true,
                title = "每天测量提醒",
                onConfirm = { _, time ->
                    if (time != null) {
                        vm.addMeasureReminderTime(time)
                        vm.toast("已添加每天 $time 提醒")
                    }
                    showTimePicker.value = false
                },
                onDismiss = { showTimePicker.value = false },
            )
        }

        // 今日用药卡（契约 §3：表格化，编辑在管理页）
        FhCard {
            CardHead("今日用药", "管理 ›") { nav.navigate(Routes.DAILY) }
            DailyMedList(
                items = member.daily,
                checks = vm.dailyCheckedSet(),
                onToggleCheck = { vm.toggleDailyChecked(it) },
            )
        }
    }
}

private val MEASURE_BUCKETS = listOf("空腹", "上午", "下午", "晚上")

private fun bucketOf(time: String): Int {
    val h = time.substringBefore(":").toIntOrNull() ?: return 1
    return when (h) {
        in 5..8 -> 0
        in 9..11 -> 1
        in 12..17 -> 2
        else -> 3
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodayMeasureGrid(
    recs: List<Measurement>,
    onCellClick: (String) -> Unit,
    onCellLongPress: () -> Unit,
) {
    FhCard {
        CardHead("今日测量")
        // 列头置顶：空腹 上午 下午 晚上
        Row(modifier = Modifier.padding(start = 34.dp, bottom = 2.dp)) {
            MEASURE_BUCKETS.forEach { b ->
                Text(
                    b, fontSize = 10.sp, color = FhColors.Tiny, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 3.dp),
                )
            }
        }
        listOf("glucose" to "血糖", "bp" to "血压").forEach { (type, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Text(
                    label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
                    modifier = Modifier.width(34.dp),
                )
                MEASURE_BUCKETS.forEachIndexed { col, _ ->
                    val x = recs.filter { it.type == type && bucketOf(it.time) == col }
                        .maxByOrNull { it.measuredAt }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 3.dp)
                            .aspectRatio(1.9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (x != null) FhColors.PrimarySoft else FhColors.ChipGray)
                            .combinedClickable(
                                onClick = { onCellClick(type) },
                                onLongClick = { if (x == null) onCellLongPress() },
                            ),
                    ) {
                        if (x != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (type == "bp") "${x.systolic}/${x.diastolic}" else "${x.glucoseMmol}",
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
                                )
                                Text(x.time, fontSize = 9.sp, color = FhColors.Text2)
                            }
                        } else {
                            Text("—", fontSize = 12.sp, color = FhColors.Tiny)
                        }
                    }
                }
            }
        }
    }
}

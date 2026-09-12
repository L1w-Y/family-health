// 契约：docs/05-页面结构与交互.md §3 Tab 1 概览（复查气泡 / 便签 / 今日测量 / 今日用药）
package com.family.health.feature.overview

import com.family.health.ui.theme.FhType

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.produceState
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
import com.family.health.data.MeasurementTimeBucket
import com.family.health.data.model.Measurement
import com.family.health.data.nextCheckup
import com.family.health.feature.meds.DailyMedList
import com.family.health.ui.Routes
import com.family.health.ui.components.CardHead
import com.family.health.ui.components.FhCard
import com.family.health.ui.theme.FhColors
import com.family.health.util.todayStr
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime

@Composable
fun OverviewScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    val today by rememberToday()
    var showDatePicker = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        // 下次复查气泡（置顶，无图标；点时间可改期）
        val next = nextCheckup(member, today)
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
            Text("下次复查", fontSize = FhType.Label, fontWeight = FontWeight.Bold, color = FhColors.Text)
            Spacer(Modifier.weight(1f))
            Text(
                next?.nextCheckupDate ?: "未设置",
                fontSize = FhType.Label, fontWeight = FontWeight.Bold,
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
                initialDate = next?.nextCheckupDate ?: today,
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
                Text("暂无便签，点这里写一条 ›", fontSize = FhType.Label, color = FhColors.Text2)
            }
            member.notes.filter { !it.done }.take(5).forEachIndexed { i, n ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                ) {
                    // 无提醒也占位，保持文本对齐
                    Text(
                        if (n.remindAt != null) "⏰" else "",
                        fontSize = FhType.Caption,
                        modifier = Modifier.width(20.dp).padding(top = 1.dp),
                    )
                    Text(
                        n.text, fontSize = FhType.Label, color = FhColors.Text, lineHeight = 20.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (i < member.notes.filter { !it.done }.take(5).lastIndex) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth().height(1.dp)
                            .background(FhColors.Line),
                    )
                }
            }
        }

        // 今日测量（两行四列 = measured_at 派生时间段；血糖另显场景）
        TodayMeasureGrid(
            recs = member.measurements.filter { it.date == today && !it.deleted },
        )

        // 今日用药卡（契约 §3：表格化，编辑在管理页）
        FhCard {
            CardHead("今日用药", "管理 ›") { nav.navigate(Routes.DAILY) }
            DailyMedList(items = member.daily, medications = member.meds)
        }
    }
}

@Composable
private fun TodayMeasureGrid(
    recs: List<Measurement>,
) {
    FhCard {
        CardHead("今日测量")
        // 列头只表达时间；“空腹”等场景仅属于血糖记录内容。
        Row(modifier = Modifier.padding(start = 34.dp, bottom = 2.dp)) {
            MeasurementTimeBucket.ALL.forEach { bucket ->
                Text(
                    bucket.label, fontSize = FhType.Caption, color = FhColors.Tiny,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 3.dp),
                )
            }
        }
        listOf("glucose" to "血糖", "bp" to "血压").forEach { (type, label) ->
            val cells = todayMeasurementCells(recs, type)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Text(
                    label, fontSize = FhType.Caption, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
                    modifier = Modifier.width(34.dp),
                )
                MeasurementTimeBucket.ALL.forEach { bucket ->
                    val cell = cells[bucket]
                    val x = cell?.latest
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 3.dp)
                            .defaultMinSize(minHeight = 46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (x != null) FhColors.PrimarySoft else FhColors.ChipGray),
                    ) {
                        if (x == null) {
                            Text("—", fontSize = FhType.Caption, color = FhColors.Tiny)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (type == "bp") "${x.systolic}/${x.diastolic}" else "${x.glucoseMmol}",
                                    fontSize = FhType.Label, fontWeight = FontWeight.Bold, color = FhColors.Text,
                                    maxLines = 1,
                                )
                                val details = buildList {
                                    if (type == "glucose") compactGlucoseScene(x.glucoseContext).takeIf { it.isNotEmpty() }?.let(::add)
                                    if ((cell?.count ?: 0) > 1) add("${cell?.count}次")
                                }.joinToString("·")
                                if (details.isNotEmpty()) {
                                    Text(
                                        details, fontSize = FhType.Caption, color = FhColors.Text2,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 跨午夜后主动刷新“今天”，无需等待 Room 或导航状态变化。 */
@Composable
private fun rememberToday() = produceState(initialValue = todayStr()) {
    while (true) {
        val now = ZonedDateTime.now()
        val nextDay = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        delay((Duration.between(now, nextDay).toMillis() + 250L).coerceAtLeast(1_000L))
        value = todayStr()
    }
}

// 契约：docs/05-页面结构与交互.md §3 Tab 1 概览（便签 / 今日测量 / 今日用药 / 下次复查）
package com.family.health.feature.overview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.family.health.data.model.DailyMedItem
import com.family.health.data.model.Labels
import com.family.health.data.nextCheckup
import com.family.health.feature.meds.DailyEditSheet
import com.family.health.feature.meds.DailyMedList
import com.family.health.ui.Routes
import com.family.health.ui.components.CardHead
import com.family.health.ui.components.FChip
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.RemindChip
import com.family.health.ui.theme.FhColors
import com.family.health.util.daysTo
import com.family.health.util.todayStr

@Composable
fun OverviewScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    vm.checkTick.collectAsStateWithLifecycle() // 勾选变化时驱动重组
    val member = ui.currentMember
    var editingDaily = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<DailyMedItem?>(null)
    }

    val undone = member.notes.filter { !it.done }
    val showNotes = (if (undone.isNotEmpty()) undone else member.notes).take(2)
    val next = nextCheckup(member, todayStr())

    Column(modifier = Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
        // 便签卡（契约 §3：最近 1~2 条，"全部 >"进列表）
        FhCard(onClick = { nav.navigate(Routes.NOTES) }) {
            CardHead("📌 便签", "全部 ›") { nav.navigate(Routes.NOTES) }
            if (showNotes.isEmpty()) {
                Text("暂无便签，点这里写一条 ›", fontSize = 13.sp, color = FhColors.Text2)
            }
            showNotes.forEach { n ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(n.text, fontSize = 14.sp, color = FhColors.Text, lineHeight = 22.sp)
                    if (n.remindAt != null) {
                        RemindChip("${n.remindAt} 提醒 · ${n.remindTargetName ?: "全家"}")
                    }
                }
            }
        }

        // 今日测量卡：当日血压/血糖 + 未测时段快捷提醒
        FhCard {
            CardHead("🩺 今日测量", "记一条 ›") { nav.navigate(Routes.measureForm("bp")) }
            val todayRecs = member.measurements.filter { it.date == todayStr() && !it.deleted }
            val bpToday = todayRecs.filter { it.type == "bp" }.sortedBy { it.measuredAt }
            val gluToday = todayRecs.filter { it.type == "glucose" }.sortedBy { it.measuredAt }
            if (bpToday.isEmpty() && gluToday.isEmpty()) {
                Text("今天还没测", fontSize = 13.sp, color = FhColors.Text2)
            }
            if (bpToday.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("血压", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
                        modifier = Modifier.width(34.dp))
                    androidx.compose.foundation.layout.FlowRow {
                        bpToday.forEach { x ->
                            Text(
                                buildString {
                                    append("${x.systolic}/${x.diastolic}")
                                    x.heartRateBpm?.let { append("·$it") }
                                    append(" ${x.time}")
                                },
                                fontSize = 13.sp, color = FhColors.Text,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    }
                }
            }
            if (gluToday.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("血糖", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
                        modifier = Modifier.width(34.dp))
                    androidx.compose.foundation.layout.FlowRow {
                        gluToday.forEach { x ->
                            Text(
                                "${x.glucoseMmol} ${Labels.sceneName(x.glucoseContext)} ${x.time}",
                                fontSize = 13.sp, color = FhColors.Text,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    }
                }
            }
            // 快捷提醒：已配每日时刻 → 未过的可再设"今天单次"；未配置 → 快捷预设
            val measureTimes = ui.reminders[member.id]?.measureTimes ?: emptyList()
            val nowLabel = "%02d:%02d".format(java.time.LocalTime.now().hour, java.time.LocalTime.now().minute)
            if (measureTimes.isNotEmpty()) {
                Text("测量提醒", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
                    modifier = Modifier.padding(top = 8.dp, bottom = 5.dp))
                Row {
                    measureTimes.sorted().forEach { t ->
                        if (t > nowLabel) {
                            FChip("$t 提醒") { vm.quickRemindToday(t) }
                        } else {
                            FChip("每天 $t", on = true) { }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            } else {
                Text("设每天测量提醒", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
                    modifier = Modifier.padding(top = 8.dp, bottom = 5.dp))
                Row {
                    listOf("07:00", "12:00", "19:00", "21:00").forEach { t ->
                        FChip("＋ $t") {
                            vm.addMeasureReminderTime(t)
                            vm.toast("已添加每天 $t 提醒")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }

        // 今日用药卡（契约 §3：执行层清单，时段分区 + 勾选）
        FhCard {
            CardHead("💊 今日用药", "管理 ›") { nav.navigate(Routes.DAILY) }
            DailyMedList(
                items = member.daily,
                checks = vm.dailyCheckedSet(),
                onToggleCheck = { vm.toggleDailyChecked(it) },
            ) { editingDaily.value = it }
        }

        // 复查卡（契约 §3：最近的未来"下次复查日期"）
        FhCard(onClick = next?.let { ev -> { nav.navigate(Routes.event(ev.id)) } }) {
            CardHead(
                "🏥 下次复查",
                more = next?.nextCheckupDate?.let { "${daysTo(it)} 天后" },
            )
            if (next != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        next.nextCheckupDate!!, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        color = FhColors.Text,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${next.department} · ${next.hospital}", fontSize = 13.sp, color = FhColors.Text2)
                }
            } else {
                Text("未设置，录入复查事件时填写\"下次复查日期\"即可", fontSize = 13.sp, color = FhColors.Text2)
            }
        }
    }

    editingDaily.value?.let { item ->
        DailyEditSheet(
            initial = item,
            onSave = {
                vm.upsertDaily(it)
                vm.toast("已保存")
                editingDaily.value = null
            },
            onDelete = { id ->
                vm.deleteDaily(id)
                vm.toast("已删除")
                editingDaily.value = null
            },
            onDismiss = { editingDaily.value = null },
        )
    }
}

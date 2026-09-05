// 契约：docs/05-页面结构与交互.md §3 Tab 1 概览（首屏三卡）
package com.family.health.feature.overview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.family.health.data.model.DailyMedItem
import com.family.health.data.nextCheckup
import com.family.health.feature.meds.DailyEditSheet
import com.family.health.feature.meds.DailyMedList
import com.family.health.ui.Routes
import com.family.health.ui.components.CardHead
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.RemindChip
import com.family.health.ui.theme.FhColors
import com.family.health.util.daysTo
import com.family.health.util.todayStr

@Composable
fun OverviewScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    var editingDaily by remember { mutableStateOf<DailyMedItem?>(null) }

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

        // 今日用药卡（契约 §3：执行层清单，与方案允许不一致）
        FhCard {
            CardHead("💊 今日用药", "管理 ›") { nav.navigate(Routes.DAILY) }
            DailyMedList(member.daily) { editingDaily = it }
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

    editingDaily?.let { item ->
        DailyEditSheet(
            initial = item,
            onSave = {
                vm.upsertDaily(it)
                vm.toast("已保存")
                editingDaily = null
            },
            onDelete = { id ->
                vm.deleteDaily(id)
                vm.toast("已删除")
                editingDaily = null
            },
            onDismiss = { editingDaily = null },
        )
    }
}

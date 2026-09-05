// 契约：docs/05-页面结构与交互.md §3 今日用药管理页（增删改）
package com.family.health.feature.meds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.model.DailyMedItem
import com.family.health.data.model.Labels
import com.family.health.ui.components.EmptyHint
import com.family.health.ui.components.FTag
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.PageSub
import com.family.health.ui.components.PageTitle
import com.family.health.ui.components.RowCard
import com.family.health.ui.components.RowLine1
import com.family.health.ui.components.RowLine2
import com.family.health.ui.theme.FhColors

@Composable
fun DailyManageScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    var editing by remember { mutableStateOf<DailyMedItem?>(null) }
    var showDelete by remember { mutableStateOf(true) }

    Column(modifier = Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
        PageTitle("今日用药 · 管理", onBack = { nav.popBackStack() })
        PageSub("家里实际执行的服药单，自由填写，与用药页的\"当前方案\"是两回事")
        if (member.daily.isEmpty()) {
            EmptyHint("还没添加，从下方添加")
        }
        member.daily.forEach { x ->
            RowCard(onClick = {
                showDelete = true
                editing = x
            }) {
                RowLine1 {
                    Text(x.name.ifBlank { "（未命名）" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                    if (x.isTcm) {
                        FTag("中药计数")
                    } else {
                        Text(
                            x.doseSlots.joinToString(" · ") { Labels.slotName(it) }.ifEmpty { "未设时段" },
                            fontSize = 12.sp, color = FhColors.Text2,
                        )
                    }
                }
                RowLine2(
                    if (x.isTcm) {
                        "剩 ${x.tcmPacks?.toInt() ?: 0} 副 · 每副 ${x.tcmDaysPerPack} 天 · 当前第 ${x.tcmUsedDays} 天"
                    } else {
                        "${x.doseText} · " + (x.stockQty?.let { s ->
                            "剩 ${s.toInt()}${x.stockUnit}" + (x.daysLeft?.let { "（约${it}天）" } ?: "")
                        } ?: "余量未记")
                    }
                )
            }
        }
        FhButton("＋ 添加一项", onClick = {
            showDelete = false
            editing = DailyMedItem(
                id = vm.newDailyId(), name = "", doseText = "1 片",
                doseSlots = listOf("morning"), stockQty = null, stockUnit = "片", dailyQty = 1.0,
            )
        })
        FhButton("＋ 添加中药计数", onClick = {
            showDelete = false
            editing = DailyMedItem(
                id = vm.newDailyId(), name = "中药方", isTcm = true,
                tcmPacks = 7.0, tcmDaysPerPack = 1, tcmUsedDays = 0,
            )
        }, ghost = true)
        Spacer(Modifier.height(20.dp))
    }

    editing?.let { item ->
        DailyEditSheet(
            initial = item,
            onSave = {
                vm.upsertDaily(it)
                vm.toast("已保存")
                editing = null
            },
            onDelete = if (showDelete) { id ->
                vm.deleteDaily(id)
                vm.toast("已删除")
                editing = null
            } else null,
            onDismiss = { editing = null },
        )
    }
}

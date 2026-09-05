// 契约：docs/05-页面结构与交互.md §5 当前方案卡点卡片编辑
package com.family.health.feature.meds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.model.Labels
import com.family.health.ui.components.FChip
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.FhTextField
import com.family.health.ui.components.PageTitle
import com.family.health.ui.components.SegControl

@Composable
fun MedEditScreen(vm: AppViewModel, nav: NavHostController, medId: String) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val med = ui.currentMember.meds.firstOrNull { it.id == medId }
    if (med == null) {
        PageTitle("编辑用药", onBack = { nav.popBackStack() })
        return
    }
    var name by remember { mutableStateOf(med.name) }
    var dosage by remember { mutableStateOf(med.dosageText) }
    var kind by remember { mutableStateOf(med.medKind) }
    var cat by remember { mutableStateOf(med.category) }
    var slots by remember { mutableStateOf(med.doseSlots.toSet()) }
    var start by remember { mutableStateOf(med.startDate) }
    var end by remember { mutableStateOf(med.endDate ?: "") }

    Column(modifier = Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
        PageTitle("编辑用药", onBack = { nav.popBackStack() })
        FhTextField(name, { name = it }, "名称")
        FhTextField(dosage, { dosage = it }, "用法用量")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                FieldLabel("类别")
                SegControl(listOf("西药", "中药"), if (kind == "tcm") 1 else 0) {
                    kind = if (it == 1) "tcm" else "western"
                }
            }
            Column(Modifier.weight(1f)) {
                FieldLabel("性质")
                SegControl(listOf("长期", "临时"), if (cat == "temporary") 1 else 0) {
                    cat = if (it == 1) "temporary" else "long_term"
                }
            }
        }
        FieldLabel("服用时段")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Labels.SLOTS.forEach { (key, label) ->
                FChip(label, on = key in slots) {
                    slots = if (key in slots) slots - key else slots + key
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FhTextField(start, { start = it }, "开始日期", Modifier.weight(1f), placeholder = "2026-08-15")
            FhTextField(end, { end = it }, "结束日期（空=进行中）", Modifier.weight(1f))
        }
        FhButton("保 存", onClick = {
            vm.saveMed(
                med.copy(
                    name = name.ifBlank { med.name },
                    dosageText = dosage,
                    medKind = kind,
                    category = cat,
                    doseSlots = Labels.SLOTS.map { it.first }.filter { it in slots },
                    startDate = start.ifBlank { med.startDate },
                    endDate = end.ifBlank { null },
                )
            )
            vm.toast("已保存")
            nav.popBackStack()
        })
        Spacer(Modifier.height(20.dp))
    }
}

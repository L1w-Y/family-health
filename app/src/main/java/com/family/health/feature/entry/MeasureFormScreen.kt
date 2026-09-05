// 契约：docs/05-页面结构与交互.md §8 记血压/血糖（大数字、时间默认现在、保存后自动返回）
package com.family.health.feature.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.model.Labels
import com.family.health.data.model.Measurement
import com.family.health.ui.Routes
import com.family.health.ui.components.FChip
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.FhTextField
import com.family.health.ui.components.PageSub
import com.family.health.ui.components.PageTitle
import com.family.health.ui.switchTab
import com.family.health.ui.theme.FhColors
import com.family.health.util.nowStr
import java.util.UUID

@Composable
fun MeasureFormScreen(vm: AppViewModel, nav: NavHostController, type: String) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val title = if (type == "glucose") "记血糖" else "记血压"

    var sys by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("") }
    var hr by remember { mutableStateOf("") }
    var glu by remember { mutableStateOf("") }
    var ctx by remember { mutableStateOf(ui.lastGlucoseCtx) }
    var at by remember { mutableStateOf(nowStr()) }
    var note by remember { mutableStateOf("") }

    fun save(again: Boolean) {
        val me = ui.devices.firstOrNull { it.self }?.displayName ?: "爸爸"
        val rec = when (type) {
            "bp" -> {
                val s = sys.toIntOrNull(); val d = dia.toIntOrNull()
                if (s == null || d == null) {
                    vm.toast("请填写高压和低压")
                    return
                }
                Measurement(
                    id = "ms" + UUID.randomUUID().toString().substring(0, 8),
                    type = "bp", measuredAt = at,
                    systolic = s, diastolic = d, heartRateBpm = hr.toIntOrNull(),
                    note = note, createdBy = me,
                )
            }
            else -> {
                val g = glu.toDoubleOrNull()
                if (g == null) {
                    vm.toast("请填写血糖")
                    return
                }
                Measurement(
                    id = "ms" + UUID.randomUUID().toString().substring(0, 8),
                    type = "glucose", measuredAt = at,
                    glucoseMmol = g, glucoseContext = ctx,
                    note = note, createdBy = me,
                )
            }
        }
        vm.addMeasurement(rec)
        vm.toast("已保存")
        if (again) {
            sys = ""; dia = ""; hr = ""; glu = ""; note = ""
            at = nowStr()
        } else {
            vm.setRecordsSeg("measure")
            nav.switchTab(Routes.RECORDS)
        }
    }

    Column(modifier = Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
        PageTitle(title, onBack = { nav.popBackStack() })
        PageSub("${ui.currentMember.name} · 时间默认现在，可改")

        if (type == "bp") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FhTextField(sys, { sys = it }, "高压 (mmHg)", Modifier.weight(1f), number = true, placeholder = "138")
                FhTextField(dia, { dia = it }, "低压 (mmHg)", Modifier.weight(1f), number = true, placeholder = "86")
            }
            FhTextField(hr, { hr = it }, "心率（可空）", number = true, placeholder = "72")
        } else {
            FhTextField(glu, { glu = it }, "血糖 (mmol/L)", number = true, placeholder = "6.1")
            Text("测量场景", fontSize = 13.sp, color = FhColors.Text2,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Labels.GLUCOSE_CTX_FULL.entries.take(3).forEach { (key, label) ->
                    FChip(label, on = ctx == key) { ctx = key }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Labels.GLUCOSE_CTX_FULL.entries.drop(3).forEach { (key, label) ->
                    FChip(label, on = ctx == key) { ctx = key }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        FhTextField(at, { at = it }, "测量时间")
        FhTextField(note, { note = it }, "备注（可空）")
        FhButton("保 存", onClick = { save(false) })
        FhButton("保存并再记一条", onClick = { save(true) }, ghost = true)
        Spacer(Modifier.height(20.dp))
    }
}

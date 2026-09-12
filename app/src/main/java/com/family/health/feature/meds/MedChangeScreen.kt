// 契约：docs/05-页面结构与交互.md §5 记用药变化（核对式）；改量自动建立 supersedes 链
package com.family.health.feature.meds

import com.family.health.ui.theme.FhType

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.model.Labels
import com.family.health.data.model.MedicationAdjustment
import com.family.health.data.model.MedicationItem
import com.family.health.data.model.dosageLabel
import com.family.health.data.model.doseTimesMatchSlots
import com.family.health.ui.Routes
import com.family.health.ui.components.FChip
import com.family.health.ui.components.FTag
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.FhTextField
import com.family.health.ui.components.PageTitle
import com.family.health.ui.components.SegControl
import com.family.health.ui.switchTab
import com.family.health.ui.theme.FhColors
import com.family.health.ui.theme.FhShape
import com.family.health.util.todayStr

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MedChangeScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    val active = member.meds.filter { it.endDate == null }

    var date by remember { mutableStateOf(todayStr()) }
    var reason by remember { mutableStateOf("") }
    var linkedEventId by remember { mutableStateOf<String?>(null) }
    var stops by remember { mutableStateOf(setOf<String>()) }
    var adj by remember { mutableStateOf(mapOf<String, MedicationAdjustment>()) }
    val news = remember { mutableListOf<NewMed>().toMutableStateList() }

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle(form = true, title = "记用药变化", onBack = { nav.popBackStack() })

        // ===== 1. 当前用药 =====
        FhCard(contentPadding = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text("当前用药", fontSize = FhType.Section, fontWeight = FontWeight.Bold, color = FhColors.Text,
                modifier = Modifier.padding(bottom = 10.dp))
            if (active.isEmpty()) {
                Text("当前无进行中用药，可直接新增", fontSize = FhType.Label, color = FhColors.Text2,
                    modifier = Modifier.padding(bottom = 8.dp))
            }
            active.forEach { med ->
                val stopped = med.id in stops
                val adjusting = adj.containsKey(med.id)
                MedChangeRow(
                    med = med,
                    stopped = stopped,
                    adjusting = adjusting,
                    adjustment = adj[med.id],
                    onToggleAdjust = {
                        if (adjusting) {
                            adj = adj - med.id
                        } else {
                            adj = adj + (med.id to MedicationAdjustment(
                                dosageText = med.dosageText,
                                doseQtyText = med.doseQty?.let { if (it == kotlin.math.floor(it)) it.toLong().toString() else it.toString() } ?: "",
                                doseUnit = med.doseUnit,
                                doseTimesText = med.doseTimesPerDay?.toString() ?: "",
                                doseSlots = med.doseSlots.toSet(),
                            ))
                            stops = stops - med.id
                        }
                    },
                    onToggleStop = {
                        if (stopped) stops = stops - med.id
                        else {
                            stops = stops + med.id
                            adj = adj - med.id
                        }
                    },
                    onAdjustmentChange = { adj = adj + (med.id to it) },
                )
            }
            // 新增药品表单（点击虚线框后展开在虚线框上方）
            news.forEachIndexed { i, n ->
                NewMedForm(n, index = i, news = news)
            }
            DashedAddBox("＋ 新增药品/药方") { news.add(NewMed()) }
        }

        // ===== 2. 生效日期 =====
        FhCard(contentPadding = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("生效日期", fontSize = FhType.Section, fontWeight = FontWeight.Bold, color = FhColors.Text)
                Spacer(Modifier.width(10.dp))
                Text(dateLabelCn(date), fontSize = FhType.Body, fontWeight = FontWeight.Bold, color = FhColors.Text)
                if (date == todayStr()) {
                    Spacer(Modifier.width(8.dp))
                    FTag("今天")
                }
            }
            InlineDateWheel(date, onDate = { date = it })
        }

        // ===== 3. 关联复查 =====
        FhCard(contentPadding = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Text("关联复查", fontSize = FhType.Section, fontWeight = FontWeight.Bold, color = FhColors.Text)
                Spacer(Modifier.width(8.dp))
                Text("选填，不选则不关联，可左右滑动", fontSize = FhType.Caption, color = FhColors.Text2)
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                member.events.forEach { e ->
                    EventChip(e.checkupDate, on = linkedEventId == e.id) {
                        linkedEventId = if (linkedEventId == e.id) null else e.id
                    }
                }
            }
        }

        // ===== 4. 备注 =====
        FhCard(contentPadding = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                Text("备注", fontSize = FhType.Section, fontWeight = FontWeight.Bold, color = FhColors.Text)
                Spacer(Modifier.weight(1f))
                Text("选填", fontSize = FhType.Caption, color = FhColors.Text2)
            }
            FhTextField(
                reason, { reason = it }, "",
                placeholder = "例如：9月复查后医生调整，中药方更换…",
            )
        }

        // ===== 5. 本次变化状态栏 =====
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text("本次变化：", fontSize = FhType.Caption, color = FhColors.Text2)
            Spacer(Modifier.width(6.dp))
            Text("改量 ${adj.size}", fontSize = FhType.Caption, fontWeight = FontWeight.SemiBold, color = FhColors.SlotNoon)
            Spacer(Modifier.width(14.dp))
            Text("停用 ${stops.size}", fontSize = FhType.Caption, fontWeight = FontWeight.SemiBold, color = FhColors.InputError)
            Spacer(Modifier.width(14.dp))
            Text("新增 ${news.count { it.name.isNotBlank() }}", fontSize = FhType.Caption, fontWeight = FontWeight.SemiBold, color = FhColors.Primary)
        }

        // ===== 6. 保存 =====
        FhButton(
            "保 存",
            onClick = {
                val invalidAdjustment = adj.any { (id, value) ->
                    member.meds.firstOrNull { it.id == id }?.medKind == "western" &&
                        ((value.doseQtyText.toDoubleOrNull() ?: 0.0) <= 0 || value.doseUnit.isBlank() ||
                            (value.doseTimesText.toIntOrNull() ?: 0) !in 1..4 || value.doseSlots.isEmpty())
                }
                val invalidNew = news.any {
                    it.name.isNotBlank() && it.kind == "western" &&
                        ((it.doseQty.toDoubleOrNull() ?: 0.0) <= 0 || it.doseUnit.isBlank() ||
                            (it.doseTimes.toIntOrNull() ?: 0) !in 1..4 || it.slots.isEmpty())
                }
                if (invalidAdjustment || invalidNew) {
                    vm.toast("西药需填写每次数量、单位、一天次数和服用时段")
                    return@FhButton
                }
                val mismatchedAdjustment = adj.any { (id, value) ->
                    member.meds.firstOrNull { it.id == id }?.medKind == "western" &&
                        !doseTimesMatchSlots(value.doseTimesText.toIntOrNull(), value.doseSlots)
                }
                val mismatchedNew = news.any {
                    it.name.isNotBlank() && it.kind == "western" && !doseTimesMatchSlots(it.doseTimes.toIntOrNull(), it.slots)
                }
                if (mismatchedAdjustment || mismatchedNew) {
                    vm.toast("一天次数必须与所选服用时段数量一致")
                    return@FhButton
                }
                val additions = news.map { n ->
                    MedicationItem(
                        id = vm.newMedId(),
                        name = n.name, dosageText = if (n.kind == "tcm") n.dosage else "",
                        doseQty = if (n.kind == "western") n.doseQty.toDoubleOrNull() else null,
                        doseUnit = n.doseUnit,
                        doseTimesPerDay = if (n.kind == "western") n.doseTimes.toIntOrNull() else null,
                        doseSlots = Labels.SLOTS.map { it.first }.filter { it in n.slots },
                        medKind = n.kind, category = n.cat,
                        startDate = date,
                        endDate = if (n.cat == "temporary") n.end.ifBlank { date } else null,
                    )
                }
                val (c, a, n2) = vm.saveMedChange(date, reason, linkedEventId, stops, adj, additions)
                vm.toast("已保存：停 $c · 改 $a · 增 $n2")
                nav.switchTab(Routes.MEDS)
            },
        )
        Spacer(Modifier.height(20.dp))
    }
}

/** "2026-09-12" → "2026年9月12日" */
private fun dateLabelCn(date: String): String {
    val p = date.split("-")
    return if (p.size == 3) "${p[0]}年${p[1].toIntOrNull() ?: p[1]}月${p[2].toIntOrNull() ?: p[2]}日" else date
}

/** "2026-09-04" → "9月4日" */
private fun mmddCn(date: String): String {
    val p = date.split("-")
    return if (p.size == 3) "${p[1].toIntOrNull() ?: p[1]}月${p[2].toIntOrNull() ?: p[2]}日" else date
}

/** 关联复查日期 chip（两行：日期 + 年份，选中态青色） */
@Composable
private fun EventChip(date: String, on: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(FhShape.Tag)
            .background(if (on) FhColors.PrimarySoft else FhColors.ChipGray)
            .border(1.dp, if (on) FhColors.Primary else FhColors.ChipGray, FhShape.Tag)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(mmddCn(date), fontSize = FhType.Label, fontWeight = FontWeight.SemiBold,
            color = if (on) FhColors.Primary else FhColors.Text)
        Text(date.take(4), fontSize = FhType.Caption,
            color = if (on) FhColors.Primary else FhColors.Text2)
    }
}

/** 当前用药条目：中药=大卡片（详细用法+居中按钮），西药=行布局（右侧按钮）。 */
@Composable
private fun MedChangeRow(
    med: MedicationItem,
    stopped: Boolean,
    adjusting: Boolean,
    adjustment: MedicationAdjustment?,
    onToggleAdjust: () -> Unit,
    onToggleStop: () -> Unit,
    onAdjustmentChange: (MedicationAdjustment) -> Unit,
) {
    Column {
        if (med.medKind == "tcm") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    med.name, fontSize = FhType.Body, fontWeight = FontWeight.Bold,
                    color = if (stopped) FhColors.Text2 else FhColors.Text,
                    textDecoration = if (stopped) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.width(6.dp))
                FTag("中药")
            }
            Text(
                med.dosageText, fontSize = FhType.Caption, color = FhColors.Text2, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                OpButton("改量", FhColors.SlotNoon, adjusting, onToggleAdjust)
                Spacer(Modifier.width(12.dp))
                OpButton("停用", FhColors.InputError, stopped, onToggleStop)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        med.name, fontSize = FhType.Body, fontWeight = FontWeight.Bold,
                        color = if (stopped) FhColors.Text2 else FhColors.Text,
                        textDecoration = if (stopped) TextDecoration.LineThrough else null,
                    )
                    Text(
                        med.dosageLabel, fontSize = FhType.Caption, color = FhColors.Text2,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                OpButton("改量", FhColors.SlotNoon, adjusting, onToggleAdjust)
                Spacer(Modifier.width(8.dp))
                OpButton("停用", FhColors.InputError, stopped, onToggleStop)
            }
        }
        if (adjustment != null) {
            AdjustmentEditor(med, adjustment, onAdjustmentChange)
        }
    }
}

/** 改量编辑区（西药=结构化字段，中药=详细用法文本）+ 时段选择。 */
@Composable
private fun AdjustmentEditor(
    med: MedicationItem,
    adjustment: MedicationAdjustment,
    onChange: (MedicationAdjustment) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(FhColors.Line))
        Spacer(Modifier.height(10.dp))
        if (med.medKind == "western") {
            WesternDoseFields(
                adjustment.doseQtyText, { onChange(adjustment.copy(doseQtyText = it)) },
                adjustment.doseUnit, { onChange(adjustment.copy(doseUnit = it)) },
                adjustment.doseTimesText, { onChange(adjustment.copy(doseTimesText = it)) },
            )
        } else {
            FhTextField(
                adjustment.dosageText,
                { onChange(adjustment.copy(dosageText = it)) },
                "改后详细用法",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Labels.SLOTS.forEach { (key, label) ->
                FChip(label, on = key in adjustment.doseSlots) {
                    val ns = if (key in adjustment.doseSlots) adjustment.doseSlots - key else adjustment.doseSlots + key
                    onChange(adjustment.copy(doseSlots = ns))
                }
            }
        }
    }
}

/** 新增药品表单（置于当前用药卡片内、虚线框上方）。 */
@Composable
private fun NewMedForm(n: NewMed, index: Int, news: androidx.compose.runtime.snapshots.SnapshotStateList<NewMed>) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(FhColors.Line))
        Spacer(Modifier.height(10.dp))
        FhTextField(n.name, { news[index] = n.copy(name = it) }, "药品 / 方名")
        if (n.kind == "western") {
            WesternDoseFields(
                n.doseQty, { news[index] = n.copy(doseQty = it) },
                n.doseUnit, { news[index] = n.copy(doseUnit = it) },
                n.doseTimes, { news[index] = n.copy(doseTimes = it) },
            )
        } else {
            FhTextField(n.dosage, { news[index] = n.copy(dosage = it) }, "详细用法")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                FieldLabel("类别")
                SegControl(listOf("西药", "中药"), if (n.kind == "tcm") 1 else 0) {
                    news[index] = n.copy(kind = if (it == 1) "tcm" else "western")
                }
            }
            Column(Modifier.weight(1f)) {
                FieldLabel("性质")
                SegControl(listOf("长期", "临时"), if (n.cat == "temporary") 1 else 0) {
                    news[index] = n.copy(cat = if (it == 1) "temporary" else "long_term")
                }
            }
        }
        FieldLabel("服用时段")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Labels.SLOTS.forEach { (key, label) ->
                FChip(label, on = key in n.slots) {
                    news[index] = n.copy(slots = if (key in n.slots) n.slots - key else n.slots + key)
                }
            }
        }
        if (n.cat == "temporary") {
            Spacer(Modifier.height(14.dp))
            FhTextField(n.end, { news[index] = n.copy(end = it) }, "止日（临时药）")
        }
    }
}

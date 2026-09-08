// 契约：docs/05-页面结构与交互.md §5 记用药变化（核对式）；改量自动建立 supersedes 链
package com.family.health.feature.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.model.Labels
import com.family.health.data.model.MedicationItem
import com.family.health.ui.Routes
import com.family.health.ui.components.FChip
import com.family.health.ui.components.FTag
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.FhSmallButton
import com.family.health.ui.components.FhTextField
import com.family.health.ui.components.PageTitle
import com.family.health.ui.components.SegControl
import com.family.health.ui.components.SlotTags
import com.family.health.ui.switchTab
import com.family.health.ui.theme.FhColors
import com.family.health.util.todayStr

private data class NewMed(
    val name: String = "",
    val dosage: String = "",
    val kind: String = "western",
    val cat: String = "long_term",
    val slots: Set<String> = setOf("morning"),
    val end: String = "",
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MedChangeScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    val active = member.meds.filter { it.endDate == null }

    var date by remember { mutableStateOf(todayStr()) }
    var reason by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var linkedEventId by remember { mutableStateOf<String?>(null) }
    var stops by remember { mutableStateOf(setOf<String>()) }
    var adj by remember { mutableStateOf(mapOf<String, Pair<String, Set<String>>>()) }
    val news = remember { mutableListOf<NewMed>().toMutableStateList() }

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle("记用药变化", onBack = { nav.popBackStack() })

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                FieldLabel("生效日期")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                ) {
                    Text(date, fontSize = 14.sp, color = FhColors.Text)
                }
            }
            Column(Modifier.weight(1f)) {
                FhTextField(reason, { reason = it }, "备注")
            }
        }
        FieldLabel("关联复查")
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FChip("不关联", on = linkedEventId == null) { linkedEventId = null }
            member.events.forEach { e ->
                FChip(e.checkupDate, on = linkedEventId == e.id) {
                    linkedEventId = if (linkedEventId == e.id) null else e.id
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 8.dp)) {
            Text("当前用药 · 逐条标记", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
            Spacer(Modifier.weight(1f))
            Text("可叠加，再点取消", fontSize = 12.sp, color = FhColors.Text2)
        }
        if (active.isEmpty()) {
            FhCard { Text("当前无进行中用药，直接新增", fontSize = 13.sp, color = FhColors.Text2) }
        }
        active.forEach { med ->
            val stopped = med.id in stops
            val adjusting = adj.containsKey(med.id)
            FhCard(contentPadding = Modifier.padding(horizontal = 10.dp, vertical = 11.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (stopped) Modifier else Modifier,
                ) {
                    Text(
                        med.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (stopped) FhColors.Text2 else FhColors.Text,
                        textDecoration = if (stopped) TextDecoration.LineThrough else null,
                    )
                    if (med.medKind == "tcm") {
                        Spacer(Modifier.width(6.dp))
                        FTag("中药")
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(med.dosageText, fontSize = 13.sp, color = FhColors.Text2)
                    SlotTags(med.doseSlots)
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    OpChip("改量", adjusting) {
                        if (adjusting) {
                            adj = adj - med.id
                        } else {
                            adj = adj + (med.id to (med.dosageText to med.doseSlots.toSet()))
                            stops = stops - med.id
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OpChip("停用", stopped) {
                        if (stopped) stops = stops - med.id
                        else {
                            stops = stops + med.id
                            adj = adj - med.id
                        }
                    }
                }
                adj[med.id]?.let { (dos, sl) ->
                    Column(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .background(Color.Transparent),
                    ) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(FhColors.Line))
                        Spacer(Modifier.height(10.dp))
                        FhTextField(dos, { v -> adj = adj + (med.id to (v to sl)) }, "改后用法用量")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Labels.SLOTS.forEach { (key, label) ->
                                FChip(label, on = key in sl) {
                                    val ns = if (key in sl) sl - key else sl + key
                                    adj = adj + (med.id to (dos to ns))
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            "新增药品 / 药方", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
            modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 8.dp),
        )
        news.forEachIndexed { i, n ->
            FhCard(contentPadding = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                FhTextField(n.name, { news[i] = n.copy(name = it) }, "药品 / 方名")
                FhTextField(n.dosage, { news[i] = n.copy(dosage = it) }, "用法用量，如 每次 50mg")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("类别")
                        SegControl(listOf("西药", "中药"), if (n.kind == "tcm") 1 else 0) {
                            news[i] = n.copy(kind = if (it == 1) "tcm" else "western")
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("性质")
                        SegControl(listOf("长期", "临时"), if (n.cat == "temporary") 1 else 0) {
                            news[i] = n.copy(cat = if (it == 1) "temporary" else "long_term")
                        }
                    }
                }
                FieldLabel("服用时段")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Labels.SLOTS.forEach { (key, label) ->
                        FChip(label, on = key in n.slots) {
                            news[i] = n.copy(slots = if (key in n.slots) n.slots - key else n.slots + key)
                        }
                    }
                }
                if (n.cat == "temporary") {
                    Spacer(Modifier.height(14.dp))
                    FhTextField(n.end, { news[i] = n.copy(end = it) }, "止日（临时药）", placeholder = "2026-08-06")
                }
            }
        }
        FhSmallButton("＋ 新增一条") { news.add(NewMed()) }
        Spacer(Modifier.height(10.dp))
        FhButton(
            "保 存（停 ${stops.size} · 改 ${adj.size} · 增 ${news.count { it.name.isNotBlank() }}）",
            onClick = {
                val additions = news.map { n ->
                    MedicationItem(
                        id = vm.newMedId(),
                        name = n.name, dosageText = n.dosage,
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

    if (showDatePicker) {
        com.family.health.ui.components.FhDateTimePickerDialog(
            initialDate = date,
            initialTime = null,
            needDate = true,
            needTime = false,
            title = "生效日期",
            onConfirm = { d, _ ->
                if (d != null) date = d
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/** 标记操作钮（prototype .op） */
@Composable
private fun OpChip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = if (on) Color.White else FhColors.Text2,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (on) FhColors.OpDark else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

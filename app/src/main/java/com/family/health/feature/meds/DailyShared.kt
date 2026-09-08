// 契约：docs/05-页面结构与交互.md §3 今日用药卡/今日用药管理页
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.health.data.model.DailyMedItem
import com.family.health.data.model.Labels
import com.family.health.ui.components.FChip
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.FhTextField
import com.family.health.ui.theme.FhColors

/** 今日用药卡内容（概览页复用）：表格化（药品/用量/剩余/预计），中药独立置顶；勾选本机按日清零 */
@Composable
fun DailyMedList(
    items: List<DailyMedItem>,
    checks: Set<String>,
    onToggleCheck: (String) -> Unit,
) {
    if (items.isEmpty()) {
        Text(
            "还没设置今日用药，点右上角\"管理\"添加",
            fontSize = 13.sp, color = FhColors.Text2,
        )
        return
    }
    val west = items.filter { !it.isTcm }
    val tcm = items.filter { it.isTcm }

    // 表头
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 1.dp)) {
        Spacer(Modifier.width(46.dp))
        Text("药品", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Tiny,
            modifier = Modifier.weight(1.35f))
        Text("用量", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Tiny,
            modifier = Modifier.weight(1f))
        Text("剩余", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Tiny,
            modifier = Modifier.weight(0.95f))
        Text("预计", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Tiny,
            modifier = Modifier.weight(0.7f))
    }

    // 中药置顶（药品栏着色，无单独提示头）
    tcm.forEach { x ->
        val leftDays = ((x.tcmPacks ?: 0.0) * x.tcmDaysPerPack - x.tcmUsedDays).toInt()
        MedTableRow(
            checked = x.id in checks,
            barColor = FhColors.TcmText,
            nameColor = FhColors.TcmText,
            name = x.name, dose = "每副 ${x.tcmDaysPerPack} 天",
            stock = "${x.tcmPacks?.toInt() ?: 0} 副",
            days = "${maxOf(leftDays, 0)}天",
            onToggle = { onToggleCheck(x.id) },
        )
    }

    // 西药：按首个时段归组，左侧竖条按时段着色
    Labels.SLOTS.forEach { (key, _) ->
        west.filter { m -> m.doseSlots.firstOrNull() == key }.forEach { x ->
            MedTableRow(
                checked = x.id in checks,
                barColor = slotBarColor(key),
                nameColor = null,
                name = x.name, dose = x.doseText,
                stock = x.stockQty?.let { s -> "${s.toInt()}${x.stockUnit}" } ?: "—",
                days = x.daysLeft?.let { "${it}天" } ?: "—",
                onToggle = { onToggleCheck(x.id) },
            )
        }
    }
}

private fun slotBarColor(key: String): androidx.compose.ui.graphics.Color = when (key) {
    "morning" -> FhColors.Primary
    "noon" -> FhColors.Amber
    "evening" -> FhColors.TcmText
    else -> androidx.compose.ui.graphics.Color(0xFF6B7FA3) // bedtime 蓝灰
}

@Composable
private fun MedTableRow(
    checked: Boolean, barColor: androidx.compose.ui.graphics.Color, nameColor: androidx.compose.ui.graphics.Color?,
    name: String, dose: String, stock: String, days: String,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        // 40dp 点击域保证可点（内部 Checkbox 仅作展示）
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(40.dp)
                .clickable(onClick = onToggle),
        ) {
            androidx.compose.material3.Checkbox(
                checked = checked,
                onCheckedChange = null,
                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = FhColors.Primary),
            )
        }
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(barColor),
        )
        Text(
            name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = nameColor ?: if (checked) FhColors.Text2 else FhColors.Text,
            textDecoration = if (checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1.35f).padding(start = 6.dp),
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(dose, fontSize = 12.sp, color = FhColors.Text2, modifier = Modifier.weight(1f))
        Text(stock, fontSize = 12.sp, color = FhColors.Text2, modifier = Modifier.weight(0.95f))
        Text(days, fontSize = 12.sp, color = FhColors.Text2, modifier = Modifier.weight(0.7f))
    }
}

@Composable
private fun SlotHeader(label: String, tcm: Boolean) {
    Row(
        modifier = Modifier
            .padding(top = 8.dp, bottom = 3.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (tcm) FhColors.TcmSoft else FhColors.PrimarySoft)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (tcm) FhColors.TcmText else FhColors.Primary,
        )
    }
}

@Composable
private fun DailyCheckRow(
    checked: Boolean, name: String, dose: String, stock: String,
    onToggle: () -> Unit, onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = FhColors.Primary),
            modifier = Modifier.padding(end = 2.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    name, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (checked) FhColors.Text2 else FhColors.Text,
                    textDecoration = if (checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.width(8.dp))
                Text(dose, fontSize = 13.sp, color = FhColors.Text2)
            }
        }
        Text(stock, fontSize = 11.sp, color = FhColors.Text2)
    }
}

/** 今日用药编辑弹层（契约 §3：西药项/中药项的增删改） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyEditSheet(
    initial: DailyMedItem,
    onSave: (DailyMedItem) -> Unit,
    onDelete: ((String) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var dose by remember(initial.id) { mutableStateOf(initial.doseText) }
    var daily by remember(initial.id) { mutableStateOf(initial.dailyQty?.toInt()?.toString() ?: "1") }
    var stock by remember(initial.id) { mutableStateOf(initial.stockQty?.toInt()?.toString() ?: "") }
    var unit by remember(initial.id) { mutableStateOf(initial.stockUnit) }
    var slots by remember(initial.id) { mutableStateOf(initial.doseSlots.toSet()) }
    var packs by remember(initial.id) { mutableStateOf(initial.tcmPacks?.toInt()?.toString() ?: "0") }
    var dpp by remember(initial.id) { mutableStateOf(initial.tcmDaysPerPack.toString()) }
    var used by remember(initial.id) { mutableStateOf(initial.tcmUsedDays.toString()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(start = 18.dp, end = 18.dp, bottom = 26.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("编辑今日用药", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text2,
                modifier = Modifier.padding(bottom = 10.dp))
            FhTextField(name, { name = it }, "名称")
            if (initial.isTcm) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FhTextField(packs, { packs = it }, "剩余副数", Modifier.weight(1f), number = true)
                    FhTextField(dpp, { dpp = it }, "每副吃几天", Modifier.weight(1f), number = true)
                    FhTextField(used, { used = it }, "当前这副已吃", Modifier.weight(1f), number = true)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FhTextField(dose, { dose = it }, "每次用量", Modifier.weight(1f))
                    FhTextField(daily, { daily = it }, "每日用量/天", Modifier.weight(1f), number = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FhTextField(stock, { stock = it }, "剩余量", Modifier.weight(1f), number = true)
                    FhTextField(unit, { unit = it }, "单位", Modifier.weight(1f))
                }
                FieldLabel("服用时段")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Labels.SLOTS.forEach { (key, label) ->
                        FChip(label, on = key in slots) {
                            slots = if (key in slots) slots - key else slots + key
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            FhButton("保 存", onClick = {
                val saved = if (initial.isTcm) {
                    initial.copy(
                        name = name.ifBlank { initial.name },
                        tcmPacks = packs.toDoubleOrNull() ?: 0.0,
                        tcmDaysPerPack = dpp.toIntOrNull() ?: 1,
                        tcmUsedDays = used.toIntOrNull() ?: 0,
                    )
                } else {
                    initial.copy(
                        name = name.ifBlank { initial.name },
                        doseText = dose,
                        dailyQty = daily.toDoubleOrNull() ?: 1.0,
                        stockQty = stock.ifBlank { null }?.toDoubleOrNull(),
                        stockUnit = unit.ifBlank { "片" },
                        doseSlots = Labels.SLOTS.map { it.first }.filter { it in slots },
                    )
                }
                onSave(saved)
            })
            if (onDelete != null) {
                FhButton("删除此项", onClick = { onDelete(initial.id) }, ghost = true)
            }
        }
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

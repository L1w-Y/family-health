// 契约：docs/05-页面结构与交互.md §3 今日用药卡、§8 今日用药管理
package com.family.health.feature.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.health.data.nextUnavailableMedicationAt
import com.family.health.data.projectedMedicationStock
import com.family.health.data.model.DailyMedItem
import com.family.health.data.model.Labels
import com.family.health.data.model.MedicationItem
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.FhTextField
import com.family.health.ui.theme.FhColors
import com.family.health.ui.theme.FhSpace
import com.family.health.util.deviceTzOffsetMin
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun DailyMedList(
    items: List<DailyMedItem>,
    medications: List<MedicationItem>,
    nowMs: Long = System.currentTimeMillis(),
) {
    val activeById = medications.filter { it.endDate == null }.associateBy { it.id }
    val rows = items.mapNotNull { daily -> activeById[daily.medicationItemId]?.let { daily to it } }
    if (rows.isEmpty()) return

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 1.dp)) {
        Spacer(Modifier.width(30.dp))
        Header("药品", 1.35f); Header("用量", 1f); Header("剩余", .8f); Header("预计", 1f)
    }
    Labels.SLOTS.forEach { (slot, slotLabel) ->
        val group = rows.filter { (_, med) -> slot in med.doseSlots }
        if (group.isNotEmpty()) {
            SlotGroup(slot, slotLabel) {
                group.forEach { (daily, med) ->
                    val dose = med.doseQty ?: 0.0
                    val remaining = projectedMedicationStock(daily, dose, nowMs)[slot] ?: 0.0
                    val unavailableAt = nextUnavailableMedicationAt(slot, remaining, dose, nowMs, daily.tzOffsetMin)
                    MedDataRow(
                        name = med.name,
                        dose = if (dose > 0) "${numberText(dose)}${med.doseUnit}" else "待补用量",
                        stock = "${numberText(remaining)}${med.doseUnit}",
                        expected = unavailableAt?.let { deadlineText(it, nowMs, daily.tzOffsetMin) } ?: "—",
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.Header(text: String, weight: Float) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Tiny, modifier = Modifier.weight(weight))
}

@Composable
private fun SlotGroup(slot: String, label: String, rows: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.width(26.dp).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(slotSoftColor(slot)),
        ) {
            Text(label, fontSize = 10.sp, color = slotBarColor(slot), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) { rows() }
    }
}

@Composable
private fun MedDataRow(name: String, dose: String, stock: String, expected: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
            modifier = Modifier.weight(1.35f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(dose, fontSize = 12.sp, color = FhColors.Text2, modifier = Modifier.weight(1f))
        Text(stock, fontSize = 12.sp, color = FhColors.Text2, modifier = Modifier.weight(.8f))
        Text(expected, fontSize = 11.sp, color = FhColors.Text2, modifier = Modifier.weight(1f), maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyStockEditSheet(
    initial: DailyMedItem,
    medication: MedicationItem,
    onSave: (DailyMedItem) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var stocks by remember(initial.id) {
        mutableStateOf(medication.doseSlots.associateWith { initial.stockBySlot[it]?.let(::numberText) ?: "" })
    }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 26.dp).verticalScroll(rememberScrollState()),
        ) {
            Text(medication.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
            Text(
                "一次 ${medication.doseQty?.let(::numberText) ?: "—"}${medication.doseUnit} · 一天 ${medication.doseTimesPerDay ?: "—"} 次",
                fontSize = 13.sp, color = FhColors.Text2, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            Text("请填写各时段剩余药量", fontSize = 12.sp, color = FhColors.Text2, modifier = Modifier.padding(bottom = 6.dp))
            SlotStockGrid(medication, stocks) { slot, value -> stocks = stocks + (slot to value) }
            error?.let { Text(it, fontSize = 12.sp, color = FhColors.InputError, modifier = Modifier.padding(bottom = 8.dp)) }
            FhButton("保存盘点", onClick = {
                val parsed = medication.doseSlots.associateWith { stocks[it].orEmpty().toDoubleOrNull() }
                if (parsed.values.any { it == null || it < 0 }) {
                    error = "请填写每个药格的非负剩余数量"
                    return@FhButton
                }
                onSave(initial.copy(
                    stockBySlot = parsed.mapValues { it.value!! },
                    stockCountedAtMs = System.currentTimeMillis(),
                    tzOffsetMin = deviceTzOffsetMin(),
                ))
            })
            onDelete?.let { FhButton("移出今日用药", onClick = it, ghost = true) }
        }
    }
}

private fun deadlineText(deadlineMs: Long, nowMs: Long, offsetMin: Int): String {
    val offset = ZoneOffset.ofTotalSeconds(offsetMin * 60)
    val deadline = Instant.ofEpochMilli(deadlineMs).atOffset(offset).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atOffset(offset).toLocalDate()
    return when (deadline.toEpochDay() - today.toEpochDay()) {
        0L -> "今天"
        1L -> "明天"
        else -> "%02d/%02d".format(deadline.monthValue, deadline.dayOfMonth)
    }
}

internal fun numberText(value: Double): String =
    if (value == kotlin.math.floor(value)) value.toLong().toString() else value.toString().trimEnd('0').trimEnd('.')

private fun slotBarColor(key: String) = when (key) {
    "morning" -> FhColors.SlotMorning; "noon" -> FhColors.SlotNoon
    "evening" -> FhColors.SlotEvening; else -> FhColors.SlotBedtime
}

private fun slotSoftColor(key: String) = when (key) {
    "morning" -> FhColors.SlotMorningSoft; "noon" -> FhColors.SlotNoonSoft
    "evening" -> FhColors.SlotEveningSoft; else -> FhColors.SlotBedtimeSoft
}

@Composable
fun FieldLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
        modifier = Modifier.padding(bottom = 6.dp))
}

/** 四个时段（早/中/晚/睡前）固定展示；仅药品实际服用的时段可编辑，其余置灰。 */
@Composable
internal fun SlotStockGrid(
    medication: MedicationItem,
    stocks: Map<String, String>,
    onStockChange: (String, String) -> Unit,
) {
    Labels.SLOTS.chunked(2).forEach { slots ->
        Row(horizontalArrangement = Arrangement.spacedBy(FhSpace.Related)) {
            slots.forEach { (key, label) ->
                val editable = key in medication.doseSlots
                FhTextField(
                    value = stocks[key].orEmpty(),
                    onChange = { onStockChange(key, it) },
                    label = label,
                    modifier = Modifier.weight(1f),
                    decimal = true,
                    enabled = editable,
                    placeholder = if (editable) "剩余${medication.doseUnit}" else "不服用",
                )
            }
        }
    }
}

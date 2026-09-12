// 契约：docs/05-页面结构与交互.md §4.2 当日明细与编辑
package com.family.health.feature.records

import com.family.health.ui.theme.FhType

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.family.health.data.model.Labels
import com.family.health.data.model.Measurement
import com.family.health.ui.components.FChip
import com.family.health.ui.components.FhTextField
import com.family.health.ui.components.RowCard
import com.family.health.ui.components.RowLine1
import com.family.health.ui.theme.FhColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayDetailSheet(
    day: String,
    recs: List<Measurement>,
    onEdit: (Measurement) -> Unit,
    onDelete: (Measurement) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 26.dp)) {
            Text(
                "$day · ${recs.size} 条记录",
                fontSize = FhType.Label, fontWeight = FontWeight.Bold, color = FhColors.Text2,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            recs.forEach { x ->
                RowCard {
                    RowLine1 {
                        Text(measurementSummary(x), fontSize = FhType.Label, color = FhColors.Text)
                        Text(x.time, fontSize = FhType.Caption, color = FhColors.Text2)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 3.dp),
                    ) {
                        Text("由 ${x.createdBy} 录入", fontSize = FhType.Label, color = FhColors.Text2)
                        Spacer(Modifier.weight(1f))
                        Text("编辑", fontSize = FhType.Label, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
                            modifier = Modifier.clickable { onEdit(x) }.padding(horizontal = 6.dp))
                        Text("删除", fontSize = FhType.Label, fontWeight = FontWeight.SemiBold, color = FhColors.Amber,
                            modifier = Modifier.clickable { onDelete(x) }.padding(horizontal = 6.dp))
                    }
                }
            }
        }
    }
}

private fun measurementSummary(m: Measurement): String = when (m.type) {
    "bp" -> "血压 ${m.systolic}/${m.diastolic} mmHg" + (m.heartRateBpm?.let { " · 心率$it" } ?: "")
    "glucose" -> "血糖 ${m.glucoseMmol} mmol/L · ${Labels.sceneName(m.glucoseContext)}"
    else -> "心率 ${m.heartRateBpm} 次/分"
}

/** 单条编辑（契约 §4.2：修正手误，保留原录入署名） */
@Composable
internal fun MeasurementEditDialog(
    initial: Measurement,
    onSave: (Measurement) -> Unit,
    onDismiss: () -> Unit,
) {
    var sys by remember { mutableStateOf(initial.systolic?.toString() ?: "") }
    var dia by remember { mutableStateOf(initial.diastolic?.toString() ?: "") }
    var hr by remember { mutableStateOf(initial.heartRateBpm?.toString() ?: "") }
    var glu by remember { mutableStateOf(initial.glucoseMmol?.toString() ?: "") }
    var ctx by remember { mutableStateOf(initial.glucoseContext ?: "fasting") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑测量（署名保留：${initial.createdBy}）", fontSize = FhType.Body, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (initial.type == "bp") {
                    FhTextField(sys, { sys = it }, "高压 (mmHg)", number = true)
                    FhTextField(dia, { dia = it }, "低压 (mmHg)", number = true)
                    FhTextField(hr, { hr = it }, "心率", number = true)
                } else {
                    FhTextField(glu, { glu = it }, "血糖 (mmol/L)", decimal = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Labels.GLUCOSE_SCENES.take(3).forEach { (key, label) ->
                            FChip(label, on = ctx == key) { ctx = key }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Labels.GLUCOSE_SCENES.drop(3).forEach { (key, label) ->
                            FChip(label, on = ctx == key) { ctx = key }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val saved = if (initial.type == "bp") {
                    initial.copy(
                        systolic = sys.toIntOrNull() ?: return@TextButton,
                        diastolic = dia.toIntOrNull() ?: return@TextButton,
                        heartRateBpm = hr.toIntOrNull(),
                    )
                } else {
                    initial.copy(
                        glucoseMmol = glu.toDoubleOrNull() ?: return@TextButton,
                        glucoseContext = ctx,
                    )
                }
                onSave(saved)
            }) { Text("保存", color = FhColors.Primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = FhColors.Text2) }
        },
    )
}

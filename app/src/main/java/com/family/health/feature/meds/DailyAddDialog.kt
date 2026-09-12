// 契约：docs/05-页面结构与交互.md §8 今日用药管理
package com.family.health.feature.meds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.family.health.data.model.DailyMedItem
import com.family.health.data.model.MedicationItem
import com.family.health.data.model.dosageLabel
import com.family.health.data.newDailyMedExecutionId
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.FhSmallButton
import com.family.health.ui.components.RowCard
import com.family.health.ui.components.RowLine1
import com.family.health.ui.components.RowLine2
import com.family.health.ui.theme.FhColors
import com.family.health.ui.theme.FhShape
import com.family.health.ui.theme.FhSpace
import com.family.health.ui.theme.FhType
import com.family.health.util.deviceTzOffsetMin

@Composable
internal fun DailyAddDialog(
    medications: List<MedicationItem>,
    onSave: (DailyMedItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<MedicationItem?>(null) }
    var stocks by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    val matches = medications.filter { it.name.contains(query.trim(), ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = FhShape.Card,
            color = FhColors.Card,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(.92f).heightIn(max = 620.dp),
        ) {
            Column(Modifier.padding(FhSpace.Content)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("添加今日用药", fontSize = FhType.Section, fontWeight = FontWeight.Bold, color = FhColors.Text)
                    Spacer(Modifier.weight(1f))
                    FhSmallButton("取消", onClick = onDismiss)
                }
                MedicationSearchField(
                    query = query,
                    selected = selected,
                    onQueryChange = {
                        query = it
                        selected = null
                        error = null
                    },
                    onClear = {
                        selected = null
                        query = ""
                        stocks = emptyMap()
                        error = null
                    },
                )
                if (selected == null) {
                    Text("选择药品后填写各时段剩余药量", fontSize = FhType.Caption, color = FhColors.Text2)
                    if (matches.isEmpty()) {
                        Text("没有可添加的药品", fontSize = FhType.Caption, color = FhColors.Text2)
                    }
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(matches, key = { it.id }) { med ->
                            RowCard(onClick = {
                                selected = med
                                stocks = med.doseSlots.associateWith { "" }
                                error = null
                            }) {
                                RowLine1 { Text(med.name, fontWeight = FontWeight.Bold, color = FhColors.Text) }
                                RowLine2(med.dosageLabel)
                            }
                        }
                    }
                } else {
                    val med = selected!!
                    Text(med.dosageLabel, fontSize = FhType.Caption, color = FhColors.Text2)
                    SlotStockGrid(med, stocks) { slot, value -> stocks = stocks + (slot to value) }
                    error?.let {
                        Text(it, fontSize = FhType.Caption, color = FhColors.InputError)
                    }
                    FhButton("保存", pressFeedback = true, onClick = {
                        val parsed = med.doseSlots.associateWith { stocks[it].orEmpty().toDoubleOrNull() }
                        if (parsed.values.any { it == null || it < 0 }) {
                            error = "请填写每个药格的非负剩余数量"
                            return@FhButton
                        }
                        onSave(
                            DailyMedItem(
                                id = newDailyMedExecutionId(),
                                medicationItemId = med.id,
                                stockBySlot = parsed.mapValues { it.value!! },
                                stockCountedAtMs = System.currentTimeMillis(),
                                tzOffsetMin = deviceTzOffsetMin(),
                            )
                        )
                    })
                }
            }
        }
    }
}

@Composable
private fun MedicationSearchField(
    query: String,
    selected: MedicationItem?,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = selected?.name ?: query,
        onValueChange = { if (selected == null) onQueryChange(it) },
        readOnly = selected != null,
        singleLine = true,
        shape = FhShape.Control,
        textStyle = FhType.BodyStyle,
        placeholder = {
            Text(if (selected == null) "搜索药品" else "已选择", fontSize = FhType.Label, color = FhColors.Text2)
        },
        trailingIcon = if (selected != null) {
            {
                IconButton(onClick = onClear) {
                    Text("✕", fontSize = FhType.Body, color = FhColors.Text2)
                }
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = FhColors.Primary,
            unfocusedBorderColor = FhColors.Outline,
            focusedTextColor = FhColors.Text,
            unfocusedTextColor = FhColors.Text,
            disabledContainerColor = FhColors.DisabledContainer,
            disabledTextColor = FhColors.DisabledContent,
            errorBorderColor = FhColors.InputError,
            errorContainerColor = FhColors.InputErrorContainer,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = FhSpace.Related),
    )
}

// 契约：docs/05-页面结构与交互.md §8 今日用药管理
package com.family.health.feature.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.model.DailyMedItem
import com.family.health.data.model.Labels
import com.family.health.data.model.MedicationItem
import com.family.health.data.model.dosageLabel
import com.family.health.data.model.doseTimesMatchSlots
import com.family.health.data.projectedMedicationStock
import com.family.health.ui.components.PageTitle
import com.family.health.ui.components.RowCard
import com.family.health.ui.components.RowLine1
import com.family.health.ui.components.RowLine2
import com.family.health.ui.theme.FhColors
import com.family.health.ui.theme.FhType

@Composable
fun DailyManageScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    val activeMeds = member.meds.filter { it.endDate == null }
    val activeById = activeMeds.associateBy { it.id }
    val activeDaily = member.daily.filter { it.medicationItemId in activeById }
    val selectedMedicationIds = member.daily.map { it.medicationItemId }.toSet()
    val addable = activeMeds.filter {
        it.medKind == "western" && it.doseQty != null && it.doseQty > 0 &&
            doseTimesMatchSlots(it.doseTimesPerDay, it.doseSlots) && it.id !in selectedMedicationIds
    }
    var editing by remember { mutableStateOf<Pair<DailyMedItem, MedicationItem>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle("今日用药", onBack = { nav.popBackStack() }) {
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.semantics { contentDescription = "添加今日用药" },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(FhColors.Primary),
                ) {
                    Text("＋", fontSize = FhType.Item, fontWeight = FontWeight.Bold, color = FhColors.OnPrimary)
                }
            }
        }
        activeDaily.forEach { daily ->
            val med = activeById.getValue(daily.medicationItemId)
            val current = projectedMedicationStock(daily, med.doseQty ?: 0.0)
            RowCard(onClick = { editing = daily to med }) {
                RowLine1 {
                    Text(med.name, fontSize = FhType.Body, fontWeight = FontWeight.Bold, color = FhColors.Text)
                    Text(med.dosageLabel, fontSize = FhType.Caption, color = FhColors.Text2)
                }
                RowLine2(med.doseSlots.joinToString(" · ") { slot ->
                    "${Labels.slotName(slot)} ${numberText(current[slot] ?: 0.0)}${med.doseUnit}"
                })
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showAddDialog) {
        DailyAddDialog(
            medications = addable,
            onSave = {
                vm.upsertDaily(it)
                vm.toast("已加入今日用药")
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    editing?.let { (daily, med) ->
        val exists = member.daily.any { it.id == daily.id }
        DailyStockEditSheet(
            initial = daily,
            medication = med,
            onSave = {
                vm.upsertDaily(it)
                vm.toast("药格余量已同步")
                editing = null
            },
            onDelete = if (exists) ({
                vm.deleteDaily(daily.id)
                vm.toast("已移出今日用药")
                editing = null
            }) else null,
            onDismiss = { editing = null },
        )
    }
}

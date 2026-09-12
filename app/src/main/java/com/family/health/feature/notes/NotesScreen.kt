// 契约：docs/05-页面结构与交互.md §3 便签卡（列表/新建/编辑/完成）
package com.family.health.feature.notes

import com.family.health.ui.theme.FhType

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.ui.Routes
import com.family.health.ui.components.EmptyHint
import com.family.health.ui.components.FChip
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.FhTextField
import com.family.health.ui.components.PageTitle
import com.family.health.ui.components.RemindChip
import com.family.health.ui.theme.FhColors

@Composable
fun NotesScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle("便签", onBack = { nav.popBackStack() }) {
            Text(
                "＋ 新建", fontSize = FhType.Label, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
                modifier = Modifier
                    .clickable { nav.navigate(Routes.NOTE_FORM) }
                    .padding(4.dp),
            )
        }
        if (member.notes.isEmpty()) {
            EmptyHint("暂无便签")
        }
        member.notes.forEach { n ->
            FhCard(contentPadding = Modifier.padding(start = 10.dp, end = 16.dp, top = 13.dp, bottom = 13.dp)) {
                Row {
                    Checkbox(
                        checked = n.done,
                        onCheckedChange = { vm.toggleNote(n.id) },
                        colors = CheckboxDefaults.colors(checkedColor = FhColors.Primary),
                        modifier = Modifier.align(Alignment.Top),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            n.text, fontSize = FhType.Body, lineHeight = 22.sp,
                            color = if (n.done) FhColors.Text2 else FhColors.Text,
                            textDecoration = if (n.done) TextDecoration.LineThrough else null,
                        )
                        if (n.remindAt != null) {
                            RemindChip("${n.remindAt} · 提醒 ${n.remindTargetName ?: "全家"}")
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            Text(
                                "${n.createdBy} · ${n.createdAtLabel}",
                                fontSize = FhType.Caption, color = FhColors.Text2,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "删除", fontSize = FhType.Caption, fontWeight = FontWeight.SemiBold, color = FhColors.Amber,
                                modifier = Modifier
                                    .clickable {
                                        vm.deleteNote(n.id)
                                        vm.toast("已删除")
                                    }
                                    .padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteFormScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var text by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<String?>(null) }
    var time by remember { mutableStateOf<String?>(null) }
    var repeatDaily by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle("新建便签", form = true, onBack = { nav.popBackStack() })
        FhTextField(text, { text = it }, "内容", multiline = true)

        Text("提醒时刻", fontSize = FhType.Label, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
            modifier = Modifier.padding(bottom = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FChip(
                if (date != null && time != null) "$date $time" else "选择日期时间",
            ) { showPicker = true }
            if (date != null || time != null) {
                Spacer(Modifier.width(8.dp))
                FChip("清除") { date = null; time = null }
            }
        }
        if (date != null && time != null) {
            Row(modifier = Modifier.padding(top = 8.dp)) {
                FChip("单次", on = !repeatDaily) { repeatDaily = false }
                Spacer(Modifier.width(8.dp))
                FChip("每天", on = repeatDaily) { repeatDaily = true }
            }
        }
        Spacer(Modifier.height(14.dp))

        Text("提醒对象", fontSize = FhType.Label, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
            modifier = Modifier.padding(bottom = 6.dp))
        Row {
            FChip("全家", on = target == null) { target = null }
            ui.devices.forEach { d ->
                Spacer(Modifier.width(8.dp))
                FChip(d.displayName, on = target == d.displayName) { target = d.displayName }
            }
        }
        Spacer(Modifier.height(16.dp))
        FhButton("保 存", onClick = {
            if (text.isBlank()) {
                vm.toast("请填写内容")
                return@FhButton
            }
            if ((date == null) != (time == null)) {
                vm.toast("日期和时间要一起选")
                return@FhButton
            }
            if (date != null && time != null && !repeatDaily) {
                val t = runCatching { com.family.health.util.dateTimeToMs("$date $time") }.getOrNull()
                if (t != null && t <= System.currentTimeMillis()) {
                    vm.toast("提醒时间已过，请重新选择")
                    return@FhButton
                }
            }
            vm.addNote(text.trim(), date, time, repeatDaily, target)
            vm.toast("已保存")
            nav.popBackStack()
        })
        Spacer(Modifier.height(20.dp))
    }

    if (showPicker) {
        com.family.health.ui.components.FhDateTimePickerDialog(
            initialDate = date ?: java.time.LocalDate.now().toString(),
            initialTime = time ?: java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
            needDate = true,
            needTime = true,
            title = "提醒时刻",
            onConfirm = { d, t ->
                date = d; time = t
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

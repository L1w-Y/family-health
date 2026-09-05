// 契约：docs/05-页面结构与交互.md §3 便签卡（列表/新建/编辑/完成）
package com.family.health.feature.notes

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

    Column(modifier = Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
        PageTitle("便签", onBack = { nav.popBackStack() }) {
            Text(
                "＋ 新建", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
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
                            n.text, fontSize = 14.5.sp, lineHeight = 22.sp,
                            color = if (n.done) FhColors.Text2 else FhColors.Text,
                            textDecoration = if (n.done) TextDecoration.LineThrough else null,
                        )
                        if (n.remindAt != null) {
                            RemindChip("${n.remindAt} · 提醒 ${n.remindTargetName ?: "全家"}")
                        }
                        Text(
                            "${n.createdBy} · ${n.createdAtLabel}",
                            fontSize = 12.sp, color = FhColors.Text2, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoteFormScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var remindAt by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
        PageTitle("新建便签", onBack = { nav.popBackStack() })
        FhTextField(text, { text = it }, "内容", multiline = true, placeholder = "如：下周三上午去取药")
        FhTextField(remindAt, { remindAt = it }, "提醒时刻（可空）", placeholder = "09-11 18:00")
        Text("提醒对象", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
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
            vm.addNote(text.trim(), remindAt.ifBlank { null }, target)
            vm.toast("已保存")
            nav.popBackStack()
        })
        Spacer(Modifier.height(20.dp))
    }
}

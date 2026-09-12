// 契约：docs/05-页面结构与交互.md §4.1 复查事件详情
package com.family.health.feature.records

import com.family.health.ui.theme.FhType

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.ui.Routes
import com.family.health.ui.components.CardHead
import com.family.health.ui.components.EmptyHint
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.KvRow
import com.family.health.ui.components.PageTitle
import com.family.health.ui.components.RowCard
import com.family.health.ui.components.RowLine1
import com.family.health.ui.components.RowLine2
import com.family.health.ui.theme.FhColors
import com.family.health.ui.theme.FhShape

@Composable
fun EventDetailScreen(vm: AppViewModel, nav: NavHostController, eventId: String) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val event = ui.currentMember.events.firstOrNull { it.id == eventId }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .verticalScroll(rememberScrollState())
            .pointerInput(Unit) {
                // 点击空白区域（未被其它元素消费的点击）时清除焦点，用于退出备注编辑态
                awaitEachGesture {
                    if (awaitFirstDown(requireUnconsumed = true) != null) {
                        focusManager.clearFocus()
                    }
                }
            },
    ) {
        if (event == null) {
            PageTitle("复查", onBack = { nav.popBackStack() })
            EmptyHint("事件不存在")
            return@Column
        }
        PageTitle(event.checkupDate, onBack = { nav.popBackStack() })

        // 备注（一个框：点击进入编辑态，失焦即保存；展示/编辑同用 BasicTextField，无高度抖动；编辑态浮动边框）
        val noteFocus = remember { FocusRequester() }
        var editingNote by remember(event.id) { mutableStateOf(false) }
        var noteFocused by remember(event.id) { mutableStateOf(false) }
        var noteDraft by remember(event.id) { mutableStateOf(event.note) }
        Box(
            Modifier
                .fillMaxWidth()
                .shadow(if (editingNote) 8.dp else 0.dp, FhShape.Control)
                .clip(FhShape.Control)
                .border(1.dp, if (editingNote) FhColors.Primary else FhColors.Outline, FhShape.Control)
                .then(if (editingNote) Modifier else Modifier.clickable {
                    editingNote = true
                    noteFocused = false
                    noteDraft = event.note
                })
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = if (editingNote) noteDraft else event.note,
                onValueChange = { if (editingNote) noteDraft = it },
                enabled = editingNote,
                textStyle = TextStyle(fontSize = FhType.Label, color = FhColors.Text, lineHeight = 22.sp),
                cursorBrush = SolidColor(FhColors.Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(noteFocus)
                    .onFocusChanged { s ->
                        if (editingNote) {
                            if (s.isFocused) noteFocused = true
                            else if (noteFocused) {
                                vm.updateCheckupNote(event.id, noteDraft.trim())
                                editingNote = false
                            }
                        }
                    },
                decorationBox = { inner ->
                    Box {
                        val empty = if (editingNote) noteDraft.isEmpty() else event.note.isEmpty()
                        if (empty) {
                            Text(
                                if (editingNote) "填写本次复查的备注…" else "点击填写备注",
                                fontSize = FhType.Label, color = FhColors.Text2, lineHeight = 22.sp,
                            )
                        }
                        inner()
                    }
                },
            )
        }
        LaunchedEffect(editingNote) { if (editingNote) noteFocus.requestFocus() }
        if (event.medChangeSummary.isNotEmpty()) {
            FhCard {
                Text("本次用药变化", fontSize = FhType.Body, fontWeight = FontWeight.Bold, color = FhColors.Text,
                    modifier = Modifier.padding(bottom = 6.dp))
                Text(event.medChangeSummary, fontSize = FhType.Label, color = FhColors.Text, lineHeight = 22.sp)
            }
        }
        CardHead("报告（${event.reports.size}）")
        event.reports.forEachIndexed { i, r ->
            RowCard(onClick = { nav.navigate(Routes.report(event.id, i)) }) {
                RowLine1 {
                    Text(r.title, fontSize = FhType.Body, fontWeight = FontWeight.Bold, color = FhColors.Text)
                    Text(
                        buildString {
                            append(if (r.indicators.isNotEmpty()) "${r.indicators.size} 项指标" else "结论文字")
                            if (r.attachments > 0) append(" · 📎${r.attachments}")
                        },
                        fontSize = FhType.Caption, color = FhColors.Text2,
                    )
                }
                if (r.conclusionText.isNotEmpty()) {
                    RowLine2(r.conclusionText.take(30) + "…")
                }
            }
        }
        Text(
            "由 ${event.createdBy} 通过导入录入 · ${event.checkupDate}",
            fontSize = FhType.Caption, color = FhColors.Text2, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

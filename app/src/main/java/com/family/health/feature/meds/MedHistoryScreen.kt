// 契约：docs/05-页面结构与交互.md §5 历史用药独立页（按变化节点分段）
package com.family.health.feature.meds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.medHistory
import com.family.health.ui.Routes
import com.family.health.ui.components.EmptyHint
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.PageSub
import com.family.health.ui.components.PageTitle
import com.family.health.ui.theme.FhColors

@Composable
fun MedHistoryScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    val segs = medHistory(member.meds)

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle("历史用药", onBack = { nav.popBackStack() })
        PageSub("按变化节点分段 · 每个阶段在用什么药")
        if (segs.isEmpty()) {
            EmptyHint("暂无用药记录")
        }
        segs.forEach { seg ->
            val change = member.changes.firstOrNull { it.effectiveDate == seg.from }
            val linked = change?.linkedEventId?.let { eid -> member.events.firstOrNull { it.id == eid } }
            FhCard {
                Text(
                    "${seg.from} 起" + if (seg.to == "至今") " · 至今" else " ~ ${seg.to}",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
                )
                if (change != null && change.note.isNotEmpty()) {
                    Row12 {
                        Text(change.note, fontSize = 12.sp, color = FhColors.Text2)
                        if (linked != null) {
                            Text(
                                " · 关联 ${linked.checkupDate} 复查",
                                fontSize = 12.sp, color = FhColors.Primary,
                                modifier = Modifier.clickable { nav.navigate(Routes.event(linked.id)) },
                            )
                        }
                    }
                }
                Text(
                    "西药：" + (seg.active.filter { it.medKind == "western" }.joinToString("、") { it.name }.ifEmpty { "无" }),
                    fontSize = 13.5.sp, color = FhColors.Text, modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "中药：" + (seg.active.filter { it.medKind == "tcm" }.joinToString("、") { it.name }.ifEmpty { "无" }),
                    fontSize = 13.5.sp, color = FhColors.Text, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun Row12(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(top = 2.dp),
    ) { content() }
}

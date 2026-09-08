// 契约：docs/05-页面结构与交互.md §4.1 复查事件详情
package com.family.health.feature.records

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

@Composable
fun EventDetailScreen(vm: AppViewModel, nav: NavHostController, eventId: String) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val event = ui.currentMember.events.firstOrNull { it.id == eventId }

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        if (event == null) {
            PageTitle("复查", onBack = { nav.popBackStack() })
            EmptyHint("事件不存在")
            return@Column
        }
        PageTitle(event.checkupDate, onBack = { nav.popBackStack() })
        if (event.note.isNotEmpty()) {
            FhCard {
                Text(event.note, fontSize = 14.sp, color = FhColors.Text, lineHeight = 22.sp)
            }
        }
        if (event.medChangeSummary.isNotEmpty()) {
            FhCard {
                Text("本次用药变化", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
                    modifier = Modifier.padding(bottom = 6.dp))
                Text(event.medChangeSummary, fontSize = 14.sp, color = FhColors.Text, lineHeight = 22.sp)
            }
        }
        CardHead("报告（${event.reports.size}）")
        event.reports.forEachIndexed { i, r ->
            RowCard(onClick = { nav.navigate(Routes.report(event.id, i)) }) {
                RowLine1 {
                    Text(r.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                    Text(
                        buildString {
                            append(if (r.indicators.isNotEmpty()) "${r.indicators.size} 项指标" else "结论文字")
                            if (r.attachments > 0) append(" · 📎${r.attachments}")
                        },
                        fontSize = 12.sp, color = FhColors.Text2,
                    )
                }
                if (r.conclusionText.isNotEmpty()) {
                    RowLine2(r.conclusionText.take(30) + "…")
                }
            }
        }
        Text(
            "由 ${event.createdBy} 通过导入录入 · ${event.checkupDate}",
            fontSize = 12.sp, color = FhColors.Text2, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

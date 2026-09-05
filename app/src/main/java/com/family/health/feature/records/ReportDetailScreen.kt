// 契约：docs/05-页面结构与交互.md §4.1 报告详情（文字版为主视图 + 原件照片附件入口）
package com.family.health.feature.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.ui.Routes
import com.family.health.ui.components.EmptyHint
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.PageSub
import com.family.health.ui.components.PageTitle
import com.family.health.ui.theme.FhColors

@Composable
fun ReportDetailScreen(vm: AppViewModel, nav: NavHostController, eventId: String, index: Int) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val event = ui.currentMember.events.firstOrNull { it.id == eventId }
    val report = event?.reports?.getOrNull(index)

    Column(modifier = Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
        if (event == null || report == null) {
            PageTitle("报告", onBack = { nav.popBackStack() })
            EmptyHint("报告不存在")
            return@Column
        }
        PageTitle(report.title, onBack = { nav.popBackStack() })
        PageSub("${event.checkupDate} · ${event.hospital} ${event.department}")

        if (report.indicators.isNotEmpty()) {
            FhCard(contentPadding = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row {
                    Text("项目", style = headerStyle, modifier = Modifier.weight(1.4f).padding(vertical = 6.dp))
                    Text("结果", style = headerStyle, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
                    Text("参考区间", style = headerStyle, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
                }
                report.indicators.forEach { it ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { nav.navigate(Routes.indicator(it.itemName)) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(it.itemName, fontSize = 13.5.sp, color = FhColors.Text, modifier = Modifier.weight(1.4f))
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                            Text(it.displayValue, fontSize = 13.5.sp, color = FhColors.Text)
                            if (it.unit.isNotEmpty()) {
                                Text(" ${it.unit}", fontSize = 12.sp, color = FhColors.Text2)
                            }
                        }
                        Text(it.referenceRange.ifEmpty { "—" }, fontSize = 13.5.sp, color = FhColors.Text2,
                            modifier = Modifier.weight(1f))
                    }
                }
            }
            Text(
                "点任一指标行可查看该项历次变化 · 原样展示，无异常标注",
                fontSize = 12.sp, color = FhColors.Text2,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
            )
        }
        if (report.conclusionText.isNotEmpty()) {
            FhCard {
                Text("结论", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
                    modifier = Modifier.padding(bottom = 6.dp))
                Text(report.conclusionText, fontSize = 14.sp, color = FhColors.Text, lineHeight = 24.sp)
            }
        }
        if (report.attachments > 0) {
            FhCard {
                Text("原件照片 ${report.attachments} 张", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = FhColors.Text, modifier = Modifier.padding(bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(report.attachments) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(64.dp)
                                .height(84.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(FhColors.TagGray),
                        ) {
                            Text("🧾", fontSize = 20.sp)
                        }
                    }
                }
                Text(
                    "点击查看 / 下载原件（演示）",
                    fontSize = 12.sp, color = FhColors.Text2, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private val headerStyle
    @Composable get() = androidx.compose.ui.text.TextStyle(
        fontSize = 12.sp, color = FhColors.Text2, fontWeight = FontWeight.SemiBold,
    )

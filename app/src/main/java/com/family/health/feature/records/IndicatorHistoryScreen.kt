// 契约：docs/05-页面结构与交互.md §4.1 指标历史页（紧凑数值、无折线、仅 1 次时提示加入重点清单）
package com.family.health.feature.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.indicatorPoints
import com.family.health.ui.components.EmptyHint
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.PageSub
import com.family.health.ui.components.PageTitle
import com.family.health.ui.components.WarnBox
import com.family.health.ui.theme.FhColors

@Composable
fun IndicatorHistoryScreen(vm: AppViewModel, nav: NavHostController, name: String) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    val (pts, wl) = indicatorPoints(member, name)

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle(name, onBack = { nav.popBackStack() })
        PageSub(
            "共 ${pts.size} 次记录" + (
                if (wl != null) " · 已归并别名：${wl.aliases.joinToString("、")}"
                else " · 按报告原文名精确匹配"
                )
        )
        // 契约 §4.1：仅匹配到 1 次记录时给出提示与"加入重点清单"按钮
        if (pts.size == 1) {
            WarnBox {
                Text(
                    "仅匹配到 1 次记录。若其他报告中该指标用了不同名称（如 ACR 与 ACR(尿)），加入重点清单并配置别名后可合并历史。",
                    fontSize = 13.sp, color = FhColors.Amber, lineHeight = 21.sp,
                )
                Text(
                    "＋ 加入重点清单",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FhColors.Amber,
                    modifier = Modifier
                        .clickable {
                            vm.addWatch(name)
                            vm.toast("已加入重点清单")
                        }
                        .padding(top = 4.dp),
                )
            }
        }
        if (pts.isEmpty()) {
            EmptyHint("暂无记录")
        } else {
            FhCard(contentPadding = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                pts.asReversed().forEach { p ->
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(p.displayValue, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                            if (p.unit.isNotEmpty()) {
                                Text(" ${p.unit}", fontSize = 11.sp, color = FhColors.Text2)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${p.date} · ${p.department}", fontSize = 11.sp, color = FhColors.Text2)
                    }
                }
            }
        }
    }
}

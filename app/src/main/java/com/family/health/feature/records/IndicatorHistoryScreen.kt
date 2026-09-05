// 契约：docs/05-页面结构与交互.md §4.1 指标历史页（精确匹配/别名归并、仅 1 次时提示加入重点清单）
package com.family.health.feature.records

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
import com.family.health.data.indicatorPoints
import com.family.health.ui.components.ChartSeries
import com.family.health.ui.components.EmptyHint
import com.family.health.ui.components.LineChart
import com.family.health.ui.components.PageSub
import com.family.health.ui.components.PageTitle
import com.family.health.ui.components.RowCard
import com.family.health.ui.components.RowLine1
import com.family.health.ui.components.WarnBox
import com.family.health.ui.theme.FhColors

@Composable
fun IndicatorHistoryScreen(vm: AppViewModel, nav: NavHostController, name: String) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    val (pts, wl) = indicatorPoints(member, name)
    val numeric = pts.filter { it.isNumeric }

    Column(modifier = Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
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
        if (numeric.size >= 2) {
            LineChart(listOf(ChartSeries(name, numeric.map { it.valueNumeric!!.toFloat() })))
        }
        pts.asReversed().forEach { p ->
            RowCard {
                RowLine1 {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.Bottom,
                    ) {
                        Text(p.displayValue, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                        if (p.unit.isNotEmpty()) {
                            Text(" ${p.unit}", fontSize = 12.sp, color = FhColors.Text2)
                        }
                    }
                    Text("${p.date} · ${p.department}", fontSize = 12.sp, color = FhColors.Text2)
                }
            }
        }
        if (pts.isEmpty()) {
            EmptyHint("暂无记录")
        }
    }
}

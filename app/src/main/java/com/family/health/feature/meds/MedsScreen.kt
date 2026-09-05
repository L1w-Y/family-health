// 契约：docs/05-页面结构与交互.md §5 Tab 3 用药（方案 · 档案层）
package com.family.health.feature.meds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.family.health.ui.Routes
import com.family.health.ui.components.CardHead
import com.family.health.ui.components.FTag
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.MListCard
import com.family.health.ui.components.SlotTags
import com.family.health.ui.theme.FhColors

@Composable
fun MedsScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    val current = member.meds.filter { it.category == "long_term" && it.endDate == null }
    val temp = member.meds.filter { it.category == "temporary" && it.endDate == null }

    Column(modifier = Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 8.dp),
        ) {
            Text("当前方案 · 长期 ${current.size} 种", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
            Spacer(Modifier.weight(1f))
            Text("点卡片编辑详情", fontSize = 12.sp, color = FhColors.Text2)
        }
        if (current.isEmpty()) {
            FhCard { Text("暂无进行中的长期用药", fontSize = 13.sp, color = FhColors.Text2) }
        }
        current.forEach { med ->
            FhCard(onClick = { nav.navigate(Routes.medEdit(med.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(med.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                    if (med.medKind == "tcm") {
                        Spacer(Modifier.width(6.dp))
                        FTag("中药")
                    }
                    SlotTags(med.doseSlots)
                }
                Text(med.dosageText, fontSize = 14.sp, color = FhColors.Text, modifier = Modifier.padding(top = 5.dp))
            }
        }
        if (temp.isNotEmpty()) {
            FhCard {
                CardHead("临时用药", "辅助记录")
                temp.forEach { x ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 5.dp),
                    ) {
                        Text(x.name, fontSize = 14.sp, color = FhColors.Text)
                        if (x.medKind == "tcm") {
                            Spacer(Modifier.width(6.dp))
                            FTag("中药")
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${x.dosageText} · 至 ${x.endDate ?: "未定"}",
                            fontSize = 13.sp, color = FhColors.Text2,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        MListCard(listOf("历史用药" to "按阶段查看每个时期在用什么药")) {
            nav.navigate(Routes.MED_HISTORY)
        }
        FhButton("＋ 记用药变化", onClick = { nav.navigate(Routes.MED_CHANGE) })
        Spacer(Modifier.height(20.dp))
    }
}

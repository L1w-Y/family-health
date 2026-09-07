// 服务器与账号：当前连接信息、同步状态、家庭名编辑、重新配置（契约：docs/05 §9 我的）
package com.family.health.feature.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.FhTextField
import com.family.health.ui.components.KvRow
import com.family.health.ui.components.PageSub
import com.family.health.ui.components.PageTitle
import com.family.health.ui.theme.FhColors

@Composable
fun AccountScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val syncError by vm.syncError.collectAsStateWithLifecycle()
    val pending by vm.pendingSyncCount.collectAsStateWithLifecycle(initialValue = 0)
    var familyName by remember(ui.familyName) { mutableStateOf(ui.familyName) }
    val session = remember { vm.sessionInfo() }

    Column(modifier = Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
        PageTitle("服务器与账号", onBack = { nav.popBackStack() })
        PageSub("连接与同步状态；家庭名仅本机显示")

        FhCard {
            KvRow("服务器") { Text(session.server, fontSize = 14.sp, color = FhColors.Text) }
            KvRow("本机署名") { Text(session.deviceName, fontSize = 14.sp, color = FhColors.Text) }
            KvRow("家庭 ID") { Text(session.familyId, fontSize = 14.sp, color = FhColors.Text) }
            KvRow("同步") {
                Text(
                    when {
                        syncing -> "同步中…"
                        pending > 0 -> "待上行 $pending 条"
                        else -> "已是最新"
                    },
                    fontSize = 14.sp,
                    color = if (pending > 0) FhColors.Amber else FhColors.Text,
                )
            }
            syncError?.let {
                Text("最近同步错误：$it", fontSize = 12.sp, color = FhColors.Amber,
                    lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        FhCard {
            FhTextField(familyName, { familyName = it }, "家庭名（本机显示）")
            FhButton("保存家庭名", onClick = {
                vm.setFamilyName(familyName.ifBlank { "我的家庭" })
                vm.toast("已保存")
            }, ghost = true)
        }

        FhButton("立即同步", onClick = { vm.syncNow() })
        FhButton("重新配置服务器（清空本机数据后重连）", onClick = {
            vm.reconfigure()
        }, ghost = true)
        Spacer(Modifier.height(20.dp))
    }
}

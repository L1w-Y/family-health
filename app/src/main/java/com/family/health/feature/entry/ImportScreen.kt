// 契约：docs/05-页面结构与交互.md §8 记复查（导入）：粘贴 → 解析预览 → 逐项核对 → 确认入库
package com.family.health.feature.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.DemoData
import com.family.health.data.ImportFormatException
import com.family.health.data.model.CheckupEvent
import com.family.health.data.parseImportJson
import com.family.health.ui.Routes
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.FhTextField
import com.family.health.ui.components.OkBox
import com.family.health.ui.components.PageTitle
import com.family.health.ui.components.StepsBar
import com.family.health.ui.switchTab
import com.family.health.ui.theme.FhColors

@Composable
fun ImportScreen(vm: AppViewModel, nav: NavHostController) {
    var step by remember { mutableIntStateOf(1) }
    var text by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<CheckupEvent?>(null) }

    Column(modifier = Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
        PageTitle("记复查（导入）", onBack = { nav.popBackStack() })
        StepsBar(total = 4, active = step)

        when (step) {
            1 -> {
                FhTextField(
                    text, { text = it },
                    "粘贴外部 AI 整理好的文本（JSON）",
                    multiline = true,
                    placeholder = "{\"format\":\"family-health-import\",...}",
                )
                FhButton("填入示例文本", onClick = { text = DemoData.IMPORT_SAMPLE }, ghost = true)
                FhButton("解 析", onClick = {
                    try {
                        parsed = parseImportJson(text)
                        step = 2
                    } catch (e: ImportFormatException) {
                        vm.toast(e.message ?: "解析失败")
                    }
                })
            }
            2 -> parsed?.let { ev ->
                val cnt = ev.reports.sumOf { it.indicators.size }
                OkBox {
                    Text(
                        "✓ 校验通过：识别到 ${ev.checkupDate} 复查 · 报告 ${ev.reports.size} 份 · 指标 $cnt 项",
                        fontSize = 13.sp, color = FhColors.Primary, lineHeight = 21.sp,
                    )
                    Text(
                        "医院：${ev.hospital.ifEmpty { "—" }}　科室：${ev.department.ifEmpty { "—" }}\n下次复查：${ev.nextCheckupDate ?: "—"}",
                        fontSize = 13.sp, color = FhColors.Primary, lineHeight = 21.sp,
                    )
                }
                FhButton("展开逐项核对", onClick = { step = 3 }, ghost = true)
                FhButton("确认入库", onClick = {
                    vm.importEvent(ev)
                    vm.toast("已入库（演示）")
                    vm.setRecordsSeg("checkup")
                    nav.switchTab(Routes.RECORDS)
                })
                FhButton("返回修改", onClick = { step = 1 }, ghost = true)
            }
            3 -> parsed?.let { ev ->
                ev.reports.forEach { r ->
                    FhCard {
                        Text(r.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
                            modifier = Modifier.padding(bottom = 6.dp))
                        Row {
                            Text("项目", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
                                modifier = Modifier.weight(1.4f))
                            Text("结果", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
                                modifier = Modifier.weight(1f))
                            Text("参考区间", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
                                modifier = Modifier.weight(1f))
                        }
                        r.indicators.forEach { it ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(it.itemName, fontSize = 13.5.sp, color = FhColors.Text, modifier = Modifier.weight(1.4f))
                                Row(modifier = Modifier.weight(1f)) {
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
                }
                FhButton("确认入库", onClick = {
                    vm.importEvent(ev)
                    vm.toast("已入库（演示）")
                    vm.setRecordsSeg("checkup")
                    nav.switchTab(Routes.RECORDS)
                })
                FhButton("返回", onClick = { step = 2 }, ghost = true)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

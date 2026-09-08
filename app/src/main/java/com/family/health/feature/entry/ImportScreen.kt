// 契约：docs/05 §8 记复查（导入）：粘贴 → dry_run 预检 → 逐项核对 → 确认入库（docs/03 §2 dry_run）
package com.family.health.feature.entry

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
import com.family.health.data.ImportFormatException
import com.family.health.data.model.CheckupEvent
import com.family.health.data.parseImportJson
import com.family.health.syncclient.ImportResult
import com.family.health.ui.Routes
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.FhTextField
import com.family.health.ui.components.OkBox
import com.family.health.ui.components.PageTitle
import com.family.health.ui.components.StepsBar
import com.family.health.ui.switchTab
import com.family.health.ui.theme.FhColors

/** 示例文本（契约：docs/03-导入格式-v1.md §7） */
private const val IMPORT_SAMPLE = """{
  "format": "family-health-import",
  "version": 1,
  "import_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "profile_ref": { "name": "张国栋" },
  "payload": {
    "type": "checkup_event",
    "event": {
      "checkup_date": "2026-09-04",
      "hospital": "市人民医院",
      "department": "肾内科",
      "note": "ACR 较上次略升，继续观察，三个月后复查。",
      "next_checkup_date": "2026-12-04",
      "reports": [
        { "title": "尿常规", "indicators": [
          { "item_name": "尿蛋白", "value": "1+", "reference_range": "阴性" },
          { "item_name": "尿微量白蛋白/肌酐比值(ACR)", "value": 215.8, "unit": "mg/g", "reference_range": "<30" }
        ] },
        { "title": "肾功能", "indicators": [
          { "item_name": "血肌酐", "value": 136, "unit": "μmol/L", "reference_range": "41~81" }
        ] }
      ]
    }
  }
}"""

@Composable
fun ImportScreen(vm: AppViewModel, nav: NavHostController) {
    var step by remember { mutableIntStateOf(1) }
    var text by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<CheckupEvent?>(null) }
    var dry by remember { mutableStateOf<ImportResult?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        busy = true
        vm.importSubmit(text) { r ->
            busy = false
            r.onSuccess {
                vm.toast("已入库：报告 ${it.reportCount} 份 · 指标 ${it.indicatorCount} 项")
                vm.setRecordsSeg("checkup")
                nav.switchTab(Routes.RECORDS)
            }.onFailure {
                vm.toast(it.message ?: "入库失败")
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle("记复查", onBack = { nav.popBackStack() })
        StepsBar(total = 4, active = step)

        when (step) {
            1 -> {
                FhTextField(
                    text, { text = it },
                    "粘贴外部 AI 整理好的文本（JSON）",
                    multiline = true,
                    placeholder = "{\"format\":\"family-health-import\",...}",
                )
                FhButton("填入示例文本", onClick = { text = IMPORT_SAMPLE }, ghost = true)
                FhButton(if (busy) "预检中…" else "预 检", onClick = {
                    if (busy) return@FhButton
                    busy = true
                    vm.importDryRun(text) { r ->
                        busy = false
                        r.onSuccess { d ->
                            dry = d
                            preview = runCatching { parseImportJson(text) }.getOrNull()
                            step = 2
                        }.onFailure {
                            vm.toast(it.message ?: "预检失败")
                        }
                    }
                })
            }
            2 -> dry?.let { d ->
                val ev = preview
                OkBox {
                    Text(
                        "✓ 服务端预检通过：将写入 报告 ${d.reportCount} 份 · 指标 ${d.indicatorCount} 项",
                        fontSize = 13.sp, color = FhColors.Primary, lineHeight = 21.sp,
                    )
                    if (ev != null) {
                        Text(
                            "日期：${ev.checkupDate}　医院：${ev.hospital.ifEmpty { "—" }}　科室：${ev.department.ifEmpty { "—" }}\n下次复查：${ev.nextCheckupDate ?: "—"}",
                            fontSize = 13.sp, color = FhColors.Primary, lineHeight = 21.sp,
                        )
                    }
                }
                FhButton("展开逐项核对", onClick = { step = 3 }, ghost = true)
                FhButton(if (busy) "入库中…" else "确认入库", onClick = { submit() })
                FhButton("返回修改", onClick = { step = 1 }, ghost = true)
            }
            3 -> preview?.let { ev ->
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
                FhButton(if (busy) "入库中…" else "确认入库", onClick = { submit() })
                FhButton("返回", onClick = { step = 2 }, ghost = true)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

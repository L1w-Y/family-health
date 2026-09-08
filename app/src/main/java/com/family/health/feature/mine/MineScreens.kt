// 契约：docs/05-页面结构与交互.md §6 Tab 4 我的
package com.family.health.feature.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.feature.meds.FieldLabel
import com.family.health.ui.Routes
import com.family.health.ui.components.Avatar
import com.family.health.ui.components.CardHead
import com.family.health.ui.components.EmptyHint
import com.family.health.ui.components.FChip
import com.family.health.ui.components.FTag
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.FhCard
import com.family.health.ui.components.FhTextField
import com.family.health.ui.components.KvRow
import com.family.health.ui.components.MListCard
import com.family.health.ui.components.PageSub
import com.family.health.ui.components.PageTitle
import com.family.health.ui.components.RowCard
import com.family.health.ui.components.RowLine1
import com.family.health.ui.components.RowLine2
import com.family.health.ui.components.SegControl
import com.family.health.ui.components.WarnBox
import com.family.health.data.model.genderLabel
import com.family.health.ui.theme.FhColors

@Composable
fun MineScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        FhCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar("家", 44)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(ui.familyName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                    Text(
                        "${ui.members.size} 位成员 · ${ui.devices.size} 台设备",
                        fontSize = 12.sp, color = FhColors.Text2,
                    )
                }
            }
        }
        MListCard(
            listOf(
                "成员档案管理" to "${ui.members.size} 份档案",
                "家庭口令与设备" to "${ui.devices.size} 台设备",
                "提醒设置" to "服药 / 测量 / 复查",
                "API 令牌" to "供 HTTP 导入",
                "数据导出" to "JSON + 照片包",
                "服务器与账号" to "连接 / 同步状态",
                "关于与隐私说明" to "",
            )
        ) { i ->
            when (i) {
                0 -> nav.navigate(Routes.MEMBERS)
                1 -> nav.navigate(Routes.DEVICES)
                2 -> nav.navigate(Routes.REMINDERS)
                3 -> nav.navigate(Routes.TOKEN)
                4 -> nav.navigate(Routes.EXPORT)
                5 -> nav.navigate(Routes.ACCOUNT)
                6 -> nav.navigate(Routes.ABOUT)
            }
        }
    }
}

@Composable
fun MembersScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle("成员档案", onBack = { nav.popBackStack() }) {
            Text(
                "＋ 添加", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
                modifier = Modifier.clickable { nav.navigate(Routes.memberEdit(null)) }.padding(4.dp),
            )
        }
        ui.members.forEach { m ->
            RowCard(onClick = { nav.navigate(Routes.memberEdit(m.id)) }) {
                RowLine1 {
                    Text(m.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                    Text("${m.relation} · ${m.genderLabel}", fontSize = 12.sp, color = FhColors.Text2)
                }
                RowLine2(m.profileNote.ifEmpty { "未填写档案说明" })
            }
        }
    }
}

@Composable
fun MemberEditScreen(vm: AppViewModel, nav: NavHostController, memberId: String?) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val existing = ui.members.firstOrNull { it.id == memberId }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var relation by remember { mutableStateOf(existing?.relation ?: "") }
    var gender by remember { mutableStateOf(existing?.gender ?: "male") }
    var birth by remember { mutableStateOf(existing?.birthDate ?: "") }
    var note by remember { mutableStateOf(existing?.profileNote ?: "") }

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle(if (memberId != null) "编辑档案" else "添加成员", onBack = { nav.popBackStack() })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FhTextField(name, { name = it }, "姓名/称呼", Modifier.weight(1f))
            FhTextField(relation, { relation = it }, "关系", Modifier.weight(1f), placeholder = "爷爷")
        }
        FieldLabel("性别")
        SegControl(listOf("男", "女"), if (gender == "female") 1 else 0) {
            gender = if (it == 1) "female" else "male"
        }
        FhTextField(birth, { birth = it }, "出生日期", placeholder = "1952-03-12")
        FhTextField(note, { note = it }, "档案说明（自由书写：确诊疾病、过敏史、手术史…）", multiline = true)
        FhButton("保 存", onClick = {
            if (name.isBlank()) {
                vm.toast("请填写姓名")
                return@FhButton
            }
            vm.saveProfile(memberId, name.trim(), relation.trim(), gender, birth.trim(), note.trim())
            vm.toast("已保存")
            nav.popBackStack()
        })
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun DevicesScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle("家庭口令与设备", onBack = { nav.popBackStack() })
        FhCard {
            CardHead("家庭口令", "更换 ›") { vm.toast("更换后所有设备需重新输入") }
            Text("新设备首次使用需输入家庭口令完成署名", fontSize = 13.sp, color = FhColors.Text2)
        }
        Text(
            "已接入设备", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
            modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 8.dp),
        )
        ui.devices.forEach { d ->
            RowCard {
                RowLine1 {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                        if (d.self) {
                            Spacer(Modifier.width(6.dp))
                            FTag("本机")
                        }
                    }
                    if (d.self) {
                        Text("当前设备", fontSize = 12.sp, color = FhColors.Text2)
                    } else {
                        Text(
                            "吊销", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
                            modifier = Modifier.clickable {
                                vm.revokeDevice(d.id)
                                vm.toast("已吊销")
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RemindersScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val member = ui.currentMember
    val r = ui.reminders[member.id] ?: com.family.health.data.model.ReminderSetting()

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle("提醒设置", onBack = { nav.popBackStack() })
        PageSub("当前成员：${member.name}")

        FhCard {
            Text("服药提醒", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
                modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.medTimes.forEachIndexed { i, t ->
                    FChip(t, on = true, trailing = {
                        Text("×", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { vm.removeReminderTime(member.id, "med", i) })
                    })
                }
                FChip("＋") {
                    vm.addReminderTime(member.id, "med")
                    vm.toast("已添加 12:00，可点 × 删除")
                }
            }
            Text(
                "通知内容自动拼接当前用药方案，用药变动无需改提醒",
                fontSize = 12.sp, color = FhColors.Text2, modifier = Modifier.padding(top = 8.dp),
            )
        }
        FhCard {
            Text("测量提醒", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
                modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.measureTimes.forEachIndexed { i, t ->
                    FChip(t, on = true, trailing = {
                        Text("×", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { vm.removeReminderTime(member.id, "measure", i) })
                    })
                }
                FChip("＋") {
                    vm.addReminderTime(member.id, "measure")
                    vm.toast("已添加 12:00，可点 × 删除")
                }
            }
        }
        FhCard {
            Text("复查提醒", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text,
                modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.advanceDays.forEach { d ->
                    FChip(if (d == 0) "当天" else "提前${d}天", on = true)
                }
            }
            Text(
                "按复查事件的\"下次复查日期\"触发",
                fontSize = 12.sp, color = FhColors.Text2, modifier = Modifier.padding(top = 8.dp),
            )
        }
        WarnBox {
            Text(
                "首次使用请按引导开启\"自启动 / 电池无限制\"，避免系统杀后台导致通知延迟。",
                fontSize = 13.sp, color = FhColors.Amber, lineHeight = 21.sp,
            )
        }
    }
}

@Composable
fun TokenScreen(vm: AppViewModel, nav: NavHostController) {
    val session = remember { vm.sessionInfo() }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle("API 令牌", onBack = { nav.popBackStack() })
        PageSub("本机设备令牌即 HTTP 导入凭证（Authorization: Bearer <令牌>），与 App 粘贴导入同一格式")
        FhCard {
            KvRow("当前令牌") {
                Text(
                    vm.deviceToken().let { if (it.length > 12) it.take(8) + "…" + it.takeLast(6) else it },
                    fontSize = 14.sp, color = FhColors.Text,
                )
            }
            KvRow("署名") { Text(session.deviceName, fontSize = 14.sp, color = FhColors.Text) }
        }
        FhButton("复制完整令牌", onClick = {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(vm.deviceToken()))
            vm.toast("已复制")
        }, ghost = true)
        Text(
            "换新令牌：在\"服务器与账号\"里重新配置（会清空本机数据并重新拉取）。",
            fontSize = 12.sp, color = FhColors.Text2, lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun ExportScreen(vm: AppViewModel, nav: NavHostController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle("数据导出", onBack = { nav.popBackStack() })
        ui.members.forEach { m ->
            RowCard {
                RowLine1 {
                    Text(m.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
                    Text(
                        "导出 ›", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
                        modifier = Modifier.clickable { vm.toast("导出功能将在后续版本提供") },
                    )
                }
                RowLine2("${m.events.size} 次复查 · ${m.measurements.size} 条测量 · ${m.meds.size} 条用药")
            }
        }
        Text(
            "导出格式与导入格式 v1 同字段，导出物可再导入（防锁定保险）。",
            fontSize = 12.sp, color = FhColors.Text2, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun AboutScreen(vm: AppViewModel, nav: NavHostController) {
    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle("关于与隐私", onBack = { nav.popBackStack() })
        FhCard {
            Text(
                "本应用是家庭内部的健康记录工具：\n" +
                    "· 不做医学判断，不标注异常，不提供健康建议\n" +
                    "· 报告参考区间为原文存档，请以医生解读为准\n" +
                    "· 数据存储于家庭自有的云环境，可随时导出\n" +
                    "· 整理报告文本给外部 AI 前，请先隐去姓名、证件号",
                fontSize = 14.sp, color = FhColors.Text, lineHeight = 27.sp,
            )
        }
    }
}

// 契约：docs/05-页面结构与交互.md §4.2 测量段（粒度 日/周/月 + 周期导航 + 异常标红）
package com.family.health.feature.records

import com.family.health.ui.theme.FhType

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.health.data.MeasurementGranularity
import com.family.health.data.MeasurementWindow
import com.family.health.data.formatWindowTitle
import com.family.health.data.model.Labels
import com.family.health.data.model.Measurement
import com.family.health.ui.components.FChip
import com.family.health.ui.components.SegControl
import com.family.health.ui.components.FhDateTimePickerDialog
import com.family.health.ui.components.EmptyHint
import com.family.health.ui.theme.FhColors
import com.family.health.ui.theme.FhShape
import java.time.LocalDate

/**
 * 测量段（血压/血糖）—— 单一时间导航体系：
 *  ① 类型 SegControl
 *  ② 粒度 SegControl（日/周/月）
 *  ③ 周期导航 ‹ 标题 ›（标题可点击弹日历，右箭头在当前周期置灰）
 *  ④ 统计摘要
 *  ⑤ 数据列表（按天分组、异常标红）
 *  左右滑动列表区域 = 翻上一/下一周期。
 */
@Composable
internal fun MeasurementLog(
    measurements: List<Measurement>, memberId: String, type: String,
    granularity: MeasurementGranularity, anchor: LocalDate, today: LocalDate,
    onGranularityChange: (MeasurementGranularity) -> Unit,
    onShift: (Int) -> Unit,
    onAnchorChange: (LocalDate) -> Unit,
    onDayClick: (String) -> Unit,
    onTypeChange: (String) -> Unit,
) {
    val window = MeasurementWindow(granularity, anchor)
    val isCurrent = window.contains(today)
    var scene by rememberSaveable(memberId) { mutableStateOf<String?>(null) }
    var showCalendar by remember { mutableStateOf(false) }

    val groups = remember(measurements, type, window, scene, today) {
        measurementGroupsInWindow(measurements, type, window, scene, today)
    }
    val totalCount = groups.sumOf { it.records.size }
    val dayCount = groups.count { it.records.isNotEmpty() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ① 类型
        Row(Modifier.fillMaxWidth()) {
            SegControl(listOf("血压", "血糖"), if (type == "glucose") 1 else 0, compact = true) {
                onTypeChange(if (it == 1) "glucose" else "bp")
            }
        }
        // ② 粒度（日/周/月）—— 居中胶囊
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            GranularityPills(granularity) { onGranularityChange(it) }
        }
        // ③ 周期导航
        PeriodNavBar(
            title = formatWindowTitle(window, today),
            onPrev = { onShift(-1) },
            onNext = { onShift(1) },
            canNext = !isCurrent,
            onTitleClick = { showCalendar = true },
        )
        // ④ 统计摘要（灰色居中小字）
        StatsSummary(granularity, totalCount, dayCount)
        // 血糖：场景筛选（紧凑一行，紧贴摘要下方）
        if (type == "glucose") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FChip("全部", on = scene == null) { scene = null }
                Labels.GLUCOSE_SCENES.forEach { (key, _) ->
                    FChip(Labels.sceneName(key), on = scene == key) { scene = key }
                }
            }
        }
        // ⑤ 数据列表（左右滑翻页）
        SwipeableList(
            type = type, today = today, groups = groups,
            onDayClick = onDayClick,
            onSwipePrev = { onShift(-1) },
            onSwipeNext = { if (!isCurrent) onShift(1) },
        )
    }

    if (showCalendar) {
        FhDateTimePickerDialog(
            initialDate = anchor.toString(), initialTime = null,
            needDate = true, needTime = false, title = "选择日期",
            onConfirm = { d, _ -> d?.let { onAnchorChange(LocalDate.parse(it)) } ; showCalendar = false },
            onDismiss = { showCalendar = false },
        )
    }
}

/** 粒度切换：居中胶囊，三个选项，命中态青绿底 */
@Composable
private fun GranularityPills(
    current: MeasurementGranularity,
    onSelect: (MeasurementGranularity) -> Unit,
) {
    val options = listOf(MeasurementGranularity.Day to "日", MeasurementGranularity.Week to "周", MeasurementGranularity.Month to "月")
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(FhColors.ChipGray)
            .padding(2.dp),
    ) {
        options.forEach { (g, label) ->
            val on = g == current
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) FhColors.Primary else FhColors.ChipGray)
                    .clickable { onSelect(g) }
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                Text(
                    label, fontSize = FhType.Label,
                    color = if (on) FhColors.OnPrimary else FhColors.Text2,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 周期导航：‹ 标题(可点击弹日历) › ；右箭头在当前周期置灰 */
@Composable
private fun PeriodNavBar(
    title: String,
    onPrev: () -> Unit, onNext: () -> Unit, canNext: Boolean,
    onTitleClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NavRoundButton("‹", enabled = true, onClick = onPrev)
        Text(
            title, fontSize = FhType.Body, fontWeight = FontWeight.SemiBold, color = FhColors.Text,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onTitleClick() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        NavRoundButton("›", enabled = canNext, onClick = onNext)
    }
}

/** 圆形翻页按钮 */
@Composable
private fun NavRoundButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(FhColors.Card)
            .border(1.dp, FhColors.Outline, CircleShape)
            .let { if (enabled) it.clickable { onClick() } else it }
            .padding(4.dp),
    ) {
        Text(symbol, fontSize = 16.sp, color = if (enabled) FhColors.Text2 else FhColors.Tiny)
    }
}

/** 统计摘要：「本月 X 次记录 · X 天有数据」 */
@Composable
private fun StatsSummary(granularity: MeasurementGranularity, total: Int, days: Int) {
    val label = when (granularity) {
        MeasurementGranularity.Day -> "本日"
        MeasurementGranularity.Week -> "本周"
        MeasurementGranularity.Month -> "本月"
    }
    Text(
        "$label $total 次记录 · $days 天有数据",
        fontSize = FhType.Caption, color = FhColors.Text2,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

/** 列表区域 + 左右滑翻页手势 */
@Composable
private fun SwipeableList(
    type: String, today: LocalDate, groups: List<MeasurementDay>,
    onDayClick: (String) -> Unit,
    onSwipePrev: () -> Unit, onSwipeNext: () -> Unit,
) {
    val totalDx = remember { mutableStateOf(0f) }
    Box(
        Modifier.fillMaxWidth().pointerInput(type, groups) {
            detectHorizontalDragGestures(
                onDragStart = { totalDx.value = 0f },
                onHorizontalDrag = { _, delta -> totalDx.value += delta },
                onDragEnd = {
                    val dx = totalDx.value
                    val threshold = 60f * density
                    if (dx <= -threshold) onSwipePrev()
                    else if (dx >= threshold) onSwipeNext()
                    totalDx.value = 0f
                },
                onDragCancel = { totalDx.value = 0f },
            )
        },
    ) {
        if (type == "bp") BpTable(groups, today, onDayClick) else GlucoseTable(groups, today, onDayClick)
    }
}

/** 异常判定：BP 高压≥140 或低压≥90；血糖≥11.1 mmol/L */
private fun abnormalBp(s: Int?, d: Int?): Boolean = (s != null && s >= 140) || (d != null && d >= 90)
private fun abnormalGlu(g: Double?): Boolean = g != null && g >= 11.1

// 表列权重（总和 1）；右三列在组内按同比例再归一，保证与表头对齐
private val dayWeight = 0.26f
private val timeWeight = 0.16f
private val valueWeight = 0.34f
private val pulseWeight = 0.24f
private val rightTotal = timeWeight + valueWeight + pulseWeight

@Composable
private fun BpTable(groups: List<MeasurementDay>, today: LocalDate, onDayClick: (String) -> Unit) {
    if (groups.isEmpty() || (groups.size == 1 && groups[0].records.isEmpty())) {
        EmptyHint("该周期暂无血压记录")
        return
    }
    MeasurementTable(
        groups, today, onDayClick,
        valueLabel = "高压 / 低压", valueUnit = "", pulseLabel = "心率", pulseUnit = "", isBp = true,
    )
}

@Composable
private fun GlucoseTable(groups: List<MeasurementDay>, today: LocalDate, onDayClick: (String) -> Unit) {
    if (groups.isEmpty() || (groups.size == 1 && groups[0].records.isEmpty())) {
        EmptyHint("该周期暂无血糖记录")
        return
    }
    MeasurementTable(
        groups, today, onDayClick,
        valueLabel = "血糖", valueUnit = "", pulseLabel = "测量标签", pulseUnit = "", isBp = false,
    )
}

/** 统一四列测量表：日期列贯穿当日，右三列与表头严格对齐 */
@Composable
private fun MeasurementTable(
    groups: List<MeasurementDay>, today: LocalDate, onDayClick: (String) -> Unit,
    valueLabel: String, valueUnit: String, pulseLabel: String, pulseUnit: String, isBp: Boolean,
) {
    Column(Modifier.fillMaxWidth().clip(FhShape.Card)) {
        // 表头
        Row(
            Modifier.fillMaxWidth().background(FhColors.SurfaceVariant).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("日期", Modifier.weight(dayWeight))
            HeaderCell("时间", Modifier.weight(timeWeight))
            HeaderCell(valueLabel, valueUnit, Modifier.weight(valueWeight))
            HeaderCell(pulseLabel, pulseUnit, Modifier.weight(pulseWeight))
        }
        groups.forEach { g ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                DayCell(g.date, today, Modifier.weight(dayWeight)) { onDayClick(g.date.toString()) }
                Column(Modifier.weight(1f - dayWeight)) {
                    g.records.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(r.time, Modifier.weight(timeWeight / rightTotal), fontSize = FhType.Caption, color = FhColors.Text2, textAlign = TextAlign.Center)
                            val (valueText, bad) = if (isBp) {
                                ("${r.systolic ?: "—"} / ${r.diastolic ?: "—"}") to abnormalBp(r.systolic, r.diastolic)
                            } else {
                                val t = r.glucoseMmol?.let {
                                    if (it == Math.floor(it) && !it.isInfinite()) it.toLong().toString() else it.toString().trimEnd('0').trimEnd('.')
                                } ?: "—"
                                t to abnormalGlu(r.glucoseMmol)
                            }
                            Text(
                                valueText, Modifier.weight(valueWeight / rightTotal), fontSize = FhType.Body,
                                fontWeight = FontWeight.Bold, color = if (bad) FhColors.InputError else FhColors.Text,
                                textAlign = TextAlign.Center,
                            )
                            Box(Modifier.weight(pulseWeight / rightTotal).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                                if (isBp) {
                                    Text(r.heartRateBpm?.toString() ?: "—", fontSize = FhType.Caption, color = FhColors.Text2)
                                } else {
                                    Text(
                                        r.glucoseContext?.let { Labels.sceneName(it) } ?: "—",
                                        fontSize = FhType.Caption, color = FhColors.Text2,
                                        modifier = Modifier
                                            .clip(FhShape.Tag)
                                            .background(FhColors.TagGray)
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 日期单元格：月日 + 星期（当天"今天"），贯穿该日 */
@Composable
private fun DayCell(date: LocalDate, today: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.fillMaxHeight().clickable { onClick() }.padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${date.monthValue}月${date.dayOfMonth}日", fontSize = FhType.Body, fontWeight = FontWeight.SemiBold, color = FhColors.Text)
        if (date == today) {
            Text("今天", fontSize = FhType.Caption, color = FhColors.Primary, modifier = Modifier.padding(top = 2.dp))
        } else {
            Text(weekdayCnShort(date.dayOfWeek.value), fontSize = FhType.Caption, color = FhColors.Tiny, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun HeaderCell(label: String, modifier: Modifier) {
    Text(label, fontSize = FhType.Caption, color = FhColors.Text2, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = modifier)
}

@Composable
private fun HeaderCell(label: String, unit: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = FhType.Caption, lineHeight = 14.sp, color = FhColors.Text2, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (unit.isNotEmpty()) Text(unit, fontSize = FhType.Caption, lineHeight = 14.sp, color = FhColors.Text2)
    }
}

private fun weekdayCnShort(v: Int): String = when (v) {
    1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"; 5 -> "周五"; 6 -> "周六"; 7 -> "周日"
    else -> ""
}

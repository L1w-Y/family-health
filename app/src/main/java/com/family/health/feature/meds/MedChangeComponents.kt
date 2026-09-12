// 契约：docs/05-页面结构与交互.md §5 记用药变化
package com.family.health.feature.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.family.health.ui.components.FhTextField
import com.family.health.ui.components.WheelColumn
import com.family.health.ui.theme.FhColors
import com.family.health.ui.theme.FhType
import java.time.LocalDate
import java.time.YearMonth

internal data class NewMed(
    val name: String = "",
    val dosage: String = "",
    val doseQty: String = "",
    val doseUnit: String = "片",
    val doseTimes: String = "1",
    val kind: String = "western",
    val cat: String = "long_term",
    val slots: Set<String> = setOf("morning"),
    val end: String = "",
)

@Composable
internal fun WesternDoseFields(
    doseQty: String,
    onDoseQty: (String) -> Unit,
    doseUnit: String,
    onDoseUnit: (String) -> Unit,
    doseTimes: String,
    onDoseTimes: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FhTextField(doseQty, onDoseQty, "每次数量", Modifier.weight(1f), decimal = true)
        FhTextField(doseUnit, onDoseUnit, "单位", Modifier.weight(1f))
        FhTextField(doseTimes, { onDoseTimes(it.filter(Char::isDigit)) }, "一天次数", Modifier.weight(1f))
    }
}

/** 改量/停用边框操作钮：未选中=彩色边框+彩字透明底，选中=彩底白字。 */
@Composable
internal fun OpButton(text: String, color: Color, on: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = FhType.Label,
        fontWeight = FontWeight.SemiBold,
        color = if (on) Color.White else color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) color else Color.Transparent)
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** 虚线边框入口框：「+ 新增药品/药方」。 */
@Composable
internal fun DashedAddBox(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                val stroke = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                )
                drawRoundRect(
                    color = FhColors.Primary,
                    style = stroke,
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                )
            }
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Text(text, fontSize = FhType.Label, color = FhColors.Primary, fontWeight = FontWeight.SemiBold)
    }
}

/** 内嵌日期滚轮选择器（年/月/日三列，内嵌卡片，非弹窗）。 */
@Composable
internal fun InlineDateWheel(date: String, onDate: (String) -> Unit) {
    val d = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
    var year by remember(d.year) { mutableIntStateOf(d.year) }
    var month by remember(d.monthValue) { mutableIntStateOf(d.monthValue) }
    var day by remember(d.dayOfMonth) { mutableIntStateOf(d.dayOfMonth) }
    val daysInMonth = runCatching { YearMonth.of(year, month).lengthOfMonth() }.getOrDefault(31)
    val safeDay = day.coerceIn(1, daysInMonth)
    LaunchedEffect(safeDay) { if (day != safeDay) day = safeDay }
    LaunchedEffect(year, month, safeDay) {
        onDate("%04d-%02d-%02d".format(year, month, safeDay))
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        WheelColumn((2024..2030).map { "${it}年" }, year - 2024, { year = it + 2024 }, Modifier.weight(1.2f))
        WheelColumn((1..12).map { "${it}月" }, month - 1, { month = it + 1 }, Modifier.weight(1f))
        WheelColumn((1..daysInMonth).map { "${it}日" }, safeDay - 1, { day = it + 1 }, Modifier.weight(1f))
    }
}

// 契约：docs/05-页面结构与交互.md §8 记血压/血糖
package com.family.health.feature.entry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.family.health.data.AppViewModel
import com.family.health.data.model.Labels
import com.family.health.data.model.Measurement
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.PageTitle
import com.family.health.ui.components.WheelColumn
import com.family.health.ui.theme.FhColors
import com.family.health.ui.theme.FhType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID

/**
 * 记血压 / 记血糖。
 * 血压和血糖均使用紧凑四列输入；概览时段由测量时间推导，血糖场景只表达测量语义。
 */
@Composable
fun MeasureFormScreen(vm: AppViewModel, nav: NavHostController, type: String) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val isGlucose = type == "glucose"
    val title = if (isGlucose) "记血糖" else "记血压"
    val now = remember { LocalTime.now() }

    var systolic by remember { mutableStateOf("") }
    var diastolic by remember { mutableStateOf("") }
    var heartRate by remember { mutableStateOf("") }
    var glucose by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val sceneKeys = remember { Labels.GLUCOSE_SCENES.map { it.first } }
    var sceneIndex by remember {
        mutableIntStateOf(sceneKeys.indexOf(ui.lastGlucoseCtx).coerceAtLeast(0))
    }

    // 测量时间默认真实当前时刻；保留原有年、月、日、时、分选择与日期裁剪逻辑。
    val today = remember { LocalDate.now() }
    val yearRange = 2024..2030
    var year by remember { mutableIntStateOf(today.year) }
    var month by remember { mutableIntStateOf(today.monthValue) }
    var day by remember { mutableIntStateOf(today.dayOfMonth) }
    var hour by remember { mutableIntStateOf(now.hour) }
    var minute by remember { mutableIntStateOf(now.minute) }
    val daysInMonth = runCatching { YearMonth.of(year, month).lengthOfMonth() }.getOrDefault(31)
    val safeDay = day.coerceIn(1, daysInMonth)
    LaunchedEffect(safeDay) { if (day != safeDay) day = safeDay }

    fun save() {
        val createdBy = ui.devices.firstOrNull { it.self }?.displayName ?: "爸爸"
        val measuredAt = "%04d-%02d-%02d %02d:%02d".format(year, month, safeDay, hour, minute)
        val selectedAt = LocalDateTime.of(year, month, safeDay, hour, minute)
        if (selectedAt.isAfter(LocalDateTime.now())) {
            vm.toast("测量时间不能晚于现在")
            return
        }

        val measurement = if (isGlucose) {
            val value = glucose.toDoubleOrNull()
            if (value == null) {
                vm.toast("请填写血糖")
                return
            }
            if (!validGlucose(value)) {
                vm.toast("血糖请输入 0.5–40 mmol/L")
                return
            }
            vm.setLastGlucoseCtx(sceneKeys[sceneIndex])
            Measurement(
                id = UUID.randomUUID().toString(),
                type = "glucose",
                measuredAt = measuredAt,
                glucoseMmol = value,
                glucoseContext = sceneKeys[sceneIndex],
                note = note,
                createdBy = createdBy,
            )
        } else {
            val high = systolic.toIntOrNull()
            val low = diastolic.toIntOrNull()
            if (high == null || low == null) {
                vm.toast("请填写高压和低压")
                return
            }
            if (!validBloodPressure(high) || !validBloodPressure(low)) {
                vm.toast("高压和低压请输入 40–300 mmHg")
                return
            }
            val bpm = heartRate.toIntOrNull()
            if (bpm != null && !validHeartRate(bpm)) {
                vm.toast("心率请输入 20–250 次/分")
                return
            }
            Measurement(
                id = UUID.randomUUID().toString(),
                type = "bp",
                measuredAt = measuredAt,
                systolic = high,
                diastolic = low,
                heartRateBpm = bpm,
                note = note,
                createdBy = createdBy,
            )
        }

        vm.addMeasurement(measurement)
        vm.toast("已保存")
        nav.popBackStack()
    }

    AnimatedVisibility(
        visible = entered,
        enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 16 },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            PageTitle(title = title, onBack = { nav.popBackStack() }, form = true, compact = true)

            if (isGlucose) {
                GlucoseInputRow(
                    glucose = glucose,
                    onGlucoseChange = { glucose = it },
                    scenes = sceneKeys.map(Labels::sceneName),
                    sceneIndex = sceneIndex,
                    onSceneChange = { sceneIndex = it },
                )
            } else {
                BloodPressureInputRow(
                    systolic = systolic,
                    onSystolicChange = { systolic = it },
                    diastolic = diastolic,
                    onDiastolicChange = { diastolic = it },
                    heartRate = heartRate,
                    onHeartRateChange = { heartRate = it },
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "测量时间",
                fontSize = FhType.Label,
                fontWeight = FontWeight.SemiBold,
                color = FhColors.Text2,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                WheelColumn(
                    yearRange.map { "${it}年" },
                    year - yearRange.first,
                    { year = yearRange.first + it },
                    Modifier.weight(1.25f),
                    itemHeight = 30.dp,
                )
                WheelColumn(
                    (1..12).map { "%02d月".format(it) },
                    month - 1,
                    { month = it + 1 },
                    Modifier.weight(1f),
                    itemHeight = 30.dp,
                )
                WheelColumn(
                    (1..daysInMonth).map { "%02d日".format(it) },
                    safeDay - 1,
                    { day = it + 1 },
                    Modifier.weight(1f),
                    itemHeight = 30.dp,
                )
                WheelColumn(
                    (0..23).map { "%02d时".format(it) },
                    hour,
                    { hour = it },
                    Modifier.weight(1f),
                    itemHeight = 30.dp,
                )
                WheelColumn(
                    (0..59).map { "%02d分".format(it) },
                    minute,
                    { minute = it },
                    Modifier.weight(1f),
                    itemHeight = 30.dp,
                )
            }

            Spacer(Modifier.height(12.dp))
            CompactNoteField(note, onChange = { note = it })
            Spacer(Modifier.height(12.dp))
            FhButton(
                text = "保 存",
                onClick = ::save,
                topPadding = 0.dp,
                pressFeedback = true,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

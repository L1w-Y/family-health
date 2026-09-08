// 滚轮选择器：日期+时间合一弹窗（不用钟表轮盘；年/月/日/时/分滚动选择）
package com.family.health.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.health.ui.theme.FhColors
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate

private const val ITEM_H = 34
private const val VISIBLE = 3 // 上下各露一行，中间为选中

/** 单列滚轮：items 文本列，selected 下标，onSelect 回调 */
@Composable
private fun WheelColumn(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 1).coerceAtLeast(0))
    // 滚动停稳后取居中项为选中
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { (idx, inProgress) ->
                if (!inProgress) {
                    val center = (idx + 1).coerceIn(0, items.lastIndex)
                    if (center != selected) onSelect(center)
                }
            }
    }
    Box(modifier = modifier.height((ITEM_H * VISIBLE).dp)) {
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = ITEM_H.dp),
        ) {
            items(items.size) { i ->
                val on = i == selected
                Text(
                    items[i],
                    fontSize = if (on) 16.sp else 13.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) FhColors.Text else FhColors.Text2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ITEM_H.dp)
                        .padding(top = 6.dp),
                )
            }
        }
        // 选中行指示线
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(1.dp)
                .background(FhColors.Line),
        )
    }
}

/**
 * 日期时间滚轮弹窗。
 * needDate/needTime 控制列；onConfirm(date "yyyy-MM-dd"?, time "HH:mm"?)。
 */
@Composable
fun FhDateTimePickerDialog(
    initialDate: String,
    initialTime: String?,
    needDate: Boolean = true,
    needTime: Boolean = true,
    title: String = "选择时间",
    onConfirm: (String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val d = runCatching { LocalDate.parse(initialDate) }.getOrDefault(LocalDate.now())
    val t = initialTime ?: "08:00"
    var year by remember { mutableIntStateOf(d.year) }
    var month by remember { mutableIntStateOf(d.monthValue) }
    var day by remember { mutableIntStateOf(d.dayOfMonth) }
    var hour by remember { mutableIntStateOf(t.substringBefore(":").toIntOrNull() ?: 8) }
    var minute by remember { mutableIntStateOf(t.substringAfter(":").toIntOrNull() ?: 0) }

    val daysInMonth = runCatching { LocalDate.of(year, month, 1).lengthOfMonth() }.getOrDefault(31)
    if (day > daysInMonth) day = daysInMonth

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (needDate) {
                    WheelColumn((2024..2030).map { "${it}年" }, year - 2024, { year = it + 2024 },
                        Modifier.weight(1.3f))
                    WheelColumn((1..12).map { "${it}月" }, month - 1, { month = it + 1 },
                        Modifier.weight(1f))
                    WheelColumn((1..daysInMonth).map { "${it}日" }, day - 1, { day = it + 1 },
                        Modifier.weight(1f))
                }
                if (needTime) {
                    WheelColumn((0..23).map { "%02d时".format(it) }, hour, { hour = it },
                        Modifier.weight(1f))
                    WheelColumn((0..59).map { "%02d分".format(it) }, minute, { minute = it },
                        Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val date = if (needDate) "%04d-%02d-%02d".format(year, month, day) else null
                val time = if (needTime) "%02d:%02d".format(hour, minute) else null
                onConfirm(date, time)
            }) { Text("确定", color = FhColors.Primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = FhColors.Text2) }
        },
    )
}

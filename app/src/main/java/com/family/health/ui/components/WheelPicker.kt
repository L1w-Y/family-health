// 滚轮选择器：日期+时间合一弹窗（不用钟表轮盘；年/月/日/时/分滚动选择）
package com.family.health.ui.components

import com.family.health.ui.theme.FhType

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.family.health.ui.theme.FhColors
import com.family.health.ui.theme.FhShape
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

private val DEFAULT_ITEM_HEIGHT = 34.dp
private const val DEFAULT_VISIBLE_ITEMS = 3 // 上下各露一行，中间为选中

/** 视口正中那一项的索引（按实际布局计算，避免内容内边距造成的行偏移）；无布局时返回 -1 */
private fun centerIndexOf(state: LazyListState, size: Int): Int {
    val info = state.layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return -1
    val centerY = (info.viewportStartOffset + info.viewportEndOffset) / 2
    val item = info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - centerY) } ?: return -1
    return if (item.index in 0 until size) item.index else -1
}

/** 单列滚轮：items 文本列，selected 下标，onSelect 回调。可内嵌页面，也可用于弹窗。 */
@Composable
internal fun WheelColumn(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = DEFAULT_ITEM_HEIGHT,
    visibleItems: Int = DEFAULT_VISIBLE_ITEMS,
) {
    val state = rememberLazyListState()
    val latestSelected by rememberUpdatedState(selected)
    // 居中项随滚动即时高亮（仅在索引变化时重组，字号固定以避免逐帧重排）
    val centered by remember(items.size) {
        derivedStateOf { centerIndexOf(state, items.size).coerceIn(0, items.lastIndex) }
    }
    // 外部选中值变化（首次进入 / 切换月份后日期被裁剪）→ 滚动到目标
    LaunchedEffect(selected, items.size) {
        val target = selected.coerceIn(0, items.lastIndex)
        if (centerIndexOf(state, items.size) != target) state.scrollToItem(target)
    }
    // 手指停稳后提交居中项（不再等待整段惯性动画结束才算数）
    LaunchedEffect(state, items.size) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { inProgress ->
                if (!inProgress) {
                    val c = centerIndexOf(state, items.size)
                    if (c >= 0 && c != latestSelected) onSelect(c)
                }
            }
    }
    val safeVisibleItems = visibleItems.coerceAtLeast(3).let { if (it % 2 == 0) it + 1 else it }
    val edgePadding = itemHeight * ((safeVisibleItems - 1) / 2)
    Box(modifier = modifier.height(itemHeight * safeVisibleItems)) {
        // 选中区底纹：绘制在文字之下，避免横线压字（旧版居中横线像删除线）
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .clip(FhShape.Segment)
                .background(FhColors.PrimarySoft.copy(alpha = .65f)),
        )
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = edgePadding),
        ) {
            items(items.size) { i ->
                val on = i == centered
                val textColor by animateColorAsState(
                    targetValue = if (on) FhColors.Primary else FhColors.Tiny,
                    animationSpec = tween(180),
                    label = "wheelTextColor",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                ) {
                    Text(
                        items[i],
                        fontSize = FhType.Label,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
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

    val daysInMonth = runCatching { YearMonth.of(year, month).lengthOfMonth() }.getOrDefault(31)
    val safeDay = day.coerceIn(1, daysInMonth)
    // 切换月份后裁剪日（放到副作用里，不在组合期写状态，避免多余重组与抖动）
    LaunchedEffect(safeDay) { if (day != safeDay) day = safeDay }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = FhType.Body, fontWeight = FontWeight.Bold) },
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
                    WheelColumn((1..daysInMonth).map { "${it}日" }, safeDay - 1, { day = it + 1 },
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
                val date = if (needDate) "%04d-%02d-%02d".format(year, month, safeDay) else null
                val time = if (needTime) "%02d:%02d".format(hour, minute) else null
                onConfirm(date, time)
            }) { Text("确定", color = FhColors.Primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = FhColors.Text2) }
        },
    )
}

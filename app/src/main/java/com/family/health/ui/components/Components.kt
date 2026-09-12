// 契约：docs/07-视觉样式.md §2–4 共享组件视觉；docs/05-页面结构与交互.md
package com.family.health.ui.components

import com.family.health.ui.theme.FhType

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.health.ui.theme.FhColors
import com.family.health.ui.theme.FhShape
import com.family.health.ui.theme.FhSpace

val NumStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun FhCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = FhShape.Card
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = FhColors.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, FhColors.Line),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier),
    ) {
        Column(modifier = contentPadding, content = content)
    }
}

/** 卡片头：标题 + 右侧"更多"链接（.card-head） */
@Composable
fun CardHead(title: String, more: String? = null, onMore: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = FhType.Section, fontWeight = FontWeight.SemiBold, color = FhColors.Text)
        if (more != null) {
            Text(
                more, fontSize = FhType.Label, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
                modifier = if (onMore != null) Modifier.clickable(onClick = onMore) else Modifier,
            )
        }
    }
}

/** 分段切换（.seg） */
@Composable
fun SegControl(options: List<String>, selected: Int, compact: Boolean = false, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (compact) 6.dp else 12.dp)
            .clip(FhShape.Control)
            .background(FhColors.ChipGray)
            .padding(FhSpace.Tight),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) FhColors.Card else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = if (compact) 5.dp else 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = FhType.Label,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) FhColors.Primary else FhColors.Text2,
                )
            }
        }
    }
}

/** 胶囊 chip（.chip）。onClick 必须为最后一个参数：尾随 lambda 一律视为点击回调 */
@Composable
fun FChip(
    text: String,
    on: Boolean = false,
    trailing: (@Composable (() -> Unit))? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(FhShape.Tag)
            .background(if (on) FhColors.PrimarySoft else FhColors.ChipGray)
            .border(1.dp, if (on) FhColors.Primary else FhColors.ChipGray, FhShape.Tag)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 5.dp),
    ) {
        Text(
            text, fontSize = FhType.Label, fontWeight = FontWeight.SemiBold,
            color = if (on) FhColors.Primary else FhColors.Text2,
        )
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}

/** 小标签（.tag / .tag.gray） */
@Composable
fun FTag(text: String, gray: Boolean = false) {
    Text(
        text, fontSize = FhType.Caption, fontWeight = FontWeight.SemiBold,
        color = if (gray) FhColors.Text2 else FhColors.Primary,
        modifier = Modifier
            .clip(FhShape.Tag)
            .background(if (gray) FhColors.TagGray else FhColors.PrimarySoft)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/** 时段小标（.slotchip） */
@Composable
fun SlotChip(label: String) {
    Text(
        label, fontSize = FhType.Caption, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
        modifier = Modifier
            .clip(FhShape.Tag)
            .background(FhColors.PrimarySoft)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

@Composable
fun SlotTags(slots: List<String>) {
    slots.forEach { key ->
        Spacer(Modifier.width(4.dp))
        SlotChip(com.family.health.data.model.Labels.slotName(key))
    }
}

/** 列表行卡片（.row） */
@Composable
fun RowCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    FhCard(onClick = onClick, contentPadding = Modifier.padding(horizontal = 16.dp, vertical = 13.dp), content = content)
}

@Composable
fun RowLine1(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
        content = content,
    )
}

@Composable
fun RowLine2(text: String, color: Color = FhColors.Text2) {
    Text(text, fontSize = FhType.Label, color = color, lineHeight = 19.sp, modifier = Modifier.padding(top = 3.dp))
}

/** 空态（.empty） */
@Composable
fun EmptyHint(text: String) {
    Text(
        text, fontSize = FhType.Label, color = FhColors.Text2, lineHeight = 26.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** 提醒 chip（.remind-chip） */
@Composable
fun RemindChip(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(FhColors.AmberBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text("⏰ $text", fontSize = FhType.Caption, color = FhColors.Amber)
    }
}

/** 警示框（.warn-box） */
@Composable
fun WarnBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(FhColors.AmberBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}

/** 通过框（.ok-box） */
@Composable
fun OkBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(FhColors.PrimarySoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}

/** 步骤条（.steps） */
@Composable
fun StepsBar(total: Int, active: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < active) FhColors.Primary else FhColors.Line),
            )
        }
    }
}

/** 键值行（.kv） */
@Composable
fun KvRow(key: String, value: @Composable RowScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            key, fontSize = FhType.Label, color = FhColors.Text2, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(96.dp),
        )
        value()
    }
}

/** 「我的」列表（.mlist） */
@Composable
fun MListCard(items: List<Pair<String, String>>, onClick: (Int) -> Unit) {
    FhCard(contentPadding = Modifier) {
        items.forEachIndexed { i, (title, desc) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(i) }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = FhType.Body, color = FhColors.Text)
                    if (desc.isNotEmpty()) {
                        Text(desc, fontSize = FhType.Caption, color = FhColors.Text2, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Text("›", fontSize = FhType.Item, color = FhColors.Text2)
            }
            if (i < items.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(FhColors.Line))
            }
        }
    }
}

/** 头像圆（成员切换条/弹层共用） */
@Composable
fun Avatar(name: String, size: Int = 32, color: Color = FhColors.Primary) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(size.dp)
            .height(size.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(color),
    ) {
        Text(name.take(1), color = Color.White, fontSize = (size * 0.44).sp, fontWeight = FontWeight.SemiBold)
    }
}

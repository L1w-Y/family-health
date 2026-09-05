// 契约：prototype/styles.css 组件样式 1:1（card/seg/chip/tag/slotchip/row/warn-box/ok-box/steps/kv/mlist）
package com.family.health.ui.components

import androidx.compose.foundation.background
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

val NumStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun FhCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = FhColors.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
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
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
        if (more != null) {
            Text(
                more, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
                modifier = if (onMore != null) Modifier.clickable(onClick = onMore) else Modifier,
            )
        }
    }
}

/** 分段切换（.seg） */
@Composable
fun SegControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(FhColors.ChipGray)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) FhColors.Card else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (on) FhColors.Text else FhColors.Text2,
                )
            }
        }
    }
}

/** 胶囊 chip（.chip） */
@Composable
fun FChip(
    text: String,
    on: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable (() -> Unit))? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (on) FhColors.Primary else FhColors.ChipGray)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 5.dp),
    ) {
        Text(
            text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (on) Color.White else FhColors.Text2,
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
        text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        color = if (gray) FhColors.Text2 else FhColors.Primary,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (gray) FhColors.TagGray else FhColors.PrimarySoft)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/** 时段小标（.slotchip） */
@Composable
fun SlotChip(label: String) {
    Text(
        label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
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
    Text(text, fontSize = 13.sp, color = color, lineHeight = 19.sp, modifier = Modifier.padding(top = 3.dp))
}

/** 空态（.empty） */
@Composable
fun EmptyHint(text: String) {
    Text(
        text, fontSize = 13.sp, color = FhColors.Text2, lineHeight = 26.sp,
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
        Text("⏰ $text", fontSize = 12.sp, color = FhColors.Amber)
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
            key, fontSize = 14.sp, color = FhColors.Text2, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(96.dp),
        )
        value()
    }
}

/** 页面标题（.page-title） */
@Composable
fun PageTitle(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 12.dp),
    ) {
        if (onBack != null) {
            Text(
                "‹", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 8.dp),
            )
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FhColors.Text)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
fun PageSub(text: String) {
    Text(
        text, fontSize = 13.sp, color = FhColors.Text2,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 12.dp),
    )
}

/** 主按钮 / 幽灵按钮（.btn / .btn.ghost / .btn.small） */
@Composable
fun FhButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, ghost: Boolean = false) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (ghost) FhColors.TagGray else FhColors.Primary)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Text(
            text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            color = if (ghost) FhColors.Text else Color.White,
        )
    }
}

@Composable
fun FhSmallButton(text: String, ghost: Boolean = true, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (ghost) FhColors.TagGray else FhColors.Primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = if (ghost) FhColors.Text else Color.White,
        )
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
                    Text(title, fontSize = 15.sp, color = FhColors.Text)
                    if (desc.isNotEmpty()) {
                        Text(desc, fontSize = 12.sp, color = FhColors.Text2, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Text("›", fontSize = 18.sp, color = FhColors.Text2)
            }
            if (i < items.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F1EE)))
            }
        }
    }
}

/** 表单输入（.field label + input） */
@Composable
fun FhTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    multiline: Boolean = false,
    placeholder: String = "",
) {
    Column(modifier = modifier.padding(bottom = 14.dp)) {
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = !multiline,
            minLines = if (multiline) 4 else 1,
            shape = RoundedCornerShape(10.dp),
            placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder, color = FhColors.Tiny) }),
            keyboardOptions = if (number) {
                androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            } else androidx.compose.foundation.text.KeyboardOptions.Default,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FhColors.Primary,
                unfocusedBorderColor = FhColors.Line,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
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

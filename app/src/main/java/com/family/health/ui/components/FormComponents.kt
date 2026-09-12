// 契约：docs/07-视觉样式.md §2–4 共享组件视觉；docs/05-页面结构与交互.md
package com.family.health.ui.components

import com.family.health.ui.theme.FhType

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.family.health.ui.theme.FhColors
import com.family.health.ui.theme.FhShape
import com.family.health.ui.theme.FhSpace

/** 页面标题（.page-title） */
@Composable
fun PageTitle(
    title: String,
    onBack: (() -> Unit)? = null,
    form: Boolean = false,
    compact: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = if (compact) 8.dp else 12.dp),
    ) {
        if (onBack != null) {
            Text(
                "‹", fontSize = FhType.Page, fontWeight = FontWeight.SemiBold, color = FhColors.Primary,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 8.dp),
            )
        }
        Text(title, fontSize = if (form) FhType.Form else FhType.Page, fontWeight = FontWeight.Bold, color = FhColors.Text)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
fun PageSub(text: String) {
    Text(
        text, fontSize = FhType.Label, color = FhColors.Text2,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 12.dp),
    )
}

/** 主按钮 / 幽灵按钮（.btn / .btn.ghost / .btn.small） */
@Composable
fun FhButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
    topPadding: Dp = 6.dp,
    pressFeedback: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressFeedback && pressed) .985f else 1f,
        animationSpec = tween(160),
        label = "buttonPressScale",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(FhShape.Control)
            .background(if (ghost) FhColors.TagGray else FhColors.Primary)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(vertical = 14.dp),
    ) {
        Text(
            text, style = FhType.ButtonStyle,
            color = if (ghost) FhColors.Text else Color.White,
        )
    }
}

@Composable
fun FhSmallButton(text: String, ghost: Boolean = true, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(FhShape.Control)
            .background(if (ghost) FhColors.TagGray else FhColors.Primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text, fontSize = FhType.Label, fontWeight = FontWeight.SemiBold,
            color = if (ghost) FhColors.Text else Color.White,
        )
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
    decimal: Boolean = false,
    multiline: Boolean = false,
    enabled: Boolean = true,
    placeholder: String? = null,
) {
    Column(modifier = modifier.padding(bottom = FhSpace.Content)) {
        Text(
            label, fontSize = FhType.Label, fontWeight = FontWeight.SemiBold, color = FhColors.Text2,
            modifier = Modifier.padding(bottom = FhSpace.Related),
        )
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            placeholder = placeholder?.let { ph -> { Text(ph, fontSize = FhType.Label, color = FhColors.Text2) } },
            singleLine = !multiline,
            minLines = if (multiline) 4 else 1,
            shape = FhShape.Control,
            textStyle = FhType.BodyStyle,
            keyboardOptions = if (number || decimal) {
                androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (decimal) androidx.compose.ui.text.input.KeyboardType.Decimal
                    else androidx.compose.ui.text.input.KeyboardType.Number,
                )
            } else androidx.compose.foundation.text.KeyboardOptions.Default,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FhColors.Primary,
                unfocusedBorderColor = FhColors.Outline,
                focusedTextColor = FhColors.Text,
                unfocusedTextColor = FhColors.Text,
                disabledContainerColor = FhColors.DisabledContainer,
                disabledTextColor = FhColors.DisabledContent,
                errorBorderColor = FhColors.InputError,
                errorContainerColor = FhColors.InputErrorContainer,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


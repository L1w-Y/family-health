// 契约：docs/05-页面结构与交互.md §8；docs/07-视觉样式.md §2–4
package com.family.health.feature.entry

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.family.health.ui.components.WheelColumn
import com.family.health.ui.theme.FhColors
import com.family.health.ui.theme.FhType

private val CompactFieldShape = RoundedCornerShape(13.dp)

@Composable
internal fun BloodPressureInputRow(
    systolic: String,
    onSystolicChange: (String) -> Unit,
    diastolic: String,
    onDiastolicChange: (String) -> Unit,
    heartRate: String,
    onHeartRateChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryLabel("血压", Modifier.weight(1f))
        CompactNumberField(systolic, onSystolicChange, "高压", Modifier.weight(1f))
        CompactNumberField(diastolic, onDiastolicChange, "低压", Modifier.weight(1f))
        CompactNumberField(heartRate, onHeartRateChange, "心率", Modifier.weight(1f))
    }
}

@Composable
internal fun GlucoseInputRow(
    glucose: String,
    onGlucoseChange: (String) -> Unit,
    scenes: List<String>,
    sceneIndex: Int,
    onSceneChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryLabel("血糖", Modifier.weight(1f))
        CompactNumberField(
            value = glucose,
            onChange = onGlucoseChange,
            placeholder = "数值",
            modifier = Modifier.weight(1f),
            allowDecimal = true,
        )
        EntryLabel("场景", Modifier.weight(1f))
        WheelColumn(
            items = scenes,
            selected = sceneIndex,
            onSelect = onSceneChange,
            modifier = Modifier.weight(1f),
            itemHeight = 26.dp,
        )
    }
}

@Composable
private fun EntryLabel(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, style = FhType.BodyStyle, fontWeight = FontWeight.SemiBold, color = FhColors.Text2)
    }
}

@Composable
internal fun CompactNumberField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) FhColors.Primary else FhColors.Outline,
        animationSpec = tween(180),
        label = "measureFieldBorder",
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (focused) FhColors.PrimarySoft.copy(alpha = .42f) else FhColors.Card,
        animationSpec = tween(180),
        label = "measureFieldBackground",
    )
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.01f else 1f,
        animationSpec = tween(180),
        label = "measureFieldScale",
    )

    BasicTextField(
        value = value,
        onValueChange = { input ->
            onChange(if (allowDecimal) sanitizeDecimalInput(input) else input.filter(Char::isDigit).take(3))
        },
        singleLine = true,
        textStyle = FhType.BodyStyle.copy(textAlign = TextAlign.Center, color = FhColors.Text),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        cursorBrush = SolidColor(FhColors.Primary),
        modifier = modifier
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CompactFieldShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CompactFieldShape)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = placeholder },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = FhType.LabelStyle,
                        color = FhColors.Tiny,
                        textAlign = TextAlign.Center,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
internal fun CompactNoteField(value: String, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) FhColors.Primary else FhColors.Outline,
        animationSpec = tween(180),
        label = "noteBorder",
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (focused) FhColors.PrimarySoft.copy(alpha = .22f) else FhColors.Card,
        animationSpec = tween(180),
        label = "noteBackground",
    )

    Text(
        "备注",
        fontSize = FhType.Label,
        fontWeight = FontWeight.SemiBold,
        color = FhColors.Text2,
        modifier = Modifier.padding(bottom = 5.dp),
    )
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = FhType.BodyStyle.copy(color = FhColors.Text),
        cursorBrush = SolidColor(FhColors.Primary),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CompactFieldShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CompactFieldShape)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .semantics { contentDescription = "备注" },
    )
}

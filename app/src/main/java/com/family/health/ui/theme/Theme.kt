// 契约：docs/07-视觉样式.md §1–4 浅色主题与组件语义颜色
package com.family.health.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object FhColors {
    val Primary = Color(0xFF086D7C)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimarySoft = Color(0xFFE8F4F5)
    val Bg = Color(0xFFF4F7F8)
    val Card = Color(0xFFFFFFFF)
    val Text = Color(0xFF172B38)
    val Text2 = Color(0xFF667782)
    val Tiny = Color(0xFF71828D)
    val Outline = Color(0xFFDCE6EB)
    val Line = Color(0xFFE5ECEF)
    val SurfaceVariant = Color(0xFFEDF3F5)
    val DisabledContainer = Color(0xFFE9EFF2)
    val DisabledContent = Color(0xFF7B8D98)
    val InputError = Color(0xFF9F433D)
    val InputErrorContainer = Color(0xFFFBF0EF)
    // 兼容现有语义调用，已有提醒和时段标记使用克制的青灰色。
    val Amber = Text2
    val AmberBg = SurfaceVariant
    val ChipGray = SurfaceVariant
    val TagGray = SurfaceVariant
    val Tcm = Primary
    val TcmSoft = PrimarySoft
    val TcmText = Primary
    // 时段语义色（docs/07 §1）：四个服用时段各自可区分的主色与浅底
    val SlotMorning = Primary
    val SlotNoon = Color(0xFFC27A2E)
    val SlotEvening = Color(0xFF4F7CA6)
    val SlotBedtime = Color(0xFF7E6BB0)
    val SlotMorningSoft = PrimarySoft
    val SlotNoonSoft = Color(0xFFFAF0E0)
    val SlotEveningSoft = Color(0xFFE9F0F6)
    val SlotBedtimeSoft = Color(0xFFEFEBF7)
    val OpDark = Text
    val ChartBlue = Text2
    val ToastBg = Text.copy(alpha = .94f)
}
private val scheme = lightColorScheme(
    primary = FhColors.Primary,
    onPrimary = Color.White,
    primaryContainer = FhColors.PrimarySoft,
    onPrimaryContainer = FhColors.Primary,
    secondary = FhColors.Primary,
    onSecondary = Color.White,
    background = FhColors.Bg,
    onBackground = FhColors.Text,
    surface = FhColors.Card,
    onSurface = FhColors.Text,
    surfaceVariant = FhColors.ChipGray,
    onSurfaceVariant = FhColors.Text2,
    outline = FhColors.Outline,
    outlineVariant = FhColors.Line,
    error = FhColors.InputError, onError = FhColors.OnPrimary,
    errorContainer = FhColors.InputErrorContainer, onErrorContainer = FhColors.InputError,
    surfaceTint = FhColors.Card,
    surfaceContainer = FhColors.Card,
    surfaceContainerHigh = FhColors.Card,
    surfaceContainerHighest = FhColors.SurfaceVariant,
)

@Composable
fun FamilyHealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = FhTypography, content = content)
}

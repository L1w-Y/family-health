// 契约：prototype/styles.css 色板 1:1（主色 #0F766E）
package com.family.health.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object FhColors {
    val Primary = Color(0xFF0F766E)
    val PrimarySoft = Color(0xFFE0F2F0)
    val Bg = Color(0xFFF4F5F3)
    val Card = Color(0xFFFFFFFF)
    val Text = Color(0xFF1B1D1B)
    val Text2 = Color(0xFF6B726C)
    val Line = Color(0xFFE6E8E4)
    val Amber = Color(0xFFB45309)
    val AmberBg = Color(0xFFFEF3C7)
    val ChipGray = Color(0xFFE7EAE6)
    val TagGray = Color(0xFFEEF0ED)
    val Tcm = Color(0xFFD9A441)
    val TcmSoft = Color(0xFFF5EBD7)
    val TcmText = Color(0xFF8A6D1F)
    val OpDark = Color(0xFF333936)
    val Tiny = Color(0xFF9AA19A)
    val ChartBlue = Color(0xFF5B6DC8)
    val ToastBg = Color(0xE0141A16)
}

private val scheme = lightColorScheme(
    primary = FhColors.Primary,
    onPrimary = Color.White,
    primaryContainer = FhColors.PrimarySoft,
    onPrimaryContainer = FhColors.Primary,
    secondary = FhColors.Amber,
    onSecondary = Color.White,
    background = FhColors.Bg,
    onBackground = FhColors.Text,
    surface = FhColors.Card,
    onSurface = FhColors.Text,
    surfaceVariant = FhColors.ChipGray,
    onSurfaceVariant = FhColors.Text2,
    outline = FhColors.Line,
)

@Composable
fun FamilyHealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

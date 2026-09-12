// 契约：docs/07-视觉样式.md §2–4 全 App 字级、间距与圆角
package com.family.health.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object FhType {
    val Page = 23.sp
    val Form = 21.sp
    val Section = 17.sp
    val Item = 18.sp
    val Body = 16.sp
    val Label = 14.sp
    val Caption = 12.sp
    val Value = 20.sp
    val BodyStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = Body,
        lineHeight = 24.sp, fontFeatureSettings = "tnum")
    val LabelStyle = BodyStyle.copy(fontSize = Label, lineHeight = 20.sp)
    val CaptionStyle = BodyStyle.copy(fontSize = Caption, lineHeight = 18.sp)
    val ButtonStyle = BodyStyle.copy(fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
}

object FhSpace {
    val Tight = 4.dp
    val Related = 8.dp
    val Component = 12.dp
    val Content = 16.dp
    val Group = 24.dp
}

object FhShape {
    val Card = RoundedCornerShape(15.dp)
    val Control = RoundedCornerShape(11.dp)
    val Segment = RoundedCornerShape(8.dp)
    val Tag = RoundedCornerShape(6.dp)
}

val FhTypography = Typography(
    headlineMedium = FhType.BodyStyle.copy(fontSize = FhType.Page, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    headlineSmall = FhType.BodyStyle.copy(fontSize = FhType.Form, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = FhType.BodyStyle.copy(fontSize = FhType.Item, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = FhType.BodyStyle.copy(fontSize = FhType.Section, fontWeight = FontWeight.SemiBold),
    titleSmall = FhType.LabelStyle.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = FhType.BodyStyle, bodyMedium = FhType.BodyStyle, bodySmall = FhType.CaptionStyle,
    labelLarge = FhType.ButtonStyle, labelMedium = FhType.LabelStyle, labelSmall = FhType.CaptionStyle,
)

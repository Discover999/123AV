package com.av123.video.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 字号 token：页面里不再直接写 11/12/13.sp，统一从这里取值。
 * 需要行高/字重时再配合 MaterialTheme.typography 的对应样式使用。
 */
object AppTypeScale {
    val Caption: TextUnit = 11.sp
    val Label: TextUnit = 12.sp
    val Body: TextUnit = 13.sp
    val BodyLarge: TextUnit = 14.sp
    val Title: TextUnit = 16.sp
    val OverlayTitle: TextUnit = 17.sp
    val Headline: TextUnit = 18.sp
}

private val Default = Typography()

val AppTypography = Typography(
    headlineSmall = Default.headlineSmall.copy(
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = Default.titleLarge.copy(
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = Default.titleMedium.copy(
        fontWeight = FontWeight.SemiBold
    ),
    // 三级字号：标题 13 / 正文 14、12 / 辅助 12、11
    titleSmall = Default.titleSmall.copy(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium
    ),
    labelLarge = Default.labelLarge.copy(
        letterSpacing = 0.5.sp
    ),
    labelMedium = Default.labelMedium.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = Default.labelSmall.copy(
        fontSize = 11.sp,
        lineHeight = 14.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = Default.bodySmall.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

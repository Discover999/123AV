package com.av123.video.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
    labelLarge = Default.labelLarge.copy(
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )
)

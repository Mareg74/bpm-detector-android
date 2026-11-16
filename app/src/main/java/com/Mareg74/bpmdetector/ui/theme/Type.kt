package com.Mareg74.bpmdetector.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
)

val BpmIntegerStyle =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 96.sp,
        lineHeight = 104.sp,
        letterSpacing = (-1.5).sp,
    )

val BpmDecimalStyle =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = 0.sp,
    )
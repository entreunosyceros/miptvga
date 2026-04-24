package com.toigo.miptvga

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MiptvgaColorScheme = darkColorScheme(
    primary = Color(0xFF2F89D5),
    onPrimary = Color(0xFFF7F9FF),
    secondary = Color(0xFF9BA7BD),
    onSecondary = Color(0xFF0E1117),
    background = Color(0xFF080B10),
    onBackground = Color(0xFFF3F5F8),
    surface = Color(0xFF11161D),
    onSurface = Color(0xFFF3F5F8),
    surfaceVariant = Color(0xFF1A2230),
    onSurfaceVariant = Color(0xFFB5C0D4),
    outline = Color(0xFF314052)
)

private val MiptvgaShapes = Shapes(
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(2.dp)
)

private val MiptvgaTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium
    )
)

@Composable
internal fun MiptvgaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MiptvgaColorScheme,
        typography = MiptvgaTypography,
        shapes = MiptvgaShapes,
        content = content
    )
}


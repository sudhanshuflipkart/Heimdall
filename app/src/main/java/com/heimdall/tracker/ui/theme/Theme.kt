package com.heimdall.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val HeimdallDarkColorScheme = darkColorScheme(
    primary = HeimdallGold,
    onPrimary = HeimdallBlack,
    primaryContainer = HeimdallGoldDark,
    onPrimaryContainer = HeimdallWhite,
    secondary = HeimdallGoldLight,
    onSecondary = HeimdallBlack,
    background = HeimdallBlack,
    onBackground = HeimdallWhite,
    surface = HeimdallDarkGray,
    onSurface = HeimdallWhite,
    surfaceVariant = HeimdallMediumGray,
    onSurfaceVariant = HeimdallWhite,
    error = HeimdallRed,
    onError = HeimdallWhite
)

private val HeimdallTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    )
)

@Composable
fun HeimdallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HeimdallDarkColorScheme,
        typography = HeimdallTypography,
        content = content
    )
}

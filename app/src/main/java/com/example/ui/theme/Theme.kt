package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CarbonDark,
    secondary = RacingAmber,
    onSecondary = CarbonDark,
    tertiary = RacingRed,
    background = CarbonDark,
    surface = CarbonCard,
    onBackground = HighWhite,
    onSurface = HighWhite,
    surfaceVariant = TrackGrey,
    onSurfaceVariant = DimGrey
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

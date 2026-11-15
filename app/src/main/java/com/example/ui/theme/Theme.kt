package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DrawToSaveColorScheme = lightColorScheme(
    primary = HoneyGold,
    onPrimary = InkDark,
    primaryContainer = HoneySurface,
    onPrimaryContainer = TextPrimary,
    secondary = SkyBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = InkDark,
    tertiary = GrassGreen,
    onTertiary = Color.White,
    background = HoneyCream,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = HoneySurface,
    onSurfaceVariant = TextSecondary,
    error = DangerRed,
    onError = Color.White
)

@Composable
fun DrawToSaveTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DrawToSaveColorScheme,
        typography = Typography,
        content = content
    )
}

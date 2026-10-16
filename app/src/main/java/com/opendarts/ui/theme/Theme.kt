package com.opendarts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OpenDartsColors = darkColorScheme(
    primary = Color(0xFF63E6BE),
    onPrimary = Color(0xFF06251D),
    secondary = Color(0xFFFFC857),
    background = Color(0xFF101312),
    surface = Color(0xFF191E1C),
    surfaceVariant = Color(0xFF252C29),
    onBackground = Color(0xFFF1F5F3),
    onSurface = Color(0xFFF1F5F3),
)

@Composable
fun OpenDartsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OpenDartsColors, content = content)
}

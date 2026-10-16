package com.opendarts.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/** Colours used across the app. The game categories each own a colour that tints their whole flow. */
object Palette {
    val Night = Color(0xFF17191B)
    val Red = Color(0xFFC0312F)
    val Charcoal = Color(0xFF3D3D3D)
    val Amber = Color(0xFFEDA024)
    val Orange = Color(0xFFF08A24)
    val Teal = Color(0xFF1AA67E)
    val Brown = Color(0xFF4A2F2C)
    val Sand = Color(0xFFF1E6D2)
    val KeyLight = Color(0xFFF4E9D8)
    val KeyMid = Color(0xFFE6D7B8)
    val KeyDark = Color(0xFFE8C27E)
    val Slate = Color(0xFF858585)
    val Ink = Color(0xFF2C2C2C)
    val Link = Color(0xFF3B9BE8)
}

/** Tall, narrow, upright lettering: the system condensed face keeps the app compact without bundling a font. */
val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
val CondensedRegular = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

private val colours = darkColorScheme(
    primary = Palette.Teal,
    onPrimary = Color.White,
    secondary = Palette.Amber,
    background = Palette.Night,
    surface = Color(0xFF222527),
    surfaceVariant = Color(0xFF2D3134),
    onBackground = Color.White,
    onSurface = Color.White,
)

private fun TextStyle.condensed() = copy(fontFamily = CondensedRegular)

private val typography = Typography().let {
    it.copy(
        displayLarge = it.displayLarge.condensed(), displayMedium = it.displayMedium.condensed(), displaySmall = it.displaySmall.condensed(),
        headlineLarge = it.headlineLarge.condensed(), headlineMedium = it.headlineMedium.condensed(), headlineSmall = it.headlineSmall.condensed(),
        titleLarge = it.titleLarge.condensed(), titleMedium = it.titleMedium.condensed(), titleSmall = it.titleSmall.condensed(),
        bodyLarge = it.bodyLarge.condensed(), bodyMedium = it.bodyMedium.condensed(), bodySmall = it.bodySmall.condensed(),
        labelLarge = it.labelLarge.condensed(), labelMedium = it.labelMedium.condensed(), labelSmall = it.labelSmall.condensed(),
    )
}

@Composable
fun OpenDartsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colours, typography = typography, content = content)
}
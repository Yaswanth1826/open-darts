package com.opendarts.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.opendarts.R

/** The Open Darts palette: near-black surfaces with one lime accent. */
object Pal {
    val Bg = Color(0xFF090B0F)
    val Surface = Color(0xFF12161C)
    val Raised = Color(0xFF1B212A)
    val Line = Color(0xFF2A323D)
    val Text = Color(0xFFF2F5F9)
    val Muted = Color(0xFF8A96A6)
    val Lime = Color(0xFFC6F24E)
    val OnLime = Color(0xFF0C1400)
    val Violet = Color(0xFF8E7CFF)
    val Sky = Color(0xFF4CC9F0)
    val Amber = Color(0xFFFFC857)
    val Coral = Color(0xFFFF6B6B)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun grotesk(weight: Int) = Font(R.font.space_grotesk, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Space Grotesk (SIL Open Font License), bundled as one variable font: bold for numbers and headings, medium for the rest. */
val Display = FontFamily(grotesk(700))
val Body = FontFamily(grotesk(500))

private val colours = darkColorScheme(
    primary = Pal.Lime,
    onPrimary = Pal.OnLime,
    secondary = Pal.Amber,
    onSecondary = Pal.OnLime,
    tertiary = Pal.Sky,
    background = Pal.Bg,
    onBackground = Pal.Text,
    surface = Pal.Surface,
    onSurface = Pal.Text,
    surfaceVariant = Pal.Raised,
    onSurfaceVariant = Pal.Muted,
    surfaceContainerLowest = Pal.Bg,
    surfaceContainerLow = Pal.Surface,
    surfaceContainer = Pal.Surface,
    surfaceContainerHigh = Pal.Raised,
    surfaceContainerHighest = Pal.Raised,
    outline = Pal.Line,
    outlineVariant = Pal.Line,
    error = Pal.Coral,
)

private fun TextStyle.body() = copy(fontFamily = Body)
private fun TextStyle.display() = copy(fontFamily = Display)

private val typography = Typography().let {
    it.copy(
        displayLarge = it.displayLarge.display(), displayMedium = it.displayMedium.display(), displaySmall = it.displaySmall.display(),
        headlineLarge = it.headlineLarge.display(), headlineMedium = it.headlineMedium.display(), headlineSmall = it.headlineSmall.display(),
        titleLarge = it.titleLarge.display(), titleMedium = it.titleMedium.display(), titleSmall = it.titleSmall.display(),
        bodyLarge = it.bodyLarge.body(), bodyMedium = it.bodyMedium.body(), bodySmall = it.bodySmall.body(),
        labelLarge = it.labelLarge.body(), labelMedium = it.labelMedium.body(), labelSmall = it.labelSmall.body(),
    )
}

@Composable
fun OpenDartsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colours, typography = typography, content = content)
}

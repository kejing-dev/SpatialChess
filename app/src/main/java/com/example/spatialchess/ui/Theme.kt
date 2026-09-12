package com.example.spatialchess.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme
import java.io.File

/**
 * Colours taken from the Spatial Chess Figma ("01 主要界面" / "02 组件与规范").
 * Values that map to a PICO role override that role; the rest are named brand tokens.
 */
object ChessColors {
    val Blue = Color(0xFF3B62E4)        // design-style: fixed-figma-color primary action (Chess Button / Primary)
    val Ink = Color(0xFF1C2433)         // design-style: fixed-figma-color primary text on white cards
    val Muted = Color(0xFF5B6472)       // design-style: fixed-figma-color secondary text
    val Card = Color(0xF5FFFFFF)        // design-style: fixed-figma-color white panel (Frame fill EDF1F5 backdrop)
    val CardBorder = Color(0x1A1C2433)
    val ChipGreenBg = Color(0xFFE3F1E6) // design-style: fixed-figma-color "已锚定桌面" chip
    val ChipGreenFg = Color(0xFF2F7A4A)
    val WarnBg = Color(0xFFFFF1C9)      // design-style: fixed-figma-color "尚未保存" pill
    val WarnFg = Color(0xFF8A6100)
    val PillGlass = Color(0xB8FFFFFF)   // design-style: fixed-figma-color 72% white pill behind icon buttons (UI 1.1)
    val Pill = Color(0xFFEEF1F5)        // design-style: fixed-figma-color "已保存至本机" pill / secondary button
    val Danger = Color(0xFFC0392B)
}

/**
 * PICO Sans is shipped with PICO OS 6 (`/system/fonts/PICOSans.ttf`, variable weight). Chinese
 * glyphs fall back to the system's zh-Hans family, which is PICO Sans SC on PICO OS.
 */
val PicoSans: FontFamily by lazy {
    runCatching {
        val file = File("/system/fonts/PICOSans.ttf")
        require(file.exists()) { "PICO Sans not found" }
        FontFamily(
            Font(file, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
            Font(file, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
            Font(file, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
            Font(file, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
            Font(file, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        )
    }.getOrElse { FontFamily.Default }
}

private fun TextStyle.pico(): TextStyle = copy(fontFamily = PicoSans)

@Composable
fun ChessTheme(content: @Composable () -> Unit) {
    val system = systemColorScheme(LocalContext.current)
    val scheme = system.copy(
        fillPrimary = system.fillPrimary,
        fillSecondary = system.fillSecondary,
        fillTertiary = system.fillTertiary,
        fillLight = system.fillLight,
        labelPrimaryLight = system.labelPrimaryLight,
        labelPrimary = system.labelPrimary,
        labelSecondary = system.labelSecondary,
        labelTertiary = system.labelTertiary,
        labelQuaternary = system.labelQuaternary,
        lightenHover = system.lightenHover,
        lightenPressed = system.lightenPressed,
        error = ChessColors.Danger,       // design-style: fixed-figma-color Button/Danger text
        alert = system.alert,
        passable = ChessColors.ChipGreenFg, // design-style: fixed-figma-color anchored chip
        interaction = ChessColors.Blue,   // design-style: fixed-figma-color Chess Button / Primary
        dividerLine = system.dividerLine,
    )
    val base = PicoTheme.typography
    val typography = base.copy(
        bodyLarge = base.bodyLarge.pico(),
        bodyLargeMultiline = base.bodyLargeMultiline.pico(),
        bodyMedium = base.bodyMedium.pico(),
        bodyMediumMultiline = base.bodyMediumMultiline.pico(),
        bodySmall = base.bodySmall.pico(),
        bodyTiny = base.bodyTiny.pico(),
        displayLarge = base.displayLarge.pico(),
        displayMedium = base.displayMedium.pico(),
        displaySmall = base.displaySmall.pico(),
        headlineLarge = base.headlineLarge.pico(),
        headlineMedium = base.headlineMedium.pico(),
        headlineSmall = base.headlineSmall.pico(),
        labelLarge = base.labelLarge.pico(),
        labelMedium = base.labelMedium.pico(),
        labelSmall = base.labelSmall.pico(),
        titleLarge = base.titleLarge.pico(),
        titleMedium = base.titleMedium.pico(),
        titleSmall = base.titleSmall.pico(),
    )
    PicoTheme(colorScheme = scheme, typography = typography, content = content)
}

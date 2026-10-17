package com.ilanmarchand.tui_launcher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ilanmarchand.tui_launcher.R

val TerminalFont = FontFamily(
    Font(R.font.jetbrains_mono_nerd_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.jetbrains_mono_nerd_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.jetbrains_mono_nerd_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.jetbrains_mono_nerd_bold_italic, FontWeight.Bold, FontStyle.Italic)
)

// The terminal's text: smaller than Material's 16sp body, and without its extra letter spacing, so more
// columns fit on the phone, as in a terminal
val TerminalFontSize = 13.sp
val TerminalTextStyle = TextStyle(
    fontFamily = TerminalFont,
    fontSize = TerminalFontSize,
    lineHeight = 18.5.sp,
    letterSpacing = 0.sp
)

// Every Material text style uses the terminal font; the body text, used everywhere in the terminal, is
// TerminalTextStyle
val Typography = Typography().let { default ->
    default.copy(
        displayLarge = default.displayLarge.copy(fontFamily = TerminalFont),
        displayMedium = default.displayMedium.copy(fontFamily = TerminalFont),
        displaySmall = default.displaySmall.copy(fontFamily = TerminalFont),
        headlineLarge = default.headlineLarge.copy(fontFamily = TerminalFont),
        headlineMedium = default.headlineMedium.copy(fontFamily = TerminalFont),
        headlineSmall = default.headlineSmall.copy(fontFamily = TerminalFont),
        titleLarge = default.titleLarge.copy(fontFamily = TerminalFont),
        titleMedium = default.titleMedium.copy(fontFamily = TerminalFont),
        titleSmall = default.titleSmall.copy(fontFamily = TerminalFont),
        bodyLarge = TerminalTextStyle,
        bodyMedium = default.bodyMedium.copy(fontFamily = TerminalFont),
        bodySmall = default.bodySmall.copy(fontFamily = TerminalFont),
        labelLarge = default.labelLarge.copy(fontFamily = TerminalFont),
        labelMedium = default.labelMedium.copy(fontFamily = TerminalFont),
        labelSmall = default.labelSmall.copy(fontFamily = TerminalFont)
    )
}

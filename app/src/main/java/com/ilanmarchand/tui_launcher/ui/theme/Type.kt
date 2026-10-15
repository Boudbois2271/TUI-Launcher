package com.ilanmarchand.tui_launcher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.ilanmarchand.tui_launcher.R

val TerminalFont = FontFamily(
    Font(R.font.jetbrains_mono_nerd_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.jetbrains_mono_nerd_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.jetbrains_mono_nerd_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.jetbrains_mono_nerd_bold_italic, FontWeight.Bold, FontStyle.Italic)
)

// Every Material text style uses the terminal font
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
        bodyLarge = default.bodyLarge.copy(fontFamily = TerminalFont),
        bodyMedium = default.bodyMedium.copy(fontFamily = TerminalFont),
        bodySmall = default.bodySmall.copy(fontFamily = TerminalFont),
        labelLarge = default.labelLarge.copy(fontFamily = TerminalFont),
        labelMedium = default.labelMedium.copy(fontFamily = TerminalFont),
        labelSmall = default.labelSmall.copy(fontFamily = TerminalFont)
    )
}

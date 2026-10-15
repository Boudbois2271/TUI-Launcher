package com.ilanmarchand.tui_launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Dark only: the launcher is a black terminal, so there is no light scheme and no dynamic color
private val TerminalColorScheme = darkColorScheme(
    background = TerminalBackground,
    onBackground = TerminalForeground,
    surface = TerminalBackground,
    onSurface = TerminalForeground,
    primary = Accent1,
    secondary = Accent2
)

@Composable
fun TUILauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = Typography,
        content = content
    )
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ilanmarchand.tui_launcher.admin.keepAsHomeApp
import com.ilanmarchand.tui_launcher.terminal.TerminalScreen
import com.ilanmarchand.tui_launcher.ui.theme.TUILauncherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepAsHomeApp(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK)
        )
        setContent {
            TUILauncherTheme {
                TerminalScreen()
            }
        }
    }
}

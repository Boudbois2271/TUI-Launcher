// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.files.CurrentWorkspace

// bash's pwd: prints the working directory
object PwdCommand : BuiltinCommand {
    override val name = "pwd"
    override val synopsis = "pwd"
    override val summary = "Print the name of the current working directory."
    override val takesArgs = true

    override fun run(context: Context, args: String) = textLines(listOf(CurrentWorkspace.get(context).cwd.path))
}

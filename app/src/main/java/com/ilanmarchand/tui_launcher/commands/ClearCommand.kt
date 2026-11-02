// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context

object ClearCommand : BuiltinCommand {
    override val name = "clear"
    override val synopsis = "clear"
    override val summary = "Clear the terminal screen."

    override fun run(context: Context, args: String) = CommandResult.Clear
}

package com.ilanmarchand.tui_launcher.commands

import android.content.Context

object ClearCommand : BuiltinCommand {
    override val name = "clear"

    override fun run(context: Context, args: String) = CommandResult.Clear
}

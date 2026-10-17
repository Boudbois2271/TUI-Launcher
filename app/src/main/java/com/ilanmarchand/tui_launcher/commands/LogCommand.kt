package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.log.readLastCommandLogLines

// Temporary, for development: shows the last entries of the command log. Expected to change later.
object LogCommand : BuiltinCommand {
    override val name = "log"

    private const val LINES = 15

    override fun run(context: Context, args: String): CommandResult =
        runCatching { readLastCommandLogLines(context, LINES) }
            .fold(
                onSuccess = { lines -> CommandResult.Output(lines.map { OutputLine(it) }) },
                onFailure = { CommandResult.Output(listOf(OutputLine("could not read the log", isError = true))) }
            )
}

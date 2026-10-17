package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.system.USER_NAME

// GNU whoami: prints the user name, the one in the prompt
object WhoamiCommand : BuiltinCommand {
    override val name = "whoami"
    override val synopsis = "whoami"
    override val summary = "Print the user name."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val word = splitShellWords(args)?.firstOrNull() ?: return textLines(listOf(USER_NAME))
        return if (word.startsWith("-") && word.length > 1) {
            errorLines("whoami: invalid option -- '${word[1]}'", tryHelp("whoami"))
        } else {
            errorLines("whoami: extra operand ‘$word’", tryHelp("whoami"))
        }
    }
}

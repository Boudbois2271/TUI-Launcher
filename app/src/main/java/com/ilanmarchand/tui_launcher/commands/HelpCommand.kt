package com.ilanmarchand.tui_launcher.commands

import android.content.Context

// bash's help: alone, it lists the built-in commands. `help NAME...` describes the commands whose name starts
// with NAME, the way bash matches help topics.
object HelpCommand : BuiltinCommand {
    override val name = "help"
    override val synopsis = "help [pattern ...]"
    override val summary = "Display information about builtin commands."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("help: unexpected EOF while looking for matching quote")
        return help(words, builtinCommands)
    }
}

internal fun help(patterns: List<String>, commands: List<BuiltinCommand>): CommandResult {
    val sorted = commands.sortedBy { it.name }
    if (patterns.isEmpty()) {
        val header = listOf(
            "TUI-Launcher shell. These commands are built in.  Type `help' to see this list.",
            "Type `help name' to find out more about the command `name'.",
            "Anything else is an alias or an app: see `compgen -a' and `compgen -c'.",
            ""
        )
        return textLines(header + sorted.map { " ${it.synopsis}" })
    }
    val lines = mutableListOf<OutputLine>()
    for (pattern in patterns) {
        val matches = sorted.filter { it.name.startsWith(pattern) }
        if (matches.isEmpty()) {
            lines += OutputLine("help: no help topics match `$pattern'.  Try `help help'.", isError = true)
        }
        for (command in matches) {
            lines += OutputLine("${command.name}: ${command.synopsis}")
            lines += OutputLine("    ${command.summary}")
        }
    }
    return CommandResult.Output(lines)
}

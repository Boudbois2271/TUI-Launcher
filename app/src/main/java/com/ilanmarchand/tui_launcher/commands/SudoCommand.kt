package com.ilanmarchand.tui_launcher.commands

import android.content.Context

// sudo: runs a built-in command as root, without a password on this phone. As root, pacman can remove and
// install. Like sudo, it doesn't expand aliases, and apps aren't programs it can run.
object SudoCommand : BuiltinCommand {
    override val name = "sudo"
    override val synopsis = "sudo COMMAND [ARG ...]"
    override val summary = "Execute a command as root."
    override val takesArgs = true

    private val USAGE = listOf(
        "usage: sudo -h | -K | -k | -V",
        "usage: sudo -v [-ABkNnS] [-g group] [-h host] [-p prompt] [-u user]",
        "usage: sudo -l [-ABkNnS] [-g group] [-h host] [-p prompt] [-U user]",
        "            [-u user] [command [arg ...]]",
        "usage: sudo [-ABbEHkNnPS] [-C num] [-D directory]",
        "            [-g group] [-h host] [-p prompt] [-R directory] [-T timeout]",
        "            [-u user] [VAR=value] [-i | -s] [command [arg ...]]"
    )

    override fun run(context: Context, args: String) = run(context, args, null)

    override fun run(context: Context, args: String, stdin: List<String>?): CommandResult {
        if (args.isBlank()) return errorLines(*USAGE.toTypedArray())
        if (args.trim() == "-V") return textLines(listOf("Sudo version 1.9.17p2"))
        val call = findBuiltinCommand(args.trim())
            ?: return errorLines("sudo: ${args.trim().substringBefore(' ')}: command not found")
        val command = call.command
        return if (command is RootCommand) command.runAsRoot(context, call.args) else command.run(context, call.args, stdin)
    }
}

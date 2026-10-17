package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.aliases.AliasStore
import com.ilanmarchand.tui_launcher.apps.LaunchableApp
import com.ilanmarchand.tui_launcher.apps.findByName
import com.ilanmarchand.tui_launcher.apps.loadLaunchableApps

// bash's type: type [-t] name...  Says what each name runs: an alias, a built-in command, or an app (where
// bash would show a program's path). -t prints only alias, builtin or file, like bash.
object TypeCommand : BuiltinCommand {
    override val name = "type"
    override val synopsis = "type [-t] name [name ...]"
    override val summary = "Display information about command type."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("type: unexpected EOF while looking for matching quote")
        return typeOf(words, AliasStore(context).all(), builtinCommands, loadLaunchableApps(context))
    }
}

internal fun typeOf(
    words: List<String>,
    aliases: Map<String, String>,
    commands: List<BuiltinCommand>,
    apps: List<LaunchableApp>
): CommandResult {
    var terse = false
    val names = mutableListOf<String>()
    for (word in words) {
        when {
            word == "-t" && names.isEmpty() -> terse = true
            word.startsWith("-") && word.length > 1 && names.isEmpty() -> return errorLines(
                "type: $word: invalid option",
                "type: usage: type [-t] name [name ...]",
                exitCode = 2
            )
            else -> names += word
        }
    }
    val lines = mutableListOf<OutputLine>()
    var found = true
    for (name in names) {
        val alias = aliases[name]
        val command = commands.firstOrNull { it.name.equals(name, ignoreCase = true) }
        val app = apps.findByName(name)
        when {
            alias != null -> lines += OutputLine(if (terse) "alias" else "$name is aliased to `$alias'")
            // contacts: the command with arguments, the app alone
            command != null && command.needsArgs && app != null -> if (terse) {
                lines += OutputLine("builtin")
            } else {
                lines += OutputLine("$name is a shell builtin when given arguments")
                lines += OutputLine("$name alone is the app ${app.label} (${app.packageName})")
            }
            command != null -> lines += OutputLine(if (terse) "builtin" else "$name is a shell builtin")
            app != null -> lines += OutputLine(if (terse) "file" else "$name is the app ${app.label} (${app.packageName})")
            else -> {
                found = false
                if (!terse) lines += OutputLine("type: $name: not found", isError = true)
            }
        }
    }
    return CommandResult.Output(lines, if (found) 0 else 1)
}

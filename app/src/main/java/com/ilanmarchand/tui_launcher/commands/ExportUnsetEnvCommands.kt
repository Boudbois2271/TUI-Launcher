// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.files.CurrentWorkspace
import com.ilanmarchand.tui_launcher.files.Workspace
import com.ilanmarchand.tui_launcher.shell.ShellVariables
import com.ilanmarchand.tui_launcher.shell.Variables
import com.ilanmarchand.tui_launcher.shell.isVariableName
import com.ilanmarchand.tui_launcher.system.HOST_NAME
import com.ilanmarchand.tui_launcher.system.USER_NAME

// The variables the shell sets by itself, all exported, as bash and the system do: HOME, PWD, OLDPWD, USER
// and HOSTNAME
fun shellBuiltinVariables(workspace: Workspace): Map<String, String> = buildMap {
    put("HOME", workspace.home.path)
    put("HOSTNAME", HOST_NAME)
    workspace.previous?.let { put("OLDPWD", it.path) }
    put("PWD", workspace.cwd.path)
    put("USER", USER_NAME)
}

// A variable's value: the shell's own ones first, then those set with NAME=value; ? is the last exit status
fun variableValue(name: String, variables: Variables, builtins: Map<String, String>): String? =
    if (name == "?") variables.lastStatus.toString() else builtins[name] ?: variables.get(name)

// bash's export: export [-p] [NAME[=value] ...]  Marks variables for the environment (see env). Alone or with
// -p, lists them as bash does.
object ExportCommand : BuiltinCommand {
    override val name = "export"
    override val synopsis = "export [-p] [NAME[=value] ...]"
    override val summary = "Set export attribute for shell variables."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("export: unexpected EOF while looking for matching quote")
        return export(words, ShellVariables.variables, shellBuiltinVariables(CurrentWorkspace.get(context)))
    }
}

// bash's unset: unset NAME...
object UnsetCommand : BuiltinCommand {
    override val name = "unset"
    override val synopsis = "unset [NAME ...]"
    override val summary = "Unset values and attributes of shell variables."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("unset: unexpected EOF while looking for matching quote")
        return unset(words, ShellVariables.variables)
    }
}

// env: prints the environment, NAME=value per line
object EnvCommand : BuiltinCommand {
    override val name = "env"
    override val synopsis = "env"
    override val summary = "Print the environment."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("env: unexpected EOF while looking for matching quote")
        if (words.isNotEmpty()) return errorLines("env: running a command with a changed environment is not available on this phone")
        return env(ShellVariables.variables, shellBuiltinVariables(CurrentWorkspace.get(context)))
    }
}

internal fun export(words: List<String>, variables: Variables, builtins: Map<String, String>): CommandResult {
    val names = words.filter { it != "-p" }
    if (names.isEmpty()) {
        val all = builtins + variables.exportedVariables()
        return textLines(all.toSortedMap().map { (name, value) -> if (value == null) "declare -x $name" else "declare -x $name=\"$value\"" })
    }
    val errors = mutableListOf<OutputLine>()
    for (word in names) {
        val name = word.substringBefore('=')
        if (!isVariableName(name)) {
            errors += OutputLine("export: `$word': not a valid identifier", isError = true)
            continue
        }
        if ('=' in word) variables.set(name, word.substringAfter('='))
        variables.export(name)
    }
    return CommandResult.Output(errors)
}

internal fun unset(words: List<String>, variables: Variables): CommandResult {
    val errors = mutableListOf<OutputLine>()
    for (name in words) {
        if (isVariableName(name)) variables.unset(name) else errors += OutputLine("unset: `$name': not a valid identifier", isError = true)
    }
    return CommandResult.Output(errors)
}

internal fun env(variables: Variables, builtins: Map<String, String>): CommandResult {
    val exported = variables.exportedVariables().filterValues { it != null }.mapValues { it.value!! }
    return textLines((builtins + exported).toSortedMap().map { (name, value) -> "$name=$value" })
}

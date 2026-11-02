// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.files.CurrentWorkspace
import com.ilanmarchand.tui_launcher.files.Workspace

// bash's cd: cd [dir]  No dir goes home (~, the shared storage), cd - goes back and prints where.
object CdCommand : BuiltinCommand {
    override val name = "cd"
    override val synopsis = "cd [dir]"
    override val summary = "Change the shell working directory."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("cd: unexpected EOF while looking for matching quote")
        return cd(words, CurrentWorkspace.get(context))
    }
}

internal fun cd(words: List<String>, workspace: Workspace): CommandResult {
    if (words.size > 1) return errorLines("cd: too many arguments")
    val target = words.firstOrNull()
    val dir = when (target) {
        null -> workspace.home
        "-" -> workspace.previous ?: return errorLines("cd: OLDPWD not set")
        else -> workspace.resolve(target)
    }
    if (!dir.exists()) return errorLines("cd: $target: No such file or directory")
    if (!dir.isDirectory) return errorLines("cd: $target: Not a directory")
    workspace.previous = workspace.cwd
    workspace.cwd = workspace.resolve(dir.path)
    return if (target == "-") textLines(listOf(workspace.cwd.path)) else CommandResult.Output(emptyList())
}

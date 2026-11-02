// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.files.CurrentWorkspace
import com.ilanmarchand.tui_launcher.files.Workspace

// GNU mkdir: mkdir [-p] DIRECTORY...  -p also makes the missing parents, and is fine with existing folders.
object MkdirCommand : BuiltinCommand {
    override val name = "mkdir"
    override val synopsis = "mkdir [-p] DIRECTORY ..."
    override val summary = "Make directories."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("mkdir: unexpected EOF while looking for matching quote")
        return mkdir(words, CurrentWorkspace.get(context))
    }
}

internal fun mkdir(words: List<String>, workspace: Workspace): CommandResult {
    val args = when (val result = parseGnuArgs("mkdir", words, "p", long = mapOf("parents" to 'p'))) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("mkdir"))
        is GnuArgsResult.Parsed -> result.args
    }
    if (args.operands.isEmpty()) return errorLines("mkdir: missing operand", tryHelp("mkdir"))
    val parents = 'p' in args.flags
    val errors = mutableListOf<OutputLine>()
    fun fail(operand: String, reason: String) {
        errors += OutputLine("mkdir: cannot create directory ‘$operand’: $reason", isError = true)
    }
    for (operand in args.operands) {
        val dir = workspace.resolve(operand)
        when {
            dir.exists() -> if (!(parents && dir.isDirectory)) fail(operand, "File exists")
            parents -> if (!dir.mkdirs()) fail(operand, "Permission denied")
            dir.parentFile?.isDirectory != true -> fail(operand, "No such file or directory")
            !dir.mkdir() -> fail(operand, "Permission denied")
        }
    }
    return CommandResult.Output(errors)
}

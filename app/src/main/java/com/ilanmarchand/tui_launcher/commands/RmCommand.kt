// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.files.CurrentWorkspace
import com.ilanmarchand.tui_launcher.files.Workspace

// GNU rm: rm [-rfd] FILE...  -r removes folders and what they hold, -f ignores missing files. The command log
// can't be removed ("Operation not permitted", like a file made immutable with chattr +i), and like GNU rm,
// -r refuses /.
object RmCommand : BuiltinCommand {
    override val name = "rm"
    override val synopsis = "rm [-rfd] FILE ..."
    override val summary = "Remove files or directories."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("rm: unexpected EOF while looking for matching quote")
        return rm(words, CurrentWorkspace.get(context))
    }
}

private val RM_LONG = mapOf("recursive" to 'r', "force" to 'f', "dir" to 'd')

internal fun rm(words: List<String>, workspace: Workspace): CommandResult {
    val args = when (val result = parseGnuArgs("rm", words, "rRfd", long = RM_LONG)) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("rm"))
        is GnuArgsResult.Parsed -> result.args
    }
    val recursive = 'r' in args.flags || 'R' in args.flags
    val force = 'f' in args.flags
    if (args.operands.isEmpty()) {
        return if (force) CommandResult.Output(emptyList()) else errorLines("rm: missing operand", tryHelp("rm"))
    }
    val errors = mutableListOf<OutputLine>()
    fun fail(shown: String, reason: String) {
        errors += OutputLine("rm: cannot remove '$shown': $reason", isError = true)
    }
    for (operand in args.operands) {
        val file = workspace.resolve(operand)
        when {
            recursive && file.path == "/" -> {
                errors += OutputLine("rm: it is dangerous to operate recursively on '/'", isError = true)
                errors += OutputLine("rm: use --no-preserve-root to override this failsafe", isError = true)
            }
            !file.exists() -> if (!force) fail(operand, "No such file or directory")
            !file.isDirectory -> when {
                workspace.isProtected(file) -> fail(operand, "Operation not permitted")
                !file.delete() -> fail(operand, "Permission denied")
            }
            !recursive -> when {
                'd' in args.flags && file.list()?.isEmpty() == true -> if (!file.delete()) fail(operand, "Permission denied")
                'd' in args.flags -> fail(operand, "Directory not empty")
                else -> fail(operand, "Is a directory")
            }
            // Children first; a folder that still holds something (such as the log) stays
            else -> for (inner in file.walkBottomUp()) {
                val shown = if (inner == file) operand else "$operand/${inner.relativeTo(file).path}"
                when {
                    workspace.isProtected(inner) -> fail(shown, "Operation not permitted")
                    inner.delete() -> Unit
                    inner.isDirectory && !inner.list().isNullOrEmpty() -> fail(shown, "Directory not empty")
                    else -> fail(shown, "Permission denied")
                }
            }
        }
    }
    return CommandResult.Output(errors)
}

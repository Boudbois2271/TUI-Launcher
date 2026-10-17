package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.files.CurrentWorkspace
import com.ilanmarchand.tui_launcher.files.Workspace

// GNU touch: touch [-c] FILE...  Makes empty files, or brings an existing file's time to now. -c makes none.
object TouchCommand : BuiltinCommand {
    override val name = "touch"
    override val synopsis = "touch [-c] FILE ..."
    override val summary = "Change file timestamps, or make empty files."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("touch: unexpected EOF while looking for matching quote")
        return touch(words, CurrentWorkspace.get(context), System.currentTimeMillis())
    }
}

internal fun touch(words: List<String>, workspace: Workspace, now: Long): CommandResult {
    val args = when (val result = parseGnuArgs("touch", words, "c", long = mapOf("no-create" to 'c'))) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("touch"))
        is GnuArgsResult.Parsed -> result.args
    }
    if (args.operands.isEmpty()) return errorLines("touch: missing file operand", tryHelp("touch"))
    val errors = mutableListOf<OutputLine>()
    for (operand in args.operands) {
        val file = workspace.resolve(operand)
        val reason = when {
            file.exists() -> if (file.setLastModified(now)) null else "Permission denied"
            'c' in args.flags -> null
            file.parentFile?.isDirectory != true -> "No such file or directory"
            runCatching { file.createNewFile() }.getOrDefault(false) -> null
            else -> "Permission denied"
        }
        reason?.let { errors += OutputLine("touch: cannot touch '$operand': $it", isError = true) }
    }
    return CommandResult.Output(errors)
}

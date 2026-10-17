package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.files.CurrentWorkspace
import com.ilanmarchand.tui_launcher.files.Workspace

// GNU cat: cat [-n] [FILE...]  Prints the files one after the other; with no file, or -, the piped-in text.
// -n numbers the lines.
object CatCommand : BuiltinCommand {
    override val name = "cat"
    override val synopsis = "cat [-n] [FILE ...]"
    override val summary = "Concatenate files and print on the standard output."
    override val takesArgs = true

    override fun run(context: Context, args: String) = run(context, args, null)

    override fun run(context: Context, args: String, stdin: List<String>?): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("cat: unexpected EOF while looking for matching quote")
        return cat(words, stdin.orEmpty(), CurrentWorkspace.get(context))
    }
}

internal fun cat(words: List<String>, input: List<String>, workspace: Workspace): CommandResult {
    val args = when (val result = parseGnuArgs("cat", words, "n", long = mapOf("number" to 'n'))) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("cat"))
        is GnuArgsResult.Parsed -> result.args
    }
    val lines = mutableListOf<OutputLine>()
    var number = 0
    for (operand in args.operands.ifEmpty { listOf("-") }) {
        val text = if (operand == "-") {
            input
        } else {
            val file = workspace.resolve(operand)
            when {
                !file.exists() -> null.also { lines += OutputLine("cat: $operand: No such file or directory", isError = true) }
                file.isDirectory -> null.also { lines += OutputLine("cat: $operand: Is a directory", isError = true) }
                else -> runCatching { file.readLines() }.getOrElse {
                    lines += OutputLine("cat: $operand: Permission denied", isError = true)
                    null
                }
            }
        } ?: continue
        for (line in text) {
            number++
            lines += OutputLine(if ('n' in args.flags) "${number.toString().padStart(6)}\t$line" else line)
        }
    }
    return CommandResult.Output(lines)
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

// GNU tac: tac [FILE...]  The lines in reverse order.
object TacCommand : TextTool("tac") {
    override val synopsis = "tac [FILE ...]"
    override val summary = "Concatenate and print files in reverse."

    override fun process(words: List<String>, input: List<String>, files: FileLines) = tac(words, input, files)
}

// util-linux rev: rev [FILE...]  Each line's characters in reverse order.
object RevCommand : TextTool("rev") {
    override val synopsis = "rev [FILE ...]"
    override val summary = "Reverse lines characterwise."

    override fun process(words: List<String>, input: List<String>, files: FileLines) = rev(words, input, files)
}

// GNU nl: nl [-b a|t] [FILE...]  Numbers the lines: by default only those with text (-b t); -b a numbers all.
object NlCommand : TextTool("nl") {
    override val synopsis = "nl [-b a|t] [FILE ...]"
    override val summary = "Number lines of files."

    override fun process(words: List<String>, input: List<String>, files: FileLines) = nl(words, input, files)
}

internal fun tac(words: List<String>, input: List<String>, files: FileLines = NoFiles): CommandResult {
    val out = mutableListOf<OutputLine>()
    forEachInput(words, input, files, onError = { name, reason ->
        out += OutputLine("tac: failed to open '$name' for reading: $reason", isError = true)
    }) { source -> out += source.lines.reversed().map { OutputLine(it) } }
    return CommandResult.Output(out)
}

internal fun rev(words: List<String>, input: List<String>, files: FileLines = NoFiles): CommandResult {
    val out = mutableListOf<OutputLine>()
    forEachInput(words, input, files, onError = { name, reason ->
        out += OutputLine("rev: cannot open $name: $reason", isError = true)
    }) { source -> out += source.lines.map { OutputLine(it.reversed()) } }
    return CommandResult.Output(out)
}

internal fun nl(words: List<String>, input: List<String>, files: FileLines = NoFiles): CommandResult {
    val args = when (val result = parseGnuArgs("nl", words, "", "b", mapOf("body-numbering" to 'b'))) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("nl"))
        is GnuArgsResult.Parsed -> result.args
    }
    val style = args.values['b']?.last() ?: "t"
    if (style != "a" && style != "t") return errorLines("nl: invalid body numbering style: ‘$style’", tryHelp("nl"))
    val out = mutableListOf<OutputLine>()
    var number = 0
    forEachInput(args.operands, input, files, onError = { name, reason ->
        out += OutputLine("nl: $name: $reason", isError = true)
    }) { source ->
        for (line in source.lines) {
            out += OutputLine(
                if (style == "a" || line.isNotEmpty()) "${(++number).toString().padStart(6)}\t$line" else "       "
            )
        }
    }
    return CommandResult.Output(out)
}

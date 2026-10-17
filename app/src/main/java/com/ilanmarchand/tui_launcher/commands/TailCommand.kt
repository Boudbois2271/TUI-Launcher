package com.ilanmarchand.tui_launcher.commands

// GNU tail: tail [-n [+]NUM] [-NUM] [FILE...]  The last 10 lines by default; -n +NUM starts at line NUM.
// Several files each get a "==> name <==" header, as in tail.
object TailCommand : TextTool("tail") {
    override val synopsis = "tail [-n [+]NUM] [FILE ...]"
    override val summary = "Print the last 10 lines of each file."

    override fun process(words: List<String>, input: List<String>, files: FileLines) = tail(words, input, files)
}

internal fun tail(words: List<String>, input: List<String>, files: FileLines = NoFiles): CommandResult {
    val (text, operands) = when (val result = parseLineCount("tail", words)) {
        is LineCountResult.Failed -> return result.output
        is LineCountResult.Count -> (result.text ?: "10") to result.operands
    }
    val count = text.removePrefix("+").removePrefix("-").toIntOrNull()
        ?: return errorLines("tail: invalid number of lines: ‘$text’")
    return eachWithHeaders("tail", operands, input, files) { lines ->
        if (text.startsWith("+")) lines.drop(maxOf(count - 1, 0)) else lines.takeLast(count)
    }
}

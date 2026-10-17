package com.ilanmarchand.tui_launcher.commands

// GNU tail: tail [-n [+]NUM] [-NUM]  The last 10 lines by default; -n +NUM starts at line NUM.
object TailCommand : TextTool("tail") {
    override fun process(words: List<String>, input: List<String>) = tail(words, input)
}

internal fun tail(words: List<String>, input: List<String>): CommandResult {
    val text = when (val result = parseLineCount("tail", words)) {
        is LineCountResult.Failed -> return result.output
        is LineCountResult.Count -> result.text ?: "10"
    }
    val count = text.removePrefix("+").removePrefix("-").toIntOrNull()
        ?: return errorLines("tail: invalid number of lines: ‘$text’")
    return textLines(if (text.startsWith("+")) input.drop(maxOf(count - 1, 0)) else input.takeLast(count))
}

package com.ilanmarchand.tui_launcher.commands

// GNU head: head [-n [-]NUM] [-NUM]  The first 10 lines by default; -n -NUM keeps all but the last NUM.
object HeadCommand : TextTool("head") {
    override val synopsis = "head [-n [-]NUM]"
    override val summary = "Print the first 10 lines of the input."

    override fun process(words: List<String>, input: List<String>) = head(words, input)
}

internal fun head(words: List<String>, input: List<String>): CommandResult {
    val text = when (val result = parseLineCount("head", words)) {
        is LineCountResult.Failed -> return result.output
        is LineCountResult.Count -> result.text ?: "10"
    }
    val count = text.toIntOrNull() ?: return errorLines("head: invalid number of lines: ‘$text’")
    return textLines(if (count >= 0) input.take(count) else input.dropLast(-count))
}

internal sealed interface LineCountResult {
    // The NUM as typed, or null when none was given
    data class Count(val text: String?) : LineCountResult
    data class Failed(val output: CommandResult) : LineCountResult
}

// head and tail options: -n NUM, -nNUM, --lines=NUM, or the short form -NUM. File operands are refused,
// since the launcher has no files yet.
internal fun parseLineCount(tool: String, words: List<String>): LineCountResult {
    val expanded = words.mapIndexed { index, word ->
        val isValue = index > 0 && (words[index - 1] == "-n" || words[index - 1] == "--lines")
        if (!isValue && Regex("-\\d+").matches(word)) "-n${word.drop(1)}" else word
    }
    val args = when (val result = parseGnuArgs(tool, expanded, "", "n", mapOf("lines" to 'n'))) {
        is GnuArgsResult.Invalid -> return LineCountResult.Failed(errorLines(result.message, tryHelp(tool)))
        is GnuArgsResult.Parsed -> result.args
    }
    if (args.operands.isNotEmpty()) {
        val errors = args.operands.map { "$tool: cannot open '$it' for reading: No such file or directory" }
        return LineCountResult.Failed(errorLines(*errors.toTypedArray()))
    }
    return LineCountResult.Count(args.values['n']?.last())
}

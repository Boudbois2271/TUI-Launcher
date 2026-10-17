package com.ilanmarchand.tui_launcher.commands

// GNU head: head [-n [-]NUM] [-NUM] [FILE...]  The first 10 lines by default; -n -NUM keeps all but the last
// NUM. Several files each get a "==> name <==" header, as in head.
object HeadCommand : TextTool("head") {
    override val synopsis = "head [-n [-]NUM] [FILE ...]"
    override val summary = "Print the first 10 lines of each file."

    override fun process(words: List<String>, input: List<String>, files: FileLines) = head(words, input, files)
}

internal fun head(words: List<String>, input: List<String>, files: FileLines = NoFiles): CommandResult {
    val (text, operands) = when (val result = parseLineCount("head", words)) {
        is LineCountResult.Failed -> return result.output
        is LineCountResult.Count -> (result.text ?: "10") to result.operands
    }
    val count = text.toIntOrNull() ?: return errorLines("head: invalid number of lines: ‘$text’")
    return eachWithHeaders("head", operands, input, files) { lines ->
        if (count >= 0) lines.take(count) else lines.dropLast(-count)
    }
}

internal sealed interface LineCountResult {
    // The NUM as typed (null when none was given) and the file operands
    data class Count(val text: String?, val operands: List<String>) : LineCountResult
    data class Failed(val output: CommandResult) : LineCountResult
}

// head and tail options: -n NUM, -nNUM, --lines=NUM, or the short form -NUM
internal fun parseLineCount(tool: String, words: List<String>): LineCountResult {
    val expanded = words.mapIndexed { index, word ->
        val isValue = index > 0 && (words[index - 1] == "-n" || words[index - 1] == "--lines")
        if (!isValue && Regex("-\\d+").matches(word)) "-n${word.drop(1)}" else word
    }
    val args = when (val result = parseGnuArgs(tool, expanded, "", "n", mapOf("lines" to 'n'))) {
        is GnuArgsResult.Invalid -> return LineCountResult.Failed(errorLines(result.message, tryHelp(tool)))
        is GnuArgsResult.Parsed -> result.args
    }
    return LineCountResult.Count(args.values['n']?.last(), args.operands)
}

// head's and tail's output for several files: each part under "==> name <==", a blank line between them
internal fun eachWithHeaders(
    tool: String,
    operands: List<String>,
    input: List<String>,
    files: FileLines,
    pick: (List<String>) -> List<String>
): CommandResult {
    val out = mutableListOf<OutputLine>()
    var printed = false
    forEachInput(
        operands, input, files,
        onError = { name, reason -> out += OutputLine("$tool: cannot open '$name' for reading: $reason", isError = true) }
    ) { source ->
        if (operands.size > 1) {
            if (printed) out += OutputLine("")
            out += OutputLine("==> ${source.name ?: "standard input"} <==")
        }
        printed = true
        out += pick(source.lines).map { OutputLine(it) }
    }
    return CommandResult.Output(out)
}

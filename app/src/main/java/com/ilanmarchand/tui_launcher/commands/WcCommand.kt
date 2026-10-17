package com.ilanmarchand.tui_launcher.commands

// GNU wc: wc [-lwmc]  Lines, words, characters and bytes, in that order; lines, words and bytes by default.
// One count prints bare; several are padded to 7 columns, as GNU wc does for piped input.
object WcCommand : TextTool("wc") {
    override fun process(words: List<String>, input: List<String>) = wc(words, input)
}

private val WC_LONG = mapOf("lines" to 'l', "words" to 'w', "chars" to 'm', "bytes" to 'c')

internal fun wc(words: List<String>, input: List<String>): CommandResult {
    val args = when (val result = parseGnuArgs("wc", words, "lwmc", long = WC_LONG)) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("wc"))
        is GnuArgsResult.Parsed -> result.args
    }
    if (args.operands.isNotEmpty()) {
        return errorLines(*args.operands.map { "wc: $it: No such file or directory" }.toTypedArray())
    }
    val selected = args.flags.ifEmpty { setOf('l', 'w', 'c') }
    // Every line ends with a newline, as it would in a file or a pipe
    val text = input.joinToString("") { "$it\n" }
    val counts = listOf(
        'l' to input.size,
        'w' to input.sumOf { line -> line.split(Regex("\\s+")).count { it.isNotEmpty() } },
        'm' to text.codePointCount(0, text.length),
        'c' to text.toByteArray(Charsets.UTF_8).size
    ).filter { it.first in selected }.map { it.second }
    val line = if (counts.size == 1) counts.single().toString() else counts.joinToString(" ") { "$it".padStart(7) }
    return textLines(listOf(line))
}

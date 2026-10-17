package com.ilanmarchand.tui_launcher.commands

// GNU wc: wc [-lwmc] [FILE...]  Lines, words, characters and bytes, in that order; lines, words and bytes by
// default. Counts are padded like GNU wc: 7 columns for piped input, the width of the total byte count for
// files, none for a single count of a single input. Several files end with a total line.
object WcCommand : TextTool("wc") {
    override val synopsis = "wc [-lwmc] [FILE ...]"
    override val summary = "Print newline, word, and byte counts for each file."

    override fun process(words: List<String>, input: List<String>, files: FileLines) = wc(words, input, files)
}

private val WC_LONG = mapOf("lines" to 'l', "words" to 'w', "chars" to 'm', "bytes" to 'c')

internal fun wc(words: List<String>, input: List<String>, files: FileLines = NoFiles): CommandResult {
    val args = when (val result = parseGnuArgs("wc", words, "lwmc", long = WC_LONG)) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("wc"))
        is GnuArgsResult.Parsed -> result.args
    }
    val selected = "lwmc".filter { it in args.flags.ifEmpty { setOf('l', 'w', 'c') } }
    if (args.operands.isEmpty()) {
        val counts = counts(input).filterKeys { it in selected }.values.toList()
        val line = if (counts.size == 1) counts.single().toString() else counts.joinToString(" ") { "$it".padStart(7) }
        return textLines(listOf(line))
    }
    // Counts or an error per operand, in order; widths are known only once all are counted
    val results = mutableListOf<Pair<String, Map<Char, Long>?>>()
    forEachInput(
        args.operands, input, files,
        onError = { name, reason -> results += "wc: $name: $reason" to null }
    ) { source -> results += (source.name ?: "-") to counts(source.lines) }
    val counted = results.mapNotNull { it.second }
    val total = "lwmc".associateWith { key -> counted.sumOf { it.getValue(key) } }
    val width = if (args.operands.size == 1 && selected.length == 1) 1 else maxOf(1, total.getValue('c').toString().length)
    fun row(values: Map<Char, Long>, name: String) =
        selected.map { values.getValue(it).toString().padStart(width) }.joinToString(" ") + " $name"
    val lines = results.map { (label, values) -> if (values == null) OutputLine(label, isError = true) else OutputLine(row(values, label)) }
    val totalLine = if (args.operands.size > 1) listOf(OutputLine(row(total, "total"))) else emptyList()
    return CommandResult.Output(lines + totalLine)
}

// Lines, words, characters and bytes; every line ends with a newline, as in a file or a pipe
private fun counts(lines: List<String>): Map<Char, Long> {
    val text = lines.joinToString("") { "$it\n" }
    return mapOf(
        'l' to lines.size.toLong(),
        'w' to lines.sumOf { line -> line.split(Regex("\\s+")).count { it.isNotEmpty() } }.toLong(),
        'm' to text.codePointCount(0, text.length).toLong(),
        'c' to text.toByteArray(Charsets.UTF_8).size.toLong()
    )
}

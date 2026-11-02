// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

// GNU cut: cut -f LIST [-d DELIM] [-s] | -c LIST [FILE...]  Keeps the chosen fields (TAB-separated unless -d
// says otherwise) or characters of each line. LIST is like 1,3 or 2- or -4 or 2-5. A line without the
// delimiter is printed whole, unless -s. Texts follow GNU cut.
object CutCommand : TextTool("cut") {
    override val synopsis = "cut -f LIST [-d DELIM] [-s] | -c LIST [FILE ...]"
    override val summary = "Remove sections from each line of files."

    override fun process(words: List<String>, input: List<String>, files: FileLines) = cut(words, input, files)
}

private val CUT_LONG = mapOf(
    "fields" to 'f', "delimiter" to 'd', "characters" to 'c', "bytes" to 'b', "only-delimited" to 's'
)

internal fun cut(words: List<String>, input: List<String>, files: FileLines = NoFiles): CommandResult {
    val args = when (val result = parseGnuArgs("cut", words, "s", "fdcb", CUT_LONG)) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("cut"))
        is GnuArgsResult.Parsed -> result.args
    }
    val fieldList = args.values['f']?.last()
    val charList = (args.values['c'] ?: args.values['b'])?.last()
    val list = fieldList ?: charList ?: return errorLines("cut: you must specify a list of bytes, characters, or fields", tryHelp("cut"))
    val delimiter = args.values['d']?.last()
    if (delimiter != null && fieldList == null) {
        return errorLines("cut: an input delimiter may be specified only when operating on fields", tryHelp("cut"))
    }
    if (delimiter != null && delimiter.length != 1) return errorLines("cut: the delimiter must be a single character", tryHelp("cut"))
    val wanted = parseCutList(list) ?: return errorLines("cut: invalid field value ‘$list’", tryHelp("cut"))
    val separator = delimiter?.single() ?: '\t'
    val out = mutableListOf<OutputLine>()
    forEachInput(args.operands, input, files, onError = { name, reason -> out += OutputLine("cut: $name: $reason", isError = true) }) { source ->
        for (line in source.lines) {
            if (fieldList == null) {
                out += OutputLine(line.filterIndexed { index, _ -> wanted(index + 1) })
                continue
            }
            if (separator !in line) {
                if ('s' !in args.flags) out += OutputLine(line)
                continue
            }
            val fields = line.split(separator)
            out += OutputLine(fields.filterIndexed { index, _ -> wanted(index + 1) }.joinToString(separator.toString()))
        }
    }
    return CommandResult.Output(out)
}

// "1,3,5-7,9-" as a test on 1-based positions, or null when it isn't a valid list
internal fun parseCutList(list: String): ((Int) -> Boolean)? {
    val ranges = list.split(',').map { part ->
        val match = Regex("""^(\d*)(-?)(\d*)$""").matchEntire(part) ?: return null
        val (from, dash, to) = match.destructured
        if (from.isEmpty() && to.isEmpty()) return null
        val start = from.toIntOrNull() ?: 1
        val end = when {
            dash.isEmpty() -> start
            to.isEmpty() -> Int.MAX_VALUE
            else -> to.toInt()
        }
        if (start < 1 || end < start) return null
        start..end
    }
    return { position -> ranges.any { position in it } }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

// GNU uniq: uniq [-cdui] [INPUT]  Folds runs of the same line into one. -c counts them, -d keeps only repeated
// lines, -u only lines that don't repeat, -i ignores case. Like uniq, only neighbors count: sort first. An
// OUTPUT file isn't supported; use > instead.
object UniqCommand : TextTool("uniq") {
    override val synopsis = "uniq [-cdui] [INPUT]"
    override val summary = "Report or omit repeated lines."

    override fun process(words: List<String>, input: List<String>, files: FileLines) = uniq(words, input, files)
}

private val UNIQ_LONG = mapOf("count" to 'c', "repeated" to 'd', "unique" to 'u', "ignore-case" to 'i')

internal fun uniq(words: List<String>, input: List<String>, files: FileLines = NoFiles): CommandResult {
    val args = when (val result = parseGnuArgs("uniq", words, "cdui", long = UNIQ_LONG)) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("uniq"))
        is GnuArgsResult.Parsed -> result.args
    }
    if (args.operands.size > 1) return errorLines("uniq: an OUTPUT file is not available on this phone; use > instead")
    var lines: List<String> = emptyList()
    var failure: String? = null
    forEachInput(args.operands, input, files, onError = { name, reason -> failure = "uniq: $name: $reason" }) { lines = it.lines }
    failure?.let { return errorLines(it) }
    val runs = mutableListOf<MutableList<String>>()
    for (line in lines) {
        val previous = runs.lastOrNull()?.first()
        if (previous != null && previous.equals(line, ignoreCase = 'i' in args.flags)) runs.last() += line else runs += mutableListOf(line)
    }
    val kept = runs.filter {
        when {
            'd' in args.flags -> it.size > 1
            'u' in args.flags -> it.size == 1
            else -> true
        }
    }
    return textLines(kept.map { if ('c' in args.flags) "${it.size.toString().padStart(7)} ${it.first()}" else it.first() })
}

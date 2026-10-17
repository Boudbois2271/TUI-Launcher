package com.ilanmarchand.tui_launcher.commands

// GNU uniq: uniq [-cdui]  Folds runs of the same line into one. -c counts them, -d keeps only repeated lines,
// -u only lines that don't repeat, -i ignores case. Like uniq, only neighbors count: sort first.
object UniqCommand : TextTool("uniq") {
    override val synopsis = "uniq [-cdui]"
    override val summary = "Filter adjacent matching lines."

    override fun process(words: List<String>, input: List<String>) = uniq(words, input)
}

private val UNIQ_LONG = mapOf("count" to 'c', "repeated" to 'd', "unique" to 'u', "ignore-case" to 'i')

internal fun uniq(words: List<String>, input: List<String>): CommandResult {
    val args = when (val result = parseGnuArgs("uniq", words, "cdui", long = UNIQ_LONG)) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("uniq"))
        is GnuArgsResult.Parsed -> result.args
    }
    if (args.operands.isNotEmpty()) {
        return errorLines(*args.operands.map { "uniq: $it: No such file or directory" }.toTypedArray())
    }
    val runs = mutableListOf<MutableList<String>>()
    for (line in input) {
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

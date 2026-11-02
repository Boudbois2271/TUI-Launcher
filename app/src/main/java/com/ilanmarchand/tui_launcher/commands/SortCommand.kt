// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import java.text.Collator
import java.util.Locale

// GNU sort: sort [-rnuf] [FILE...]  The lines of all files together, in dictionary order (digits, then
// letters, lowercase before uppercase, as GNU sort does in a regular locale), -n by leading number,
// -r reversed, -u without repeats, -f ignoring case.
object SortCommand : TextTool("sort") {
    override val synopsis = "sort [-rnuf] [FILE ...]"
    override val summary = "Write sorted concatenation of all files."

    override fun process(words: List<String>, input: List<String>, files: FileLines) = sort(words, input, files)
}

private val SORT_LONG = mapOf("reverse" to 'r', "numeric-sort" to 'n', "unique" to 'u', "ignore-case" to 'f')

internal fun sort(words: List<String>, input: List<String>, files: FileLines = NoFiles): CommandResult {
    val args = when (val result = parseGnuArgs("sort", words, "rnuf", long = SORT_LONG)) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("sort"), exitCode = 2)
        is GnuArgsResult.Parsed -> result.args
    }
    val all = mutableListOf<String>()
    var failure: String? = null
    forEachInput(args.operands, input, files, onError = { name, reason -> if (failure == null) failure = "sort: cannot read: $name: $reason" }) {
        all += it.lines
    }
    // Like GNU sort, a file it can't read stops it before it prints anything
    failure?.let { return errorLines(it, exitCode = 2) }
    val collator = Collator.getInstance(Locale.ROOT)
    val text = Comparator<String> { a, b ->
        if ('f' in args.flags) collator.compare(a.lowercase(), b.lowercase()) else collator.compare(a, b)
    }
    val key = if ('n' in args.flags) compareBy<String> { leadingNumber(it) } else text
    // Lines with equal keys fall back to a whole-line comparison, like GNU sort's last resort
    var order = key.then(text)
    if ('r' in args.flags) order = order.reversed()
    val sorted = all.sortedWith(order)
    val result = if ('u' in args.flags) {
        sorted.filterIndexed { index, line -> index == 0 || key.compare(sorted[index - 1], line) != 0 }
    } else {
        sorted
    }
    return textLines(result)
}

// The number a line starts with, after blanks; 0 when it doesn't start with one
private fun leadingNumber(line: String): Double =
    Regex("""^\s*(-?\d+(?:\.\d+)?)""").find(line)?.groupValues?.get(1)?.toDouble() ?: 0.0

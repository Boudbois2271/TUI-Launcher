package com.ilanmarchand.tui_launcher.commands

// GNU tr: tr [-cds] SET1 [SET2]  Translates, deletes (-d) or squeezes (-s) characters of the piped-in text,
// newlines included, so tr ' ' '\n' puts each word on its own line. Sets take ranges (a-z), classes
// ([:upper:]) and \n, \t, \\. -c uses every character not in SET1. Texts follow GNU tr.
object TrCommand : TextTool("tr") {
    override val synopsis = "tr [-cds] SET1 [SET2]"
    override val summary = "Translate, squeeze, and/or delete characters."

    override fun process(words: List<String>, input: List<String>, files: FileLines) = tr(words, input)
}

private val CLASSES = mapOf(
    "lower" to ('a'..'z').toList(),
    "upper" to ('A'..'Z').toList(),
    "digit" to ('0'..'9').toList(),
    "alpha" to ('a'..'z') + ('A'..'Z'),
    "alnum" to ('a'..'z') + ('A'..'Z') + ('0'..'9'),
    "space" to listOf(' ', '\t', '\n', '\r', '\u000B', '\u000C'),
    "blank" to listOf(' ', '\t'),
    "punct" to "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".toList()
)

internal fun tr(words: List<String>, input: List<String>): CommandResult {
    val flags = mutableSetOf<Char>()
    val sets = mutableListOf<String>()
    for (word in words) {
        if (word.startsWith("-") && word.length > 1 && sets.isEmpty()) {
            for (letter in word.drop(1)) {
                if (letter !in "cdsC") return errorLines("tr: invalid option -- '$letter'", tryHelp("tr"))
                flags += if (letter == 'C') 'c' else letter
            }
        } else {
            sets += word
        }
    }
    val delete = 'd' in flags
    val squeeze = 's' in flags
    when {
        sets.isEmpty() -> return errorLines("tr: missing operand", tryHelp("tr"))
        sets.size == 1 && !delete && !squeeze -> return errorLines(
            "tr: missing operand after ‘${sets[0]}’",
            "Two strings must be given when translating.",
            tryHelp("tr")
        )
        sets.size > 2 || (sets.size == 2 && delete && !squeeze) -> return errorLines("tr: extra operand ‘${sets.last()}’", tryHelp("tr"))
    }
    val first = expandSet(sets[0])
    val second = sets.getOrNull(1)?.let { expandSet(it) }
    val inFirst: (Char) -> Boolean = if ('c' in flags) { c -> c !in first } else { c -> c in first }
    var text = input.joinToString("") { "$it\n" }
    text = when {
        delete -> text.filterNot(inFirst)
        second != null && second.isNotEmpty() -> text.map { c ->
            if (!inFirst(c)) c else if ('c' in flags) second.last() else second[minOf(first.indexOf(c), second.lastIndex)]
        }.joinToString("")
        else -> text
    }
    if (squeeze) {
        // Squeezes the last set given: SET2 when translating or deleting, else SET1
        val squeezed: (Char) -> Boolean = second?.let { set -> { c: Char -> c in set } } ?: inFirst
        text = text.filterIndexed { index, c -> index == 0 || c != text[index - 1] || !squeezed(c) }
    }
    return textLines(text.split('\n').let { if (text.endsWith("\n")) it.dropLast(1) else it })
}

// The characters of a tr set: ranges, [:classes:] and backslash escapes expanded
internal fun expandSet(set: String): List<Char> {
    val chars = mutableListOf<Char>()
    var i = 0
    fun charAt(index: Int): Pair<Char, Int> {
        val c = set[index]
        if (c != '\\' || index + 1 >= set.length) return c to index + 1
        val escaped = when (set[index + 1]) {
            'n' -> '\n'
            't' -> '\t'
            'r' -> '\r'
            else -> set[index + 1]
        }
        return escaped to index + 2
    }
    while (i < set.length) {
        if (set.startsWith("[:", i)) {
            val end = set.indexOf(":]", i + 2)
            val name = if (end > 0) set.substring(i + 2, end) else null
            if (name != null && name in CLASSES) {
                chars += CLASSES.getValue(name)
                i = end + 2
                continue
            }
        }
        val (c, next) = charAt(i)
        if (next < set.length - 1 && set[next] == '-') {
            val (last, after) = charAt(next + 1)
            if (last >= c) {
                chars += (c..last)
                i = after
                continue
            }
        }
        chars += c
        i = next
    }
    return chars
}

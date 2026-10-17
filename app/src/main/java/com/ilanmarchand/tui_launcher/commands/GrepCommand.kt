package com.ilanmarchand.tui_launcher.commands

import java.util.regex.PatternSyntaxException

// GNU grep: grep [-ivcnEFGwxq] [-e PATTERN]... PATTERN
// Exits 0 when a line is selected, 1 when none is, 2 on an error, as grep does. Texts follow GNU grep.
object GrepCommand : TextTool("grep") {
    override val synopsis = "grep [-ivcnEFGwxq] [-e PATTERN]... PATTERN"
    override val summary = "Print lines that match patterns."

    override fun process(words: List<String>, input: List<String>) = grep(words, input)
}

private val GREP_USAGE = arrayOf(
    "Usage: grep [OPTION]... PATTERNS [FILE]...",
    "Try 'grep --help' for more information."
)

private val GREP_LONG = mapOf(
    "ignore-case" to 'i', "invert-match" to 'v', "count" to 'c', "line-number" to 'n',
    "extended-regexp" to 'E', "fixed-strings" to 'F', "basic-regexp" to 'G', "word-regexp" to 'w',
    "line-regexp" to 'x', "quiet" to 'q', "silent" to 'q', "regexp" to 'e'
)

internal fun grep(words: List<String>, input: List<String>): CommandResult {
    val args = when (val result = parseGnuArgs("grep", words, "ivcnEFGwxqs", "e", GREP_LONG)) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, *GREP_USAGE, exitCode = 2)
        is GnuArgsResult.Parsed -> result.args
    }
    val operands = args.operands.toMutableList()
    val patterns = args.values['e']
        ?: listOf(operands.removeFirstOrNull() ?: return errorLines(*GREP_USAGE, exitCode = 2))
    if (operands.isNotEmpty()) {
        return errorLines(*operands.map { "grep: $it: No such file or directory" }.toTypedArray(), exitCode = 2)
    }
    val regexes = try {
        patterns.map { grepRegex(it, args.flags) }
    } catch (e: PatternSyntaxException) {
        val message = if (e.description.startsWith("Unclosed group")) "Unmatched ( or \\(" else "Invalid regular expression"
        return errorLines("grep: $message", exitCode = 2)
    }
    val invert = 'v' in args.flags
    val selected = input.withIndex().filter { (_, line) -> regexes.any { it.containsMatchIn(line) } != invert }
    val status = if (selected.isEmpty()) 1 else 0
    return when {
        'q' in args.flags -> textLines(emptyList(), status)
        'c' in args.flags -> textLines(listOf(selected.size.toString()), status)
        'n' in args.flags -> textLines(selected.map { (index, line) -> "${index + 1}:$line" }, status)
        else -> textLines(selected.map { it.value }, status)
    }
}

// GNU grep's pattern kinds: fixed strings (-F), extended (-E), or basic (the default), where + ? | ( ) { }
// are plain characters unless a backslash turns them into operators
internal fun grepRegex(pattern: String, flags: Set<Char>): Regex {
    var body = when {
        'F' in flags -> Regex.escape(pattern)
        'E' in flags -> posixToJava(pattern, basic = false)
        else -> posixToJava(pattern, basic = true)
    }
    if ('w' in flags) body = "(?<![\\p{L}\\p{N}_])(?:$body)(?![\\p{L}\\p{N}_])"
    if ('x' in flags) body = "^(?:$body)$"
    return Regex(body, if ('i' in flags) setOf(RegexOption.IGNORE_CASE) else emptySet())
}

private val POSIX_CLASSES = mapOf(
    "alpha" to "\\p{Alpha}", "digit" to "\\d", "alnum" to "\\p{Alnum}", "upper" to "\\p{Upper}",
    "lower" to "\\p{Lower}", "space" to "\\s", "blank" to "\\p{Blank}", "punct" to "\\p{Punct}",
    "xdigit" to "\\p{XDigit}", "cntrl" to "\\p{Cntrl}", "print" to "\\p{Print}", "graph" to "\\p{Graph}"
)

// Rewrites a POSIX regular expression for Java: bracket expressions (with classes such as [:digit:]),
// GNU's \< and \> word edges, a leading * taken literally, and in basic mode the backslashed operators
internal fun posixToJava(pattern: String, basic: Boolean): String {
    val out = StringBuilder()
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        when {
            c == '\\' && i + 1 < pattern.length -> {
                val next = pattern[i + 1]
                when {
                    next == '<' || next == '>' -> out.append("\\b")
                    basic && next in "+?|(){}" -> out.append(next)
                    else -> out.append('\\').append(next)
                }
                i += 2
                continue
            }
            c == '[' -> {
                i = appendBracket(pattern, i, out)
                continue
            }
            c == '*' && (out.isEmpty() || out.endsWith("(") || out.endsWith("|") || out.endsWith("^")) ->
                out.append("\\*")
            basic && c in "+?|(){}" -> out.append('\\').append(c)
            else -> out.append(c)
        }
        i++
    }
    return out.toString()
}

// Copies a POSIX bracket expression starting at `start`, where a backslash is a plain character and a
// leading ] belongs to the set. Returns the index after its closing ].
private fun appendBracket(pattern: String, start: Int, out: StringBuilder): Int {
    var i = start + 1
    val body = StringBuilder()
    if (pattern.getOrNull(i) == '^') {
        body.append('^')
        i++
    }
    if (pattern.getOrNull(i) == ']') {
        body.append("\\]")
        i++
    }
    while (i < pattern.length && pattern[i] != ']') {
        val c = pattern[i]
        if (c == '[' && pattern.getOrNull(i + 1) == ':') {
            val end = pattern.indexOf(":]", i + 2)
            if (end > 0) {
                body.append(
                    POSIX_CLASSES[pattern.substring(i + 2, end)]
                        ?: throw PatternSyntaxException("Invalid character class", pattern, i)
                )
                i = end + 2
                continue
            }
        }
        if (c in "[&\\^") body.append('\\')
        body.append(c)
        i++
    }
    if (i >= pattern.length) throw PatternSyntaxException("Unclosed character class", pattern, start)
    out.append('[').append(body).append(']')
    return i + 1
}

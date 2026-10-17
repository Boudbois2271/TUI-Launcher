package com.ilanmarchand.tui_launcher.commands

import java.util.regex.PatternSyntaxException

// GNU sed's core: sed [-nE] [-e SCRIPT]... [SCRIPT] [FILE...]
// Commands: s/regex/replacement/[gIp N] (& and \1…\9 in the replacement), p, d, q and =; addresses: N, $,
// /regex/, ADDR1,ADDR2 ranges, and ! to negate; several commands joined by ; or given with -e. Regexes are
// basic like grep's, or extended with -E. All files form one stream, as in sed. Texts follow GNU sed 4.10.
object SedCommand : TextTool("sed") {
    override val synopsis = "sed [-nE] [-e SCRIPT]... [SCRIPT] [FILE ...]"
    override val summary = "Stream editor for filtering and transforming text."

    override fun process(words: List<String>, input: List<String>, files: FileLines) = sed(words, input, files)
}

private sealed interface Address {
    data class Line(val number: Int) : Address
    data object Last : Address
    data class Pattern(val regex: Regex) : Address
}

private class Substitution(val regex: Regex, val replacement: String, val global: Boolean, val occurrence: Int, val print: Boolean)

private class SedInstruction(val from: Address?, val to: Address?, val negate: Boolean, val name: Char, val substitution: Substitution?) {
    // Whether a range has started and not ended yet
    var inRange = false
}

private class SedError(message: String) : Exception(message)

internal fun sed(words: List<String>, input: List<String>, files: FileLines = NoFiles): CommandResult {
    var quiet = false
    var extended = false
    val scripts = mutableListOf<String>()
    val operands = mutableListOf<String>()
    var index = 0
    while (index < words.size) {
        val word = words[index++]
        when {
            word == "-n" || word == "--quiet" || word == "--silent" -> quiet = true
            word == "-E" || word == "-r" || word == "--regexp-extended" -> extended = true
            word == "-e" -> scripts += words.getOrNull(index++) ?: return errorLines("sed: option requires an argument -- 'e'", tryHelp("sed"))
            word.startsWith("--expression=") -> scripts += word.substringAfter('=')
            word == "-i" || word.startsWith("--in-place") -> return errorLines("sed: -i is not available on this phone; use > to write a new file")
            word.startsWith("-") && word.length > 1 && word.drop(1).all { it in "nEr" } -> {
                if ('n' in word) quiet = true
                if ('E' in word || 'r' in word) extended = true
            }
            word.startsWith("-") && word.length > 1 -> return errorLines("sed: invalid option -- '${word[1]}'", tryHelp("sed"))
            else -> operands += word
        }
    }
    if (scripts.isEmpty()) {
        scripts += operands.removeFirstOrNull()
            ?: return errorLines("Usage: sed [OPTION]... {script-only-if-no-other-script} [input-file]...")
    }
    val commands = try {
        parseSedScript(scripts.joinToString("\n"), extended)
    } catch (e: SedError) {
        return errorLines("sed: -e expression #1, ${e.message}")
    }
    val lines = mutableListOf<String>()
    val errors = mutableListOf<OutputLine>()
    forEachInput(operands, input, files, onError = { name, reason ->
        errors += OutputLine("sed: can't read $name: $reason", isError = true)
    }) { lines += it.lines }
    val out = mutableListOf<OutputLine>()
    fun print(space: String) = space.split('\n').forEach { out += OutputLine(it) }
    run stream@{
        lines.forEachIndexed { position, line ->
            val lineNumber = position + 1
            val isLast = position == lines.lastIndex
            var space = line
            var deleted = false
            var quit = false
            for (command in commands) {
                if (!command.applies(lineNumber, space, isLast)) continue
                when (command.name) {
                    'p' -> print(space)
                    '=' -> out += OutputLine(lineNumber.toString())
                    'd' -> deleted = true
                    'q' -> quit = true
                    's' -> {
                        val substitution = command.substitution!!
                        substitute(space, substitution)?.let {
                            space = it
                            if (substitution.print) print(space)
                        }
                    }
                }
                if (deleted || quit) break
            }
            if (!deleted && !quiet) print(space)
            if (quit) return@stream
        }
    }
    return CommandResult.Output(errors + out, if (errors.isEmpty()) 0 else 2)
}

// Whether the command runs on this line; a range starts when its first address matches and ends at its second
private fun SedInstruction.applies(lineNumber: Int, space: String, isLast: Boolean): Boolean {
    fun matches(address: Address) = when (address) {
        is Address.Line -> lineNumber == address.number
        Address.Last -> isLast
        is Address.Pattern -> address.regex.containsMatchIn(space)
    }
    val selected = when {
        from == null -> true
        to == null -> matches(from)
        inRange -> {
            val ends = when (to) {
                is Address.Line -> lineNumber >= to.number
                Address.Last -> isLast
                is Address.Pattern -> to.regex.containsMatchIn(space)
            }
            if (ends) inRange = false
            true
        }
        matches(from) -> {
            // A second address that is a line already reached makes the range this one line
            inRange = !(to is Address.Line && to.number <= lineNumber)
            true
        }
        else -> false
    }
    return selected != negate
}

// The pattern space after the substitution, or null when nothing was replaced
private fun substitute(space: String, substitution: Substitution): String? {
    val matches = substitution.regex.findAll(space).toList()
    val chosen = matches.filterIndexed { index, _ ->
        val number = index + 1
        if (substitution.global) number >= substitution.occurrence else number == substitution.occurrence
    }
    if (chosen.isEmpty()) return null
    val out = StringBuilder()
    var last = 0
    for (match in chosen) {
        out.append(space, last, match.range.first)
        out.append(expandReplacement(substitution.replacement, match))
        last = match.range.last + 1
    }
    out.append(space.substring(last))
    return out.toString()
}

// & is the whole match, \1…\9 its groups, \n a new line; \& and \\ are a plain & and \
private fun expandReplacement(replacement: String, match: MatchResult): String {
    val out = StringBuilder()
    var i = 0
    while (i < replacement.length) {
        val c = replacement[i]
        val next = replacement.getOrNull(i + 1)
        when {
            c == '&' -> out.append(match.value)
            c == '\\' && next != null && next.isDigit() -> {
                out.append(match.groupValues.getOrElse(next.digitToInt()) { "" })
                i++
            }
            c == '\\' && next == 'n' -> {
                out.append('\n')
                i++
            }
            c == '\\' && next != null -> {
                out.append(next)
                i++
            }
            else -> out.append(c)
        }
        i++
    }
    return out.toString()
}

// Parses a script; errors carry GNU's "char N: message", N counted from 1
private fun parseSedScript(script: String, extended: Boolean): List<SedInstruction> {
    val commands = mutableListOf<SedInstruction>()
    var i = 0

    fun regex(pattern: String, ignoreCase: Boolean, end: Int): Regex = try {
        Regex(posixToJava(pattern, basic = !extended), if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
    } catch (e: PatternSyntaxException) {
        val message = if (e.description.startsWith("Unclosed group")) "Unmatched ( or \\(" else "Invalid regular expression"
        throw SedError("char $end: $message")
    }

    // Reads up to the next unescaped delimiter; an escaped delimiter becomes plain
    fun until(delimiter: Char, what: String): String {
        val out = StringBuilder()
        while (i < script.length && script[i] != delimiter) {
            if (script[i] == '\\' && i + 1 < script.length) {
                if (script[i + 1] != delimiter) out.append('\\')
                out.append(script[i + 1])
                i += 2
            } else {
                out.append(script[i++])
            }
        }
        if (i >= script.length) throw SedError("char ${script.length}: $what")
        i++
        return out.toString()
    }

    fun address(): Address? = when {
        i < script.length && script[i].isDigit() -> {
            val digits = script.drop(i).takeWhile { it.isDigit() }
            i += digits.length
            Address.Line(digits.toInt())
        }
        i < script.length && script[i] == '$' -> {
            i++
            Address.Last
        }
        i < script.length && script[i] == '/' -> {
            i++
            val pattern = until('/', "unterminated address regex")
            val ignoreCase = script.getOrNull(i) == 'I'
            if (ignoreCase) i++
            Address.Pattern(regex(pattern, ignoreCase, i))
        }
        else -> null
    }

    while (i < script.length) {
        if (script[i].isWhitespace() || script[i] == ';') {
            i++
            continue
        }
        val from = address()
        var to: Address? = null
        if (from != null && script.getOrNull(i) == ',') {
            i++
            to = address() ?: throw SedError("char ${i + 1}: unexpected `,'")
        }
        while (i < script.length && script[i] == ' ') i++
        var negate = false
        if (script.getOrNull(i) == '!') {
            negate = true
            i++
            while (i < script.length && script[i] == ' ') i++
        }
        val name = script.getOrNull(i) ?: throw SedError("char ${script.length}: missing command")
        i++
        val substitution = when (name) {
            'p', 'd', 'q', '=' -> null
            's' -> {
                val delimiter = script.getOrNull(i) ?: throw SedError("char ${script.length}: unterminated 's' command")
                i++
                val pattern = until(delimiter, "unterminated 's' command")
                val replacement = until(delimiter, "unterminated 's' command")
                var global = false
                var ignoreCase = false
                var print = false
                var occurrence = 1
                while (i < script.length && script[i] != ';' && script[i] != '\n' && script[i] != '}') {
                    val flag = script[i]
                    when {
                        flag == 'g' -> global = true
                        flag == 'i' || flag == 'I' -> ignoreCase = true
                        flag == 'p' -> print = true
                        flag.isDigit() -> {
                            val digits = script.drop(i).takeWhile { it.isDigit() }
                            occurrence = digits.toInt()
                            i += digits.length - 1
                        }
                        flag.isWhitespace() -> Unit
                        else -> throw SedError("char ${i + 1}: unknown option to 's'")
                    }
                    i++
                }
                Substitution(regex(pattern, ignoreCase, i), replacement, global, maxOf(occurrence, 1), print)
            }
            else -> throw SedError("char $i: unknown command: '$name'")
        }
        commands += SedInstruction(from, to, negate, name, substitution)
    }
    return commands
}

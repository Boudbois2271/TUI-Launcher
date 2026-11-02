// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

// Command-line options parsed the GNU way, for the text tools
internal data class GnuArgs(
    val flags: Set<Char>,
    val values: Map<Char, List<String>>,
    val operands: List<String>
)

internal sealed interface GnuArgsResult {
    data class Parsed(val args: GnuArgs) : GnuArgsResult
    data class Invalid(val message: String) : GnuArgsResult
}

// GNU getopt rules: short flags can be combined (-in), a short option with a value takes it attached (-n5)
// or as the next word (-n 5), long options use --name or --name=value, "--" ends the options, and a lone
// "-" is an operand. `flags` are letters without a value, `valued` letters with one, `long` maps long names
// to their letter. Unknown options give GNU's message.
internal fun parseGnuArgs(
    tool: String,
    words: List<String>,
    flags: String,
    valued: String = "",
    long: Map<String, Char> = emptyMap()
): GnuArgsResult {
    val foundFlags = mutableSetOf<Char>()
    val values = mutableMapOf<Char, MutableList<String>>()
    val operands = mutableListOf<String>()
    var index = 0
    while (index < words.size) {
        val word = words[index++]
        when {
            word == "--" -> {
                operands += words.drop(index)
                break
            }
            word.startsWith("--") -> {
                val name = word.drop(2).substringBefore('=')
                val letter = long[name] ?: return GnuArgsResult.Invalid("$tool: unrecognized option '$word'")
                if (letter in valued) {
                    val value = if ('=' in word) word.substringAfter('=') else words.getOrNull(index++)
                        ?: return GnuArgsResult.Invalid("$tool: option '--$name' requires an argument")
                    values.getOrPut(letter) { mutableListOf() } += value
                } else {
                    foundFlags += letter
                }
            }
            word.startsWith("-") && word.length > 1 -> {
                var position = 1
                while (position < word.length) {
                    val letter = word[position++]
                    when (letter) {
                        in valued -> {
                            val value = if (position < word.length) word.substring(position) else words.getOrNull(index++)
                                ?: return GnuArgsResult.Invalid("$tool: option requires an argument -- '$letter'")
                            values.getOrPut(letter) { mutableListOf() } += value
                            position = word.length
                        }
                        in flags -> foundFlags += letter
                        else -> return GnuArgsResult.Invalid("$tool: invalid option -- '$letter'")
                    }
                }
            }
            else -> operands += word
        }
    }
    return GnuArgsResult.Parsed(GnuArgs(foundFlags, values, operands))
}

// GNU tools add this hint after an option error
internal fun tryHelp(tool: String) = "Try '$tool --help' for more information."

internal fun errorLines(vararg lines: String, exitCode: Int = 1) =
    CommandResult.Output(lines.map { OutputLine(it, isError = true) }, exitCode)

internal fun textLines(lines: List<String>, exitCode: Int = 0) =
    CommandResult.Output(lines.map { OutputLine(it) }, exitCode)

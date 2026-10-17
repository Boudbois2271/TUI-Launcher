package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import java.math.BigDecimal
import java.math.RoundingMode

// GNU seq: seq [-w] [-s SEP] [FIRST [INCREMENT]] LAST  Numbers from FIRST (1) to LAST, one per line or joined
// by SEP; -w pads them with zeros to the same width. Decimals keep the most digits any argument has, like
// seq. At most 100000 numbers, so a typo can't flood the phone. Texts follow GNU seq.
object SeqCommand : BuiltinCommand {
    override val name = "seq"
    override val synopsis = "seq [-w] [-s SEP] [FIRST [INCREMENT]] LAST"
    override val summary = "Print a sequence of numbers."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("seq: unexpected EOF while looking for matching quote")
        return seq(words)
    }
}

private const val MAX_NUMBERS = 100_000

internal fun seq(words: List<String>): CommandResult {
    var equalWidth = false
    var separator = "\n"
    val numbers = mutableListOf<String>()
    var index = 0
    while (index < words.size) {
        val word = words[index++]
        when {
            // A negative number is a number, not an option, as seq reads it
            word.startsWith("-") && word.length > 1 && !word[1].isDigit() && word[1] != '.' -> when {
                word == "-w" || word == "--equal-width" -> equalWidth = true
                word == "-s" -> separator = words.getOrNull(index++)
                    ?: return errorLines("seq: option requires an argument -- 's'", tryHelp("seq"))
                word.startsWith("-s") -> separator = word.drop(2)
                word.startsWith("--separator=") -> separator = word.substringAfter('=')
                else -> return errorLines("seq: invalid option -- '${word[1]}'", tryHelp("seq"))
            }
            else -> numbers += word
        }
    }
    if (numbers.isEmpty()) return errorLines("seq: missing operand", tryHelp("seq"))
    if (numbers.size > 3) return errorLines("seq: extra operand ‘${numbers[3]}’", tryHelp("seq"))
    val values = numbers.map {
        it.toBigDecimalOrNull() ?: return errorLines("seq: invalid floating point argument: ‘$it’", tryHelp("seq"))
    }
    val (first, increment, last) = when (values.size) {
        1 -> Triple(BigDecimal.ONE, BigDecimal.ONE, values[0])
        2 -> Triple(values[0], BigDecimal.ONE, values[1])
        else -> Triple(values[0], values[1], values[2])
    }
    if (increment.signum() == 0) return errorLines("seq: invalid Zero increment value: ‘${numbers[1]}’", tryHelp("seq"))
    val scale = numbers.maxOf { if ('.' in it) it.substringAfter('.').length else 0 }
    val out = mutableListOf<String>()
    var current = first
    while (if (increment.signum() > 0) current <= last else current >= last) {
        if (out.size == MAX_NUMBERS) return errorLines("seq: more than $MAX_NUMBERS numbers is too many for this phone")
        out += current.setScale(scale, RoundingMode.HALF_UP).toPlainString()
        current += increment
    }
    val shown = if (equalWidth) out.map { it.padStart(out.maxOf { n -> n.length }, '0') } else out
    return textLines(if (separator == "\n") shown else listOf(shown.joinToString(separator)))
}

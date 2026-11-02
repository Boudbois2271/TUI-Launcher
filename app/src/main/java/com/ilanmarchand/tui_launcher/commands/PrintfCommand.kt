// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

// bash's printf: printf FORMAT [ARGUMENTS...]  Conversions %s %b %c %d %i %o %u %x %X %e %E %f %F %g %G and
// %%, with flags (-+ #0), a width and a precision (* takes them from the arguments). The format is used
// again until every argument is consumed; missing arguments are empty or 0. Escapes such as \n and \t work
// in the format, and in %b's argument. Texts follow bash 5.
object PrintfCommand : BuiltinCommand {
    override val name = "printf"
    override val synopsis = "printf FORMAT [ARGUMENTS ...]"
    override val summary = "Formats and prints ARGUMENTS under control of the FORMAT."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("printf: unexpected EOF while looking for matching quote")
        return printf(words)
    }
}

// bash's true and false: they ignore their arguments and only set $?
object TrueCommand : BuiltinCommand {
    override val name = "true"
    override val synopsis = "true"
    override val summary = "Return a successful result."
    override val takesArgs = true

    override fun run(context: Context, args: String) = CommandResult.Output(emptyList(), 0)
}

object FalseCommand : BuiltinCommand {
    override val name = "false"
    override val synopsis = "false"
    override val summary = "Return an unsuccessful result."
    override val takesArgs = true

    override fun run(context: Context, args: String) = CommandResult.Output(emptyList(), 1)
}

private class PrintfStop : Exception()

internal fun printf(words: List<String>): CommandResult {
    val format = words.firstOrNull() ?: return errorLines("printf: usage: printf [-v var] format [arguments]", exitCode = 2)
    val args = words.drop(1)
    val out = StringBuilder()
    val errors = mutableListOf<OutputLine>()
    var next = 0
    fun arg(): String? = args.getOrNull(next++)

    // A number argument: decimal, 0x hex, 0 octal, or 'c for the character's code, as in bash
    fun integer(): BigInteger {
        val text = arg() ?: return BigInteger.ZERO
        if (text.length >= 2 && (text[0] == '\'' || text[0] == '"')) return text.codePointAt(1).toBigInteger()
        val trimmed = text.trim()
        val negative = trimmed.startsWith("-")
        val digits = trimmed.removePrefix("-").removePrefix("+")
        val value = runCatching {
            when {
                digits.startsWith("0x") || digits.startsWith("0X") -> BigInteger(digits.drop(2), 16)
                digits.length > 1 && digits.startsWith("0") -> BigInteger(digits.drop(1), 8)
                else -> BigInteger(digits)
            }
        }.getOrNull()
        if (value == null) {
            errors += OutputLine("printf: $text: invalid number", isError = true)
            return BigInteger.ZERO
        }
        return if (negative) value.negate() else value
    }

    fun decimal(): Double {
        val text = arg() ?: return 0.0
        if (text.length >= 2 && (text[0] == '\'' || text[0] == '"')) return text.codePointAt(1).toDouble()
        return text.trim().toDoubleOrNull() ?: 0.0.also { errors += OutputLine("printf: $text: invalid number", isError = true) }
    }

    try {
        do {
            val before = next
            var i = 0
            while (i < format.length) {
                val c = format[i]
                when {
                    c == '\\' -> i = appendEscape(format, i, out, inArgument = false)
                    c != '%' -> {
                        out.append(c)
                        i++
                    }
                    format.getOrNull(i + 1) == '%' -> {
                        out.append('%')
                        i += 2
                    }
                    else -> {
                        i++
                        val flags = StringBuilder()
                        while (i < format.length && format[i] in "-+ #0") flags.append(format[i++])
                        var width: Int? = null
                        if (format.getOrNull(i) == '*') {
                            width = integer().toInt()
                            i++
                        } else {
                            val digits = format.drop(i).takeWhile { it.isDigit() }
                            if (digits.isNotEmpty()) width = digits.toInt()
                            i += digits.length
                        }
                        if (width != null && width < 0) {
                            flags.append('-')
                            width = -width
                        }
                        var precision: Int? = null
                        if (format.getOrNull(i) == '.') {
                            i++
                            if (format.getOrNull(i) == '*') {
                                precision = integer().toInt()
                                i++
                            } else {
                                val digits = format.drop(i).takeWhile { it.isDigit() }
                                precision = digits.toIntOrNull() ?: 0
                                i += digits.length
                            }
                        }
                        // Length modifiers such as l or h change nothing here
                        while (i < format.length && format[i] in "hlLqjzt") i++
                        val conversion = format.getOrNull(i)
                        if (conversion == null) {
                            errors += OutputLine("printf: `%': missing format character", isError = true)
                            throw PrintfStop()
                        }
                        i++
                        val spec = Spec(flags.toString(), width, precision)
                        val text = when (conversion) {
                            's' -> (arg() ?: "").let { if (precision != null) it.take(precision) else it }
                            'b' -> {
                                val expanded = StringBuilder()
                                val stopped = expandEscapes(arg() ?: "", expanded)
                                val text = if (precision != null) expanded.take(precision).toString() else expanded.toString()
                                if (stopped) {
                                    out.append(pad(text, spec))
                                    throw PrintfStop()
                                }
                                text
                            }
                            'c' -> (arg() ?: "").take(1)
                            'd', 'i' -> formatInteger(integer(), 10, spec, upper = false)
                            'u' -> formatInteger(integer().let { if (it.signum() < 0) it.add(BigInteger.ONE.shiftLeft(64)) else it }, 10, spec, upper = false)
                            'o' -> formatInteger(integer(), 8, spec, upper = false)
                            'x' -> formatInteger(integer(), 16, spec, upper = false)
                            'X' -> formatInteger(integer(), 16, spec, upper = true)
                            'e', 'E', 'f', 'F', 'g', 'G' -> formatDecimal(decimal(), conversion, spec)
                            else -> {
                                errors += OutputLine("printf: `$conversion': invalid format character", isError = true)
                                throw PrintfStop()
                            }
                        }
                        out.append(if (conversion in "dioxXuefgEFG") text else pad(text, spec))
                    }
                }
            }
        } while (next in (before + 1)..<args.size)
    } catch (e: PrintfStop) {
        // \c, or a bad conversion, ends the output
    }
    val lines = out.toString().split('\n').let { if (out.endsWith("\n")) it.dropLast(1) else it }
    val printed = if (out.isEmpty()) emptyList() else lines.map { OutputLine(it) }
    return CommandResult.Output(errors + printed, if (errors.isEmpty()) 0 else 1)
}

private class Spec(val flags: String, val width: Int?, val precision: Int?)

private fun pad(text: String, spec: Spec): String {
    val width = spec.width ?: return text
    return if ('-' in spec.flags) text.padEnd(width) else text.padStart(width)
}

// C's integer conversions: a precision is the least number of digits, 0 pads to the width, # adds 0x or 0
private fun formatInteger(value: BigInteger, radix: Int, spec: Spec, upper: Boolean): String {
    var digits = value.abs().toString(radix).let { if (upper) it.uppercase() else it }
    if (spec.precision != null) digits = if (spec.precision == 0 && value.signum() == 0) "" else digits.padStart(spec.precision, '0')
    val prefix = when {
        '#' in spec.flags && radix == 16 && value.signum() != 0 -> if (upper) "0X" else "0x"
        '#' in spec.flags && radix == 8 && !digits.startsWith("0") -> "0"
        else -> ""
    }
    val sign = when {
        value.signum() < 0 -> "-"
        radix == 10 && '+' in spec.flags -> "+"
        radix == 10 && ' ' in spec.flags -> " "
        else -> ""
    }
    val width = spec.width ?: 0
    val body = sign + prefix + digits
    return when {
        '-' in spec.flags -> body.padEnd(width)
        '0' in spec.flags && spec.precision == null -> sign + prefix + digits.padStart(width - sign.length - prefix.length, '0')
        else -> body.padStart(width)
    }
}

// C's %e, %f and %g; %g picks %e or %f by the exponent and drops trailing zeros unless #. Rounding is done
// on the number's exact binary value, half to even, as C does (2.25 with %.1f is 2.2).
private fun formatDecimal(value: Double, conversion: Char, spec: Spec): String {
    val precision = spec.precision ?: 6
    val magnitude = abs(value)
    val digits = when {
        value.isNaN() -> "nan"
        value.isInfinite() -> "inf"
        conversion.lowercaseChar() == 'f' -> fixed(magnitude, precision)
        conversion.lowercaseChar() == 'e' -> scientific(magnitude, precision)
        else -> {
            val p = if (precision == 0) 1 else precision
            val exponent = scientific(magnitude, p - 1).substringAfter('e').toInt()
            val text = if (exponent < -4 || exponent >= p) scientific(magnitude, p - 1) else fixed(magnitude, p - 1 - exponent)
            if ('#' in spec.flags) text else stripZeros(text)
        }
    }.let { if (conversion.isUpperCase()) it.uppercase() else it }
    val sign = when {
        value < 0 || (value == 0.0 && 1 / value < 0) -> "-"
        '+' in spec.flags -> "+"
        ' ' in spec.flags -> " "
        else -> ""
    }
    val width = spec.width ?: 0
    return when {
        '-' in spec.flags -> (sign + digits).padEnd(width)
        '0' in spec.flags && digits[0].isDigit() -> sign + digits.padStart(width - sign.length, '0')
        else -> (sign + digits).padStart(width)
    }
}

private fun fixed(magnitude: Double, precision: Int): String =
    BigDecimal(magnitude).setScale(precision, RoundingMode.HALF_EVEN).toPlainString()

// d.ddde+XX with precision digits after the point
private fun scientific(magnitude: Double, precision: Int): String {
    if (magnitude == 0.0) return (if (precision > 0) "0." + "0".repeat(precision) else "0") + "e+00"
    val rounded = BigDecimal(magnitude).round(MathContext(precision + 1, RoundingMode.HALF_EVEN))
    val unscaled = rounded.unscaledValue().toString()
    val exponent = unscaled.length - 1 - rounded.scale()
    val mantissa = unscaled.padEnd(precision + 1, '0').take(precision + 1)
    val point = if (precision > 0) mantissa.take(1) + "." + mantissa.drop(1) else mantissa
    return point + "e" + (if (exponent < 0) "-" else "+") + abs(exponent).toString().padStart(2, '0')
}

private fun stripZeros(text: String): String {
    val mantissa = text.substringBefore('e')
    val exponent = text.substring(mantissa.length)
    if ('.' !in mantissa) return text
    return mantissa.trimEnd('0').trimEnd('.') + exponent
}

// Appends the escape at text[start] (a backslash) and returns the index after it. In the format, \c is kept
// as typed and \NNN is octal; in %b's argument, \0NNN is octal.
private fun appendEscape(text: String, start: Int, out: StringBuilder, inArgument: Boolean): Int {
    val next = text.getOrNull(start + 1)
    if (next == null) {
        out.append('\\')
        return start + 1
    }
    var i = start + 2
    when (next) {
        'n' -> out.append('\n')
        't' -> out.append('\t')
        'r' -> out.append('\r')
        'a' -> out.append('\u0007')
        'b' -> out.append('\b')
        'f' -> out.append('\u000c')
        'v' -> out.append('\u000b')
        'e', 'E' -> out.append('\u001b')
        '\\' -> out.append('\\')
        '"' -> out.append('"')
        '\'' -> out.append('\'')
        'x' -> {
            val hex = text.drop(i).take(2).takeWhile { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
            if (hex.isEmpty()) out.append("\\x") else out.append(hex.toInt(16).toChar())
            i += hex.length
        }
        in '0'..'7' -> {
            val from = if (inArgument && next == '0') i else i - 1
            val octal = text.drop(from).take(3).takeWhile { it in '0'..'7' }
            out.append((octal.ifEmpty { "0" }).toInt(8).toChar())
            i = from + octal.length
        }
        else -> out.append('\\').append(next)
    }
    return i
}

// %b's argument with its escapes expanded; true when it held \c, which ends all output
private fun expandEscapes(text: String, out: StringBuilder): Boolean {
    var i = 0
    while (i < text.length) {
        if (text[i] != '\\') {
            out.append(text[i++])
            continue
        }
        if (text.getOrNull(i + 1) == 'c') return true
        i = appendEscape(text, i, out, inArgument = true)
    }
    return false
}

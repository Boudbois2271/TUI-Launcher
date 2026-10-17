package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.system.zoneAbbreviation
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// GNU date: date [-uIR] [+FORMAT]  Prints in English, like `LC_ALL=C date`. Setting the date is refused,
// as it is for anyone but root.
object DateCommand : BuiltinCommand {
    override val name = "date"
    override val synopsis = "date [-uIR] [+FORMAT]"
    override val summary = "Print the system date and time."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("date: unexpected EOF while looking for matching quote")
        return date(words, ZonedDateTime.now(), zoneAbbreviation())
    }
}

// zoneName: the local zone's abbreviation (CEST), used by %Z instead of Java's name for it
internal fun date(words: List<String>, now: ZonedDateTime, zoneName: String? = null): CommandResult {
    var format = "%a %b %e %H:%M:%S %Z %Y"
    var utc = false
    for (word in words) {
        when {
            word == "-u" || word == "--utc" || word == "--universal" -> utc = true
            word == "-I" || word == "--iso-8601" -> format = "%F"
            word == "-R" || word == "--rfc-email" -> format = "%a, %d %b %Y %H:%M:%S %z"
            word.startsWith("+") -> format = word.drop(1)
            word.startsWith("--") -> return errorLines("date: unrecognized option '$word'", tryHelp("date"))
            word.startsWith("-") && word.length > 1 -> return errorLines("date: invalid option -- '${word[1]}'", tryHelp("date"))
            else -> return errorLines("date: cannot set date: Operation not permitted")
        }
    }
    val time = if (utc) now.withZoneSameInstant(ZoneId.of("UTC")) else now
    return textLines(strftime(format, time, if (utc) "UTC" else zoneName).split('\n'))
}

// strftime, as used by date +FORMAT: %Y-%m-%d, %H:%M:%S, %a %b, %s, %Z… Unknown sequences are kept as typed.
internal fun strftime(format: String, time: ZonedDateTime, zoneName: String? = null): String {
    val out = StringBuilder()
    var i = 0
    while (i < format.length) {
        val c = format[i]
        if (c != '%' || i + 1 >= format.length) {
            out.append(c)
            i++
            continue
        }
        val spec = format[i + 1]
        i += 2
        if (spec == ':' && format.getOrNull(i) == 'z') {
            out.append(DateTimeFormatter.ofPattern("xxx", Locale.ROOT).format(time))
            i++
            continue
        }
        val hour12 = (time.hour + 11) % 12 + 1
        out.append(
            when (spec) {
                'a' -> time.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                'A' -> time.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                'b', 'h' -> time.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                'B' -> time.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                'c' -> strftime("%a %b %e %H:%M:%S %Y", time)
                'C' -> two(time.year / 100)
                'd' -> two(time.dayOfMonth)
                'D' -> strftime("%m/%d/%y", time)
                'e' -> time.dayOfMonth.toString().padStart(2)
                'F' -> strftime("%Y-%m-%d", time)
                'H' -> two(time.hour)
                'I' -> two(hour12)
                'j' -> time.dayOfYear.toString().padStart(3, '0')
                'k' -> time.hour.toString().padStart(2)
                'l' -> hour12.toString().padStart(2)
                'm' -> two(time.monthValue)
                'M' -> two(time.minute)
                'n' -> "\n"
                'N' -> time.nano.toString().padStart(9, '0')
                'p' -> if (time.hour < 12) "AM" else "PM"
                'P' -> if (time.hour < 12) "am" else "pm"
                'R' -> strftime("%H:%M", time)
                's' -> time.toEpochSecond().toString()
                'S' -> two(time.second)
                't' -> "\t"
                'T' -> strftime("%H:%M:%S", time)
                'u' -> time.dayOfWeek.value.toString()
                'w' -> (time.dayOfWeek.value % 7).toString()
                'y' -> two(time.year % 100)
                'Y' -> time.year.toString()
                'z' -> DateTimeFormatter.ofPattern("xx", Locale.ROOT).format(time)
                'Z' -> zoneName ?: DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH).format(time)
                '%' -> "%"
                else -> "%$spec"
            }
        )
    }
    return out.toString()
}

private fun two(value: Int) = value.toString().padStart(2, '0')

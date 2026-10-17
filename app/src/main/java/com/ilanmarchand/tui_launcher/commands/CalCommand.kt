package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// util-linux cal: cal [-m|-s] [[month] year]  One month, Sunday first by default (-m for Monday). A year
// alone prints its twelve months one under the other, since three side by side don't fit the phone.
object CalCommand : BuiltinCommand {
    override val name = "cal"
    override val synopsis = "cal [-m | -s] [[month] year]"
    override val summary = "Display a calendar."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("cal: unexpected EOF while looking for matching quote")
        return cal(words, LocalDate.now())
    }
}

internal fun cal(words: List<String>, today: LocalDate): CommandResult {
    val args = when (val result = parseGnuArgs("cal", words, "ms", long = mapOf("monday" to 'm', "sunday" to 's'))) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("cal"))
        is GnuArgsResult.Parsed -> result.args
    }
    val mondayFirst = 'm' in args.flags && 's' !in args.flags
    val operands = args.operands
    if (operands.size > 2) return errorLines("cal: bad usage", tryHelp("cal"))
    val year = operands.lastOrNull()?.let { text ->
        text.toIntOrNull()?.takeIf { it in 1..9999 } ?: return errorLines("cal: illegal year value: use 1-9999")
    } ?: today.year
    if (operands.size == 1) return textLines((1..12).flatMap { calendarMonth(it, year, mondayFirst) })
    val month = operands.firstOrNull()?.takeIf { operands.size == 2 }?.let { text ->
        parseMonth(text) ?: return errorLines("cal: illegal month value: use 1-12")
    } ?: today.monthValue
    return textLines(calendarMonth(month, year, mondayFirst))
}

// A month number, or an English month name or its start ("oct", "October")
private fun parseMonth(text: String): Int? =
    text.toIntOrNull()?.takeIf { it in 1..12 }
        ?: Month.entries.firstOrNull { text.length >= 3 && it.name.startsWith(text.uppercase()) }?.value

// util-linux cal's month: a title centered over 20 columns, the day names, then always six week rows
internal fun calendarMonth(month: Int, year: Int, mondayFirst: Boolean): List<String> {
    val yearMonth = YearMonth.of(year, month)
    val title = "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} $year"
    val left = (20 - title.length + 1) / 2
    val header = if (mondayFirst) "Mo Tu We Th Fr Sa Su" else "Su Mo Tu We Th Fr Sa"
    val firstDay = yearMonth.atDay(1).dayOfWeek.value
    val offset = if (mondayFirst) firstDay - 1 else firstDay % 7
    val cells = List(offset) { "  " } + (1..yearMonth.lengthOfMonth()).map { it.toString().padStart(2) }
    val weeks = (cells + List(42 - cells.size) { "  " }).chunked(7).map { it.joinToString(" ") }
    return listOf(title.padStart(left + title.length).padEnd(20), header) + weeks
}

package com.ilanmarchand.tui_launcher.commands

import android.content.Context

// GNU sleep: sleep NUMBER[SUFFIX]...  Waits for the sum of the times (s seconds, the default, m minutes,
// h hours, d days; fractions allowed). Like any running command, Back interrupts it, as Ctrl+C would.
object SleepCommand : BuiltinCommand {
    override val name = "sleep"
    override val synopsis = "sleep NUMBER[SUFFIX] ..."
    override val summary = "Delay for a specified amount of time."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("sleep: unexpected EOF while looking for matching quote")
        return when (val duration = sleepDuration(words)) {
            is SleepDuration.Invalid -> duration.output
            is SleepDuration.Millis -> {
                Thread.sleep(duration.millis)
                CommandResult.Output(emptyList())
            }
        }
    }
}

internal sealed interface SleepDuration {
    data class Millis(val millis: Long) : SleepDuration
    data class Invalid(val output: CommandResult) : SleepDuration
}

internal fun sleepDuration(words: List<String>): SleepDuration {
    if (words.isEmpty()) return SleepDuration.Invalid(errorLines("sleep: missing operand", tryHelp("sleep")))
    var total = 0.0
    for (word in words) {
        if (word.startsWith("-") && word.length > 1) {
            return SleepDuration.Invalid(errorLines("sleep: invalid option -- '${word[1]}'", tryHelp("sleep")))
        }
        val match = Regex("""^(\d+(?:\.\d*)?|\.\d+)([smhd]?)$""").matchEntire(word)
            ?: return SleepDuration.Invalid(errorLines("sleep: invalid time interval ‘$word’", tryHelp("sleep")))
        val (number, unit) = match.destructured
        val seconds = when (unit) {
            "m" -> 60.0
            "h" -> 3600.0
            "d" -> 86_400.0
            else -> 1.0
        }
        total += number.toDouble() * seconds
    }
    return SleepDuration.Millis((total * 1000).toLong())
}

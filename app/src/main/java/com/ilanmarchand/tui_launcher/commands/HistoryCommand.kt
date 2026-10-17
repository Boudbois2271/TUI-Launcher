package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.log.readCommandHistory

// bash's history: history [n]  The command log is the history file, so it holds every command ever typed.
// Like bash with its default HISTSIZE, it shows the last 500, numbered for !n. It can't be cleared, since
// the log keeps the user's ideas.
object HistoryCommand : BuiltinCommand {
    override val name = "history"
    override val synopsis = "history [n]"
    override val summary = "Display the command history list."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("history: unexpected EOF while looking for matching quote")
        return history(words, readCommandHistory(context))
    }
}

internal fun history(words: List<String>, entries: List<String>): CommandResult {
    val word = words.firstOrNull()
    if (word == "-c") return errorLines("history: -c is not available: the history is the command log, which is never erased")
    val count = when {
        word == null -> 500
        else -> word.toIntOrNull()?.takeIf { it >= 0 } ?: return errorLines("history: $word: numeric argument required")
    }
    val first = maxOf(entries.size - count, 0)
    return textLines(entries.drop(first).mapIndexed { index, entry -> "${(first + index + 1).toString().padStart(5)}  $entry" })
}

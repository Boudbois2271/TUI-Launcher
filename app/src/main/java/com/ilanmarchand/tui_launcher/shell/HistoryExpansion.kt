package com.ilanmarchand.tui_launcher.shell

sealed interface HistoryExpansion {
    // changed: the line had a ! event, so bash would echo the new line before running it
    data class Expanded(val line: String, val changed: Boolean) : HistoryExpansion
    data class Failed(val message: String) : HistoryExpansion
}

// bash history expansion, done before the line is parsed: !! is the last command, !n the command numbered
// n by `history`, !-n the n-th from the end, !text the last command starting with text. Single quotes and
// a backslash protect a !, and a ! followed by a blank, = or ( stays a plain character, as in bash.
fun expandHistory(line: String, history: List<String>): HistoryExpansion {
    val out = StringBuilder()
    var changed = false
    var inSingle = false
    var inDouble = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        val next = line.getOrNull(i + 1)
        when {
            c == '\\' && !inSingle && next != null -> {
                out.append(c).append(next)
                i += 2
                continue
            }
            c == '\'' && !inDouble -> inSingle = !inSingle
            c == '"' && !inSingle -> inDouble = !inDouble
            c == '!' && !inSingle && next != null && !next.isWhitespace() && next != '=' && next != '(' -> {
                val (event, length) = when {
                    next == '!' -> history.lastOrNull() to 2
                    next.isDigit() || (next == '-' && line.getOrNull(i + 2)?.isDigit() == true) -> {
                        val digits = line.drop(i + 1).takeWhile { it.isDigit() || it == '-' }
                        val number = digits.toIntOrNull()
                        val event = when {
                            number == null -> null
                            number > 0 -> history.getOrNull(number - 1)
                            else -> history.getOrNull(history.size + number)
                        }
                        event to digits.length + 1
                    }
                    else -> {
                        val text = line.drop(i + 1).takeWhile { !it.isWhitespace() && it !in ";&|()<>\"'" }
                        history.lastOrNull { it.startsWith(text) } to text.length + 1
                    }
                }
                if (event == null) return HistoryExpansion.Failed("${line.substring(i, i + length)}: event not found")
                out.append(event)
                changed = true
                i += length
                continue
            }
        }
        out.append(c)
        i++
    }
    return HistoryExpansion.Expanded(out.toString(), changed)
}

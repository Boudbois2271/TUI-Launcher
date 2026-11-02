// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

// bash's echo: echo [-neE] [ARG]...  With -e, \n, \t and \\ mean a new line, a tab and a backslash.
object EchoCommand : TextTool("echo") {
    override val synopsis = "echo [-neE] [arg ...]"
    override val summary = "Write arguments to the standard output."

    override fun process(words: List<String>, input: List<String>, files: FileLines) = echo(words)
}

internal fun echo(words: List<String>): CommandResult {
    var escapes = false
    var index = 0
    while (index < words.size && Regex("-[neE]+").matches(words[index])) {
        for (flag in words[index].drop(1)) {
            if (flag == 'e') escapes = true
            if (flag == 'E') escapes = false
        }
        index++
    }
    val text = words.drop(index).joinToString(" ")
    return textLines((if (escapes) unescape(text) else text).split('\n'))
}

private fun unescape(text: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val next = text.getOrNull(i + 1)
        if (c == '\\' && next != null && next in "ntr\\") {
            out.append(
                when (next) {
                    'n' -> '\n'
                    't' -> '\t'
                    'r' -> '\r'
                    else -> '\\'
                }
            )
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

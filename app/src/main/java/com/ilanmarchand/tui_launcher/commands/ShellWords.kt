package com.ilanmarchand.tui_launcher.commands

// Splits a line into words with bash quoting: blanks separate words, single quotes keep everything
// literal, double quotes keep everything except \" \\ \$ and \`, and a backslash outside quotes keeps
// the next character. Returns null when a quote is not closed.
fun splitShellWords(line: String): List<String>? {
    val words = mutableListOf<String>()
    val word = StringBuilder()
    var inWord = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when (c) {
            ' ', '\t' -> if (inWord) {
                words += word.toString()
                word.clear()
                inWord = false
            }

            '\'' -> {
                val end = line.indexOf('\'', i + 1)
                if (end < 0) return null
                word.append(line, i + 1, end)
                i = end
                inWord = true
            }

            '"' -> {
                inWord = true
                i++
                while (true) {
                    if (i >= line.length) return null
                    val d = line[i]
                    if (d == '"') break
                    if (d == '\\' && i + 1 < line.length && line[i + 1] in "\"\\$`") {
                        word.append(line[i + 1])
                        i += 2
                    } else {
                        word.append(d)
                        i++
                    }
                }
            }

            '\\' -> {
                inWord = true
                if (i + 1 < line.length) {
                    word.append(line[i + 1])
                    i++
                }
            }

            else -> {
                word.append(c)
                inWord = true
            }
        }
        i++
    }
    if (inWord) words += word.toString()
    return words
}

// Quotes a value so splitShellWords reads it back unchanged, the way bash prints aliases
fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

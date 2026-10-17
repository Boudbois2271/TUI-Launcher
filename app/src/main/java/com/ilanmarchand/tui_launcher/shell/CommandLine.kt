package com.ilanmarchand.tui_launcher.shell

import com.ilanmarchand.tui_launcher.commands.splitShellWords

// The operator that joins a pipeline to the one before it: && runs it after a success, || after a failure,
// ; always
enum class Connector { AND, OR, THEN }

// > writes the output to a file, >> adds it at the end, < reads the input from a file; 2> and 2>> do the
// same with the error lines
enum class RedirectKind { WRITE, APPEND, READ, ERRORS_WRITE, ERRORS_APPEND }

// target: the file name as typed, without its quotes
data class Redirect(val kind: RedirectKind, val target: String)

// One command of a pipeline: its raw text (quotes kept, so each command splits its own words) and its
// redirections, in the order they were typed
data class SimpleCommand(val text: String, val redirects: List<Redirect> = emptyList())

// One pipeline of a command list, with the operator before it (null for the first)
data class ListItem(val connector: Connector?, val pipeline: List<SimpleCommand>)

sealed interface ParseResult {
    data class Parsed(val items: List<ListItem>) : ParseResult
    data class SyntaxError(val message: String) : ParseResult
}

// Splits a typed line into a bash command list: pipelines joined by ;, && and ||, commands joined by |,
// each with its redirections (> >> < 2> 2>>). Quotes and backslashes protect these characters, and a #
// that starts a word begins a comment. Background jobs (&) and redirections to another output (2>&1) don't
// exist here, so they are refused instead of being typed as text.
fun parseCommandLine(line: String): ParseResult {
    val items = mutableListOf<ListItem>()
    var pipeline = mutableListOf<SimpleCommand>()
    var connector: Connector? = null
    val current = StringBuilder()
    var redirects = mutableListOf<Redirect>()
    var quote: Char? = null

    fun atWordStart() = current.isEmpty() || current.last().isWhitespace()

    // Ends the current command at an operator; nothing there (not even a redirection) is bash's syntax error
    fun endCommand(token: String): String? {
        val text = current.toString().trim()
        if (text.isEmpty() && redirects.isEmpty()) return "syntax error near unexpected token `$token'"
        pipeline += SimpleCommand(text, redirects)
        current.clear()
        redirects = mutableListOf()
        return null
    }

    fun endPipeline(next: Connector) {
        items += ListItem(connector, pipeline)
        pipeline = mutableListOf()
        connector = next
    }

    var i = 0
    loop@ while (i < line.length) {
        val c = line[i]
        val next = line.getOrNull(i + 1)
        when {
            quote == '\'' -> {
                current.append(c)
                if (c == '\'') quote = null
            }
            quote == '"' -> {
                current.append(c)
                if (c == '\\' && next != null) {
                    current.append(next)
                    i++
                } else if (c == '"') {
                    quote = null
                }
            }
            c == '\\' -> {
                current.append(c)
                if (next != null) {
                    current.append(next)
                    i++
                }
            }
            c == '\'' || c == '"' -> {
                quote = c
                current.append(c)
            }
            c == '#' && atWordStart() -> break@loop
            c == '|' && next == '|' -> {
                endCommand("||")?.let { return ParseResult.SyntaxError(it) }
                endPipeline(Connector.OR)
                i++
            }
            c == '|' -> endCommand("|")?.let { return ParseResult.SyntaxError(it) }
            c == '&' && next == '&' -> {
                endCommand("&&")?.let { return ParseResult.SyntaxError(it) }
                endPipeline(Connector.AND)
                i++
            }
            c == '&' -> return ParseResult.SyntaxError("background jobs (&) are not available here")
            c == ';' && next == ';' -> return ParseResult.SyntaxError("syntax error near unexpected token `;;'")
            c == ';' -> {
                endCommand(";")?.let { return ParseResult.SyntaxError(it) }
                endPipeline(Connector.THEN)
            }
            c == '>' || c == '<' -> {
                // A lone 2 right before > sends the error lines instead
                val errors = c == '>' && current.endsWith("2") && (current.length == 1 || current[current.length - 2].isWhitespace())
                if (errors) current.setLength(current.length - 1)
                var end = i + 1
                val kind = when {
                    c == '<' -> RedirectKind.READ
                    line.getOrNull(end) == '>' -> {
                        end++
                        if (errors) RedirectKind.ERRORS_APPEND else RedirectKind.APPEND
                    }
                    else -> if (errors) RedirectKind.ERRORS_WRITE else RedirectKind.WRITE
                }
                if (line.getOrNull(end) == '&') {
                    return ParseResult.SyntaxError("redirections to another output (>&) are not available here")
                }
                while (end < line.length && line[end].isWhitespace()) end++
                val (word, after) = readWord(line, end)
                if (word.isEmpty()) return ParseResult.SyntaxError("syntax error near unexpected token `${tokenAt(line, after)}'")
                val target = splitShellWords(word)?.firstOrNull()
                    ?: return ParseResult.SyntaxError("unexpected EOF while looking for matching quote")
                redirects += Redirect(kind, target)
                i = after
                continue@loop
            }
            else -> current.append(c)
        }
        i++
    }
    if (quote != null) return ParseResult.SyntaxError("unexpected EOF while looking for matching `$quote'")

    val last = current.toString().trim()
    if (last.isNotEmpty() || redirects.isNotEmpty()) {
        pipeline += SimpleCommand(last, redirects)
    } else if (pipeline.isNotEmpty() || connector == Connector.AND || connector == Connector.OR) {
        // A line ending in |, && or || waits for more in bash; here the line is over
        return ParseResult.SyntaxError("syntax error: unexpected end of file")
    }
    if (pipeline.isNotEmpty()) items += ListItem(connector, pipeline)
    return ParseResult.Parsed(items)
}

// The word starting at `start`, quotes kept, up to a blank or an operator. Returns it with the index after it.
private fun readWord(line: String, start: Int): Pair<String, Int> {
    var i = start
    var quote: Char? = null
    while (i < line.length) {
        val c = line[i]
        when {
            quote != null -> if (c == quote) quote = null
            c == '\\' -> i++
            c == '\'' || c == '"' -> quote = c
            c.isWhitespace() || c in "|;&<>" -> break
        }
        i++
    }
    val end = minOf(i, line.length)
    return line.substring(start, end) to end
}

// The operator at an index, for bash's "unexpected token" message, or "newline" at the end of the line
private fun tokenAt(line: String, index: Int): String {
    if (index >= line.length) return "newline"
    val two = line.substring(index, minOf(index + 2, line.length))
    return if (two in listOf("||", "&&", ">>", ";;")) two else line[index].toString()
}

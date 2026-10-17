package com.ilanmarchand.tui_launcher.shell

// The operator that joins a pipeline to the one before it: && runs it after a success, || after a failure,
// ; always
enum class Connector { AND, OR, THEN }

// One pipeline of a command list, as the raw text of each command (quotes kept, so each command splits
// its own words), with the operator before it (null for the first)
data class ListItem(val connector: Connector?, val pipeline: List<String>)

sealed interface ParseResult {
    data class Parsed(val items: List<ListItem>) : ParseResult
    data class SyntaxError(val message: String) : ParseResult
}

// Splits a typed line into a bash command list: pipelines joined by ;, && and ||, commands joined by |.
// Quotes and backslashes protect these characters, and a # that starts a word begins a comment. Background
// jobs (&) and redirections (> <) don't exist here, so they are refused instead of being typed as text.
fun parseCommandLine(line: String): ParseResult {
    val items = mutableListOf<ListItem>()
    var pipeline = mutableListOf<String>()
    var connector: Connector? = null
    val current = StringBuilder()
    var quote: Char? = null

    fun atWordStart() = current.isEmpty() || current.last().isWhitespace()

    // Ends the current command at an operator; an empty command there is bash's syntax error
    fun endCommand(token: String): String? {
        val text = current.toString().trim()
        if (text.isEmpty()) return "syntax error near unexpected token `$token'"
        pipeline += text
        current.clear()
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
            c == '>' || c == '<' -> return ParseResult.SyntaxError("redirections ($c) are not available here")
            else -> current.append(c)
        }
        i++
    }
    if (quote != null) return ParseResult.SyntaxError("unexpected EOF while looking for matching `$quote'")

    val last = current.toString().trim()
    if (last.isNotEmpty()) {
        pipeline += last
    } else if (pipeline.isNotEmpty() || connector == Connector.AND || connector == Connector.OR) {
        // A line ending in |, && or || waits for more in bash; here the line is over
        return ParseResult.SyntaxError("syntax error: unexpected end of file")
    }
    if (pipeline.isNotEmpty()) items += ListItem(connector, pipeline)
    return ParseResult.Parsed(items)
}

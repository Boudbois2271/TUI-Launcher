package com.ilanmarchand.tui_launcher.shell

import java.io.File
import java.text.Collator
import java.util.Locale

// bash's pathname expansion on a command's raw text: an unquoted word with *, ? or [...] becomes the matching
// file names, sorted, each escaped so the command's word splitting keeps it whole. A word that matches
// nothing stays as typed, a pattern part not starting with a dot skips hidden files, and quoted or
// backslashed characters never match as wildcards. Paths resolve from `cwd`; ~ was already expanded.
fun expandWildcards(text: String, cwd: File): String {
    val out = StringBuilder()
    var i = 0
    while (i < text.length) {
        if (text[i].isWhitespace()) {
            out.append(text[i])
            i++
            continue
        }
        val (raw, end) = rawWord(text, i)
        out.append(globWord(raw, cwd) ?: raw)
        i = end
    }
    return out.toString()
}

// The word starting at `start`, quotes kept, up to an unquoted blank
private fun rawWord(text: String, start: Int): Pair<String, Int> {
    var i = start
    var quote: Char? = null
    while (i < text.length) {
        val c = text[i]
        when {
            quote != null -> if (c == quote) quote = null
            c == '\\' -> i++
            c == '\'' || c == '"' -> quote = c
            c.isWhitespace() -> break
        }
        i++
    }
    val end = minOf(i, text.length)
    return text.substring(start, end) to end
}

// The matches of a raw word, escaped and joined by spaces, or null when it has no unquoted wildcard or no match
private fun globWord(raw: String, cwd: File): String? {
    // The pattern, with each character marked as a live wildcard or a plain character
    val chars = mutableListOf<Pair<Char, Boolean>>()
    var quote: Char? = null
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        when {
            quote != null -> if (c == quote) quote = null else chars += c to false
            c == '\\' && i + 1 < raw.length -> {
                chars += raw[i + 1] to false
                i++
            }
            c == '\'' || c == '"' -> quote = c
            else -> chars += c to (c in "*?[")
        }
        i++
    }
    if (chars.none { it.second }) return null
    val parts = splitParts(chars)
    val absolute = chars.firstOrNull()?.first == '/'
    var bases = listOf(if (absolute) File("/") else cwd)
    var shown = listOf(if (absolute) "/" else "")
    for ((index, part) in parts.withIndex()) {
        val isLast = index == parts.lastIndex
        val nextBases = mutableListOf<File>()
        val nextShown = mutableListOf<String>()
        for ((base, prefix) in bases.zip(shown)) {
            if (part.none { it.second }) {
                val name = part.map { it.first }.joinToString("")
                val file = File(base, name)
                if (file.exists()) {
                    nextBases += file
                    nextShown += prefix + name + if (isLast) "" else "/"
                }
                continue
            }
            val regex = partRegex(part)
            val showHidden = part.firstOrNull()?.first == '.'
            val names = base.list()?.filter { (showHidden || !it.startsWith(".")) && regex.matches(it) }.orEmpty()
            for (name in names.sortedWith(Collator.getInstance(Locale.ROOT))) {
                val file = File(base, name)
                if (!isLast && !file.isDirectory) continue
                nextBases += file
                nextShown += prefix + name + if (isLast) "" else "/"
            }
        }
        bases = nextBases
        shown = nextShown
    }
    if (shown.isEmpty()) return null
    return shown.joinToString(" ") { name -> name.map { if (it.isWhitespace() || it in "\\'\"*?[") "\\$it" else "$it" }.joinToString("") }
}

// The pattern's parts between slashes; empty parts (a leading slash, //) are dropped
private fun splitParts(chars: List<Pair<Char, Boolean>>): List<List<Pair<Char, Boolean>>> {
    val parts = mutableListOf<List<Pair<Char, Boolean>>>()
    var current = mutableListOf<Pair<Char, Boolean>>()
    for (char in chars) {
        if (char.first == '/') {
            if (current.isNotEmpty()) parts += current
            current = mutableListOf()
        } else {
            current += char
        }
    }
    if (current.isNotEmpty()) parts += current
    return parts
}

// A Java regex for one part: * any run, ? one character, [...] a set ([!...] or [^...] negated)
private fun partRegex(part: List<Pair<Char, Boolean>>): Regex {
    val out = StringBuilder()
    var i = 0
    while (i < part.size) {
        val (c, live) = part[i]
        when {
            live && c == '*' -> out.append(".*")
            live && c == '?' -> out.append('.')
            live && c == '[' -> {
                val close = (i + 2 until part.size).firstOrNull { part[it].first == ']' }
                if (close == null) {
                    out.append("\\[")
                } else {
                    val body = part.subList(i + 1, close).map { it.first }.joinToString("")
                    val negated = body.startsWith("!") || body.startsWith("^")
                    val set = (if (negated) body.drop(1) else body).replace("\\", "\\\\").replace("[", "\\[")
                    out.append('[').append(if (negated) "^" else "").append(set).append(']')
                    i = close
                }
            }
            else -> out.append(Regex.escape(c.toString()))
        }
        i++
    }
    return Regex(out.toString())
}

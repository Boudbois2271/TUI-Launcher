package com.ilanmarchand.tui_launcher.shell

// The variables set in the shell (NAME=value), which of them are exported (export, listed by env), and the
// last exit status ($?). They live as long as the launcher runs, like a shell session.
class Variables {
    private val values = linkedMapOf<String, String>()
    private val exported = linkedSetOf<String>()

    @Volatile
    var lastStatus = 0

    @Synchronized
    fun get(name: String): String? = values[name]

    @Synchronized
    fun set(name: String, value: String) {
        values[name] = value
    }

    @Synchronized
    fun export(name: String) {
        exported += name
    }

    @Synchronized
    fun unset(name: String) {
        values.remove(name)
        exported.remove(name)
    }

    // The exported variables; one exported without a value has none, as `export NAME` allows in bash
    @Synchronized
    fun exportedVariables(): Map<String, String?> = exported.associateWith { values[it] }
}

// The launcher's one shell session
object ShellVariables {
    val variables = Variables()
}

// A variable name: a letter or _, then letters, digits or _
fun isVariableName(name: String) = Regex("[A-Za-z_][A-Za-z0-9_]*").matches(name)

// bash's expansions on a command's raw text, outside single quotes: $NAME, ${NAME} and $? become their
// values (an unset name becomes nothing), and a ~ that starts a word becomes $HOME. Values are escaped so the
// command's own word splitting keeps them as plain text: quotes and backslashes in a value stay characters,
// and outside double quotes its blanks still split words, as in bash. With escape off (a redirection's file
// name, which isn't split again), values go in as they are.
fun expandVariables(text: String, escape: Boolean = true, lookup: (String) -> String?): String {
    val out = StringBuilder()
    var inSingle = false
    var inDouble = false
    var i = 0
    fun insert(value: String) {
        val special = if (!escape) "" else if (inDouble) "\\\"$`" else "\\'\""
        value.forEach { if (it in special) out.append('\\'); out.append(it) }
    }
    while (i < text.length) {
        val c = text[i]
        val next = text.getOrNull(i + 1)
        when {
            inSingle -> {
                if (c == '\'') inSingle = false
                out.append(c)
            }
            c == '\\' && next != null -> {
                out.append(c).append(next)
                i += 2
                continue
            }
            c == '\'' && !inDouble -> {
                inSingle = true
                out.append(c)
            }
            c == '"' -> {
                inDouble = !inDouble
                out.append(c)
            }
            c == '~' && !inDouble && (i == 0 || text[i - 1].isWhitespace()) && (next == null || next == '/' || next.isWhitespace()) ->
                insert(lookup("HOME").orEmpty())
            c == '$' && next == '?' -> {
                insert(lookup("?").orEmpty())
                i += 2
                continue
            }
            c == '$' && next == '{' -> {
                val end = text.indexOf('}', i + 2)
                val name = if (end > 0) text.substring(i + 2, end) else ""
                if (end < 0 || !isVariableName(name)) {
                    out.append(c)
                } else {
                    insert(lookup(name).orEmpty())
                    i = end + 1
                    continue
                }
            }
            c == '$' && next != null && (next.isLetter() || next == '_') -> {
                val name = text.drop(i + 1).takeWhile { it.isLetterOrDigit() || it == '_' }
                insert(lookup(name).orEmpty())
                i += name.length + 1
                continue
            }
            else -> out.append(c)
        }
        i++
    }
    return out.toString()
}

// NAME=value words, when they make up the whole command, as in bash; null when the command is something else
fun assignmentsIn(words: List<String>): List<Pair<String, String>>? {
    if (words.isEmpty()) return null
    val pairs = words.map { word ->
        val name = word.substringBefore('=', "")
        if (!isVariableName(name)) return null
        name to word.substringAfter('=')
    }
    return pairs
}

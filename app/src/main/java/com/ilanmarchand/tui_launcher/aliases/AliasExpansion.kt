package com.ilanmarchand.tui_launcher.aliases

// Alias expansion as in bash: the first word of the line is replaced by its alias, then the first word
// of the result, and so on. A word is not expanded twice, so `alias a=b` with `alias b=a` stops instead
// of looping. When an alias value ends with a blank, the next word is checked for an alias too.
fun expandAliases(line: String, aliases: Map<String, String>): String {
    val result = StringBuilder()
    var rest = line
    while (true) {
        val word = rest.substringBefore(' ')
        var head = word
        var checkNext = false
        val seen = mutableSetOf<String>()
        while (true) {
            val first = head.substringBefore(' ')
            val value = aliases[first] ?: break
            if (!seen.add(first)) break
            if (value.endsWith(' ') || value.endsWith('\t')) checkNext = true
            head = value + head.substring(first.length)
        }
        result.append(head)
        rest = rest.substring(word.length)
        if (!checkNext) return result.append(rest).toString()
        // One blank between the words, as bash would read them
        rest = rest.trimStart(' ')
        if (rest.isEmpty()) return result.toString()
        if (!head.endsWith(' ') && !head.endsWith('\t')) result.append(' ')
    }
}

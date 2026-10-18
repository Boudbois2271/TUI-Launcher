package com.ilanmarchand.tui_launcher.completion

// What Tab does to the line, as bash does with its default completion
sealed interface Completion {
    // Replaces the whole line with this text
    data class Insert(val line: String) : Completion

    // Nothing more can be filled in: these names match, listed on the second Tab
    data class Ambiguous(val names: List<String>) : Completion

    data object None : Completion
}

// A file or folder in a folder, as the completer sees it
data class FileEntry(val name: String, val isDirectory: Boolean)

// One thing the last word can become. text is what is compared and listed, suffix is added after a
// unique match: a space after a command or file, a slash after a folder.
private data class Candidate(val text: String, val suffix: String) {
    val listed get() = text + if (suffix == "/") "/" else ""
}

// Completes the word the line ends with, as bash does. commandNames: every name a command can start with
// (built-ins, aliases, app labels). listFiles: the entries of a folder for a word after the command, or null
// when it cannot be read. Matching ignores case, as the launcher's command names do.
fun complete(
    line: String,
    commandNames: List<String>,
    listFiles: (folder: String) -> List<FileEntry>?
): Completion {
    // The command being typed starts after the last pipe, ; or &, as in `ls | gr`
    val start = line.indexOfLast { it == '|' || it == ';' || it == '&' } + 1
    val segment = line.substring(start).trimStart(' ')
    val commandPosition = ' ' !in segment || commandNames.any { ' ' in it && it.startsWith(segment, ignoreCase = true) }
    val word = if (commandPosition) segment else segment.substringAfterLast(' ')
    val head = line.dropLast(word.length)

    val candidates = if (commandPosition) {
        commandNames.filter { it.startsWith(word, ignoreCase = true) }.distinct().sorted()
            .map { Candidate(it, " ") }
    } else {
        fileCandidates(word, listFiles)
    }
    return when {
        candidates.isEmpty() -> Completion.None
        candidates.size == 1 -> Completion.Insert(head + candidates[0].text + candidates[0].suffix)
        else -> {
            val common = commonPrefix(candidates.map { it.text })
            if (common.length > word.length) {
                Completion.Insert(head + common)
            } else {
                Completion.Ambiguous(candidates.map { it.listed })
            }
        }
    }
}

// A word after the command names a file: a folder's entries, with the folder part kept as typed (ls docs/a)
private fun fileCandidates(word: String, listFiles: (String) -> List<FileEntry>?): List<Candidate> {
    val folder = if ('/' in word) word.substringBeforeLast('/') + "/" else ""
    val name = word.substringAfterLast('/')
    val entries = listFiles(folder.ifEmpty { "." }) ?: return emptyList()
    return entries
        // Hidden entries are offered only when the word starts with a dot, as in bash
        .filter { it.name.startsWith(name, ignoreCase = true) && (!it.name.startsWith('.') || name.startsWith('.')) }
        .sortedBy { it.name.lowercase() }
        .map { Candidate(folder + it.name, if (it.isDirectory) "/" else " ") }
}

// The longest start that all the texts share, ignoring case
private fun commonPrefix(texts: List<String>): String =
    texts.reduce { common, text ->
        common.take(common.zip(text).takeWhile { (a, b) -> a.equals(b, ignoreCase = true) }.size)
    }

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import com.ilanmarchand.tui_launcher.files.Workspace

// What reading a file named on a command line gives: its lines, or why it can't be read, in the words GNU
// tools use ("No such file or directory", "Is a directory", "Permission denied")
sealed interface FileRead {
    data class Lines(val lines: List<String>) : FileRead
    data class Failed(val reason: String) : FileRead
}

// How the text tools read their file operands; tests give a fake
fun interface FileLines {
    fun read(name: String): FileRead
}

// Reads through the workspace, so names resolve like the file commands' (~, relative to the working directory)
class WorkspaceFileLines(private val workspace: Workspace) : FileLines {
    override fun read(name: String): FileRead {
        val file = workspace.resolve(name)
        return when {
            !file.exists() -> FileRead.Failed("No such file or directory")
            file.isDirectory -> FileRead.Failed("Is a directory")
            else -> runCatching { FileRead.Lines(file.readLines()) }.getOrElse { FileRead.Failed("Permission denied") }
        }
    }
}

// No files at all
object NoFiles : FileLines {
    override fun read(name: String) = FileRead.Failed("No such file or directory")
}

// One input of a text tool: a file, or the piped-in text (name null), as `-` or when no file is named
internal class NamedInput(val name: String?, val lines: List<String>)

// Goes through the inputs for file operands, in order: the pipe when there are none, or for "-". A file that
// can't be read goes to onError, and the next one is read, as GNU tools do.
internal fun forEachInput(
    operands: List<String>,
    stdin: List<String>,
    files: FileLines,
    onError: (name: String, reason: String) -> Unit,
    onInput: (NamedInput) -> Unit
) {
    if (operands.isEmpty()) return onInput(NamedInput(null, stdin))
    for (name in operands) {
        if (name == "-") {
            onInput(NamedInput(null, stdin))
            continue
        }
        when (val read = files.read(name)) {
            is FileRead.Lines -> onInput(NamedInput(name, read.lines))
            is FileRead.Failed -> onError(name, read.reason)
        }
    }
}

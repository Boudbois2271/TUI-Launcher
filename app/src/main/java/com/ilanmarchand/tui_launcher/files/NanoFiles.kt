// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.files

import java.io.File

// Writes a whole file for nano, which ends a file with a newline like nano does. Returns null once written,
// else the reason nano shows ("Permission denied"); the command log can't be written.
fun writeWholeFile(workspace: Workspace, path: String, text: String): String? {
    val file = File(path)
    return when {
        workspace.isProtected(file) -> "Operation not permitted"
        file.isDirectory -> "Is a directory"
        file.parentFile?.isDirectory != true -> "No such file or directory"
        else -> runCatching { file.writeText(if (text.isEmpty() || text.endsWith("\n")) text else "$text\n") }
            .exceptionOrNull()?.let { "Permission denied" }
    }
}

// How many lines nano counts in a text: a final newline doesn't start one more
fun nanoLineCount(text: String): Int = if (text.isEmpty()) 0 else text.removeSuffix("\n").split('\n').size

// nano's "[ Read 3 lines ]", "[ Wrote 1 line ]"
fun nanoLines(verb: String, count: Int) = "[ $verb $count line${if (count == 1) "" else "s"} ]"

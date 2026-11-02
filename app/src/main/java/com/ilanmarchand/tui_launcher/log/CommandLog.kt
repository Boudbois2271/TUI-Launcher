// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.log

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val LOG_FILE_NAME = "terminal-log.txt"
private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

// The file is in the app's external files folder, so it can be pulled over adb without any storage permission
fun commandLogFile(context: Context): File {
    val folder = context.getExternalFilesDir(null) ?: context.filesDir
    return File(folder, LOG_FILE_NAME)
}

// Appends one timestamped line per submitted command
fun appendToCommandLog(context: Context, command: String) {
    commandLogFile(context).appendText("${LocalDateTime.now().format(TIMESTAMP)}  $command\n")
}

// Returns the last entries of the log, oldest first
fun readLastCommandLogLines(context: Context, count: Int): List<String> {
    val file = commandLogFile(context)
    if (!file.exists()) return emptyList()
    return file.readLines().takeLast(count)
}

// The logged commands without their timestamps, oldest first: the shell's history, like ~/.bash_history
fun readCommandHistory(context: Context): List<String> {
    val file = commandLogFile(context)
    if (!file.exists()) return emptyList()
    return file.readLines().map(::stripTimestamp)
}

// "2026-10-17 12:16:46  unown" becomes "unown"
internal fun stripTimestamp(line: String): String =
    if (line.length >= 21 && line[4] == '-' && line[19] == ' ' && line[20] == ' ') line.drop(21) else line

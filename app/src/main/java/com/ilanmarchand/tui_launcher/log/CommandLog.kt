package com.ilanmarchand.tui_launcher.log

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val LOG_FILE_NAME = "terminal-log.txt"
private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

// The file is in the app's external files folder, so it can be pulled over adb without any storage permission
private fun commandLogFile(context: Context): File {
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

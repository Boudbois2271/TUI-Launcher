package com.ilanmarchand.tui_launcher.log

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val LOG_FILE_NAME = "terminal-log.txt"
private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

// Appends one timestamped line per submitted command. The file is in the app's external files
// folder, so it can be pulled over adb without any storage permission.
fun appendToCommandLog(context: Context, command: String) {
    val folder = context.getExternalFilesDir(null) ?: context.filesDir
    File(folder, LOG_FILE_NAME).appendText("${LocalDateTime.now().format(TIMESTAMP)}  $command\n")
}

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.system.HOST_NAME

// hostname: prints the host name, the one in the prompt. Setting it needs root, so it is refused.
object HostnameCommand : BuiltinCommand {
    override val name = "hostname"
    override val synopsis = "hostname [-s | -f]"
    override val summary = "Show the system's host name."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("hostname: unexpected EOF while looking for matching quote")
        if (words.any { !it.startsWith("-") }) return errorLines("hostname: you must be root to change the host name")
        return textLines(listOf(HOST_NAME))
    }
}

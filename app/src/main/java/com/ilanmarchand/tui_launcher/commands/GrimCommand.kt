package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.accessibility.LockService

// grim, the Wayland screenshot tool: takes a screenshot of the whole screen through the launcher's
// accessibility service. Android saves it in Pictures/Screenshots, so an output file can't be chosen.
object GrimCommand : BuiltinCommand {
    override val name = "grim"
    override val synopsis = "grim"
    override val summary = "Take a screenshot of the whole screen."
    override val takesArgs = true

    private val USAGE = listOf(
        "Usage: grim [options...] [output-file]",
        "",
        "  -h              Show help message and quit.",
        "",
        "On this phone, Android saves the screenshot in Pictures/Screenshots."
    )

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("grim: unexpected EOF while looking for matching quote")
        if ("-h" in words) return textLines(USAGE)
        if (words.isNotEmpty()) {
            return errorLines("grim: '${words.first()}' is not available on this phone: Android saves the screenshot in Pictures/Screenshots")
        }
        if (!LockService.takeScreenshot()) {
            return errorLines("grim: turn on \"TUI-Launcher system actions\" in the Accessibility settings")
        }
        return CommandResult.Output(emptyList())
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

// xdg-open: opens a URL in the app Android picks for it, such as a web page in the browser, mailto: in the
// mail app, tel: in the dialer or geo: in the maps app. The launcher has no files yet, so a file name gets
// xdg-open's "does not exist". Quote a URL with & in it, as in bash. Texts follow xdg-open 1.2.1.
object XdgOpenCommand : BuiltinCommand {
    override val name = "xdg-open"
    override val synopsis = "xdg-open { file | URL }"
    override val summary = "Open a URL in the user's preferred application."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("xdg-open: unexpected EOF while looking for matching quote")
        return when (val target = xdgOpenTarget(words)) {
            is XdgOpenTarget.Show -> target.output
            is XdgOpenTarget.Url -> try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                CommandResult.Output(emptyList())
            } catch (e: ActivityNotFoundException) {
                errorLines("xdg-open: no method available for opening '${target.url}'", exitCode = 3)
            }
        }
    }
}

internal sealed interface XdgOpenTarget {
    data class Url(val url: String) : XdgOpenTarget
    data class Show(val output: CommandResult) : XdgOpenTarget
}

private val XDG_OPEN_USAGE = listOf(
    "   xdg-open -- opens a file or URL in the user's preferred",
    "   application",
    "",
    "Synopsis",
    "",
    "   xdg-open { file | URL }",
    "",
    "   xdg-open { --help | --manual | --version }",
    "",
    "Use 'man xdg-open' or 'xdg-open --manual' for additional info."
)

internal fun xdgOpenTarget(words: List<String>): XdgOpenTarget {
    val target = words.firstOrNull() ?: return XdgOpenTarget.Show(textLines(XDG_OPEN_USAGE, exitCode = 1))
    when (target) {
        "--help" -> return XdgOpenTarget.Show(textLines(XDG_OPEN_USAGE))
        "--version" -> return XdgOpenTarget.Show(textLines(listOf("xdg-open 1.2.1")))
        "--manual" -> return XdgOpenTarget.Show(errorLines("xdg-open: '--manual' is not available on this phone"))
    }
    if (words.size > 1) return XdgOpenTarget.Show(errorLines("xdg-open: unexpected argument '${words[1]}'", tryHelp("xdg-open")))
    // A URL starts with a scheme, as in https:, mailto: or tel:
    if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(target)) return XdgOpenTarget.Url(target)
    return XdgOpenTarget.Show(errorLines("xdg-open: file '$target' does not exist", exitCode = 2))
}

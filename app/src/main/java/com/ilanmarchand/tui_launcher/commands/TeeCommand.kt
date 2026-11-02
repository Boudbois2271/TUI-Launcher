// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.files.CurrentWorkspace
import com.ilanmarchand.tui_launcher.files.WorkspaceRedirects
import com.ilanmarchand.tui_launcher.shell.RedirectException
import com.ilanmarchand.tui_launcher.shell.RedirectFiles

// GNU tee: tee [-a] FILE...  Copies the piped-in text to each file and passes it on, as in
// `nmcli d w l | tee wifi.txt | grep -i box`. -a adds to the files instead of replacing them.
object TeeCommand : BuiltinCommand {
    override val name = "tee"
    override val synopsis = "tee [-a] FILE ..."
    override val summary = "Read from standard input and write to standard output and files."
    override val takesArgs = true

    override fun run(context: Context, args: String) = run(context, args, null)

    override fun run(context: Context, args: String, stdin: List<String>?): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("tee: unexpected EOF while looking for matching quote")
        return tee(words, stdin.orEmpty(), WorkspaceRedirects(CurrentWorkspace.get(context)))
    }
}

internal fun tee(words: List<String>, input: List<String>, files: RedirectFiles): CommandResult {
    val args = when (val result = parseGnuArgs("tee", words, "a", long = mapOf("append" to 'a'))) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("tee"))
        is GnuArgsResult.Parsed -> result.args
    }
    val errors = mutableListOf<OutputLine>()
    for (name in args.operands) {
        try {
            files.write(name, input, append = 'a' in args.flags)
        } catch (e: RedirectException) {
            errors += OutputLine("tee: ${e.message}", isError = true)
        }
    }
    return CommandResult.Output(errors + input.map { OutputLine(it) })
}

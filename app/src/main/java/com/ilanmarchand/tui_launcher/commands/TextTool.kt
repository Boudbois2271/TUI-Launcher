package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.files.CurrentWorkspace

// A command that reads text: the files named on its command line, or else the output of the command before
// it in a pipeline. With nothing piped in it reads nothing, as if given </dev/null (a real terminal would
// wait for typed input instead).
abstract class TextTool(override val name: String) : BuiltinCommand {
    override val takesArgs = true

    override fun run(context: Context, args: String) = run(context, args, null)

    override fun run(context: Context, args: String, stdin: List<String>?): CommandResult {
        val words = splitShellWords(args)
            ?: return errorLines("$name: unexpected EOF while looking for matching quote")
        return process(words, stdin.orEmpty(), WorkspaceFileLines(CurrentWorkspace.get(context)))
    }

    abstract fun process(words: List<String>, input: List<String>, files: FileLines): CommandResult
}

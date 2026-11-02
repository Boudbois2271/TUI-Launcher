// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.files.CurrentWorkspace
import com.ilanmarchand.tui_launcher.files.Workspace

// GNU nano: nano FILE  Opens the file in the editor screen, or a new one if it doesn't exist yet. The editor's
// actions are buttons, since the launcher has no keyboard shortcuts. A file name is needed, since there is no
// "File Name to Write" prompt here.
object NanoCommand : BuiltinCommand {
    override val name = "nano"
    override val synopsis = "nano FILE"
    override val summary = "Edit a text file in a small editor."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("nano: unexpected EOF while looking for matching quote")
        return nano(words, CurrentWorkspace.get(context))
    }
}

internal fun nano(words: List<String>, workspace: Workspace): CommandResult {
    val operands = words.filterNot { it.startsWith("-") || it.startsWith("+") }
    val name = operands.firstOrNull() ?: return errorLines("nano: a file name is needed on this phone, as in: nano notes.md")
    if (operands.size > 1) return errorLines("nano: one file at a time on this phone")
    val file = workspace.resolve(name)
    if (file.isDirectory) return errorLines("nano: \"$name\" is a directory")
    if (!file.exists()) return CommandResult.Edit(file.path, name, "", isNew = true)
    val text = runCatching { file.readText() }.getOrElse { return errorLines("nano: \"$name\": Permission denied") }
    return CommandResult.Edit(file.path, name, text, isNew = false)
}

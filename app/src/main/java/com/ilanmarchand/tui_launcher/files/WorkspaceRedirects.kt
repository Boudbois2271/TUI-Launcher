package com.ilanmarchand.tui_launcher.files

import com.ilanmarchand.tui_launcher.shell.RedirectException
import com.ilanmarchand.tui_launcher.shell.RedirectFiles

// Redirections on the workspace: paths resolve like the file commands' (~, relative to the working
// directory), the command log can't be written, and errors read like bash's ("notes: Is a directory").
class WorkspaceRedirects(private val workspace: Workspace) : RedirectFiles {
    override fun read(target: String): List<String> {
        val file = workspace.resolve(target)
        if (!file.exists()) fail(target, "No such file or directory")
        if (file.isDirectory) fail(target, "Is a directory")
        return runCatching { file.readLines() }.getOrElse { fail(target, "Permission denied") }
    }

    override fun write(target: String, lines: List<String>, append: Boolean) {
        val file = workspace.resolve(target)
        if (workspace.isProtected(file)) fail(target, "Operation not permitted")
        if (file.isDirectory) fail(target, "Is a directory")
        if (file.parentFile?.isDirectory != true) fail(target, "No such file or directory")
        val text = lines.joinToString("") { "$it\n" }
        runCatching { if (append) file.appendText(text) else file.writeText(text) }
            .getOrElse { fail(target, "Permission denied") }
    }

    private fun fail(target: String, reason: String): Nothing = throw RedirectException("$target: $reason")
}

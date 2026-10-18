package com.ilanmarchand.tui_launcher.terminal

import android.content.Context
import com.ilanmarchand.tui_launcher.aliases.AliasStore
import com.ilanmarchand.tui_launcher.apps.LaunchableApp
import com.ilanmarchand.tui_launcher.commands.builtinCommands
import com.ilanmarchand.tui_launcher.completion.FileEntry
import com.ilanmarchand.tui_launcher.files.CurrentWorkspace

// Every name a command can start with: built-ins, aliases, and app labels. Reads the alias preferences,
// so call it off the main thread.
internal fun commandNames(context: Context, apps: List<LaunchableApp>): List<String> =
    builtinCommands.map { it.name } + AliasStore(context).all().keys + apps.map { it.label }

// The entries of a folder, as the shell's working folder sees it. Null when the folder cannot be read.
internal fun folderEntries(context: Context, folder: String): List<FileEntry>? =
    CurrentWorkspace.get(context).resolve(folder).listFiles()?.map { FileEntry(it.name, it.isDirectory) }

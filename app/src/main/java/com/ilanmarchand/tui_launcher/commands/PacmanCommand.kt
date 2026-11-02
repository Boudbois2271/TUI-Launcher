// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.apps.InstalledPackage
import com.ilanmarchand.tui_launcher.apps.installedPackages
import com.ilanmarchand.tui_launcher.apps.openPlayStore
import com.ilanmarchand.tui_launcher.apps.uninstallPackage
import java.net.URLEncoder

// pacman, Arch Linux's package manager, for Android apps. A package is its Android package name
// (com.instagram.android) or its app name (instagram).
//   pacman -Q [package...]        installed packages and their versions; -Qs QUERY searches them
//   sudo pacman -R package...     uninstalls after pacman's [Y/n] (or --noconfirm), silently as device owner
//   sudo pacman -S package        opens the app's Play Store page: the launcher has no network, so installs
//                                 come from the Play Store
//   pacman -Ss QUERY              searches the Play Store
// Like pacman, -R and -S need root: use sudo. Messages follow pacman.
object PacmanCommand : RootCommand {
    override val name = "pacman"
    override val synopsis = "pacman -Q | -Qs QUERY | -R PACKAGE... | -S PACKAGE | -Ss QUERY"
    override val summary = "Manage apps, like Arch Linux's package manager."
    override val takesArgs = true

    override fun run(context: Context, args: String) = runAs(context, args, root = false)

    override fun runAsRoot(context: Context, args: String) = runAs(context, args, root = true)

    private fun runAs(context: Context, args: String, root: Boolean): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("pacman: unexpected EOF while looking for matching quote")
        val actions = PacmanActions(
            self = context.packageName,
            remove = { uninstallPackage(context, it) },
            openStore = { openPlayStore(context, it) }
        )
        return pacman(words, root, installedPackages(context), actions)
    }
}

// What pacman does to the phone; replaced in tests
internal class PacmanActions(
    val self: String,
    val remove: (String) -> String?,
    val openStore: (String) -> Boolean
)

private val PACMAN_USAGE = listOf(
    "usage:  pacman <operation> [...]",
    "operations:",
    "    pacman {-h --help}",
    "    pacman {-Q --query}    [options] [package(s)]",
    "    pacman {-R --remove}   [options] <package(s)>",
    "    pacman {-S --sync}     [options] [package(s)]",
    "",
    "use 'pacman {-h --help}' with an operation for available options"
)

private val PACMAN_LONG = mapOf("--query" to 'Q', "--remove" to 'R', "--sync" to 'S', "--search" to 's')

private const val NOT_ROOT = "error: you cannot perform this operation unless you are root."

internal fun pacman(words: List<String>, root: Boolean, packages: List<InstalledPackage>, actions: PacmanActions): CommandResult {
    var operation: Char? = null
    val modifiers = mutableSetOf<Char>()
    var noConfirm = false
    val targets = mutableListOf<String>()
    for (word in words) {
        val letters = when {
            word == "--noconfirm" -> {
                noConfirm = true
                continue
            }
            word == "--help" -> "h"
            word.startsWith("--") -> PACMAN_LONG[word]?.toString() ?: return errorLines("pacman: unrecognized option '$word'")
            word.startsWith("-") && word.length > 1 -> word.drop(1)
            else -> {
                targets += word
                continue
            }
        }
        for (letter in letters) {
            when {
                letter in "DFQRSTUV" -> {
                    if (operation != null && operation != letter) return errorLines("error: only one operation may be used at a time")
                    operation = letter
                }
                letter in "hsnuyicd" -> modifiers += letter
                else -> return errorLines("pacman: invalid option -- '$letter'")
            }
        }
    }
    if (operation == null) {
        return if ('h' in modifiers) textLines(PACMAN_USAGE) else errorLines("error: no operation specified (use -h for help)")
    }
    return when (operation) {
        'Q' -> pacmanQuery(modifiers, targets, packages)
        'R' -> pacmanRemove(root, noConfirm, targets, packages, actions)
        'S' -> pacmanSync(root, modifiers, targets, packages, actions)
        else -> errorLines("error: 'pacman -$operation' is not available on this phone")
    }
}

// A package by its package name, else by its app name
private fun List<InstalledPackage>.resolve(target: String) =
    firstOrNull { it.name == target } ?: firstOrNull { it.label.equals(target, ignoreCase = true) }

private fun pacmanQuery(modifiers: Set<Char>, targets: List<String>, packages: List<InstalledPackage>): CommandResult {
    if ('s' in modifiers) {
        val query = targets.joinToString(" ")
        val found = packages.filter {
            it.name.contains(query, ignoreCase = true) || it.label?.contains(query, ignoreCase = true) == true
        }
        return textLines(found.flatMap { listOf("local/${it.name} ${it.version}", "    ${it.label.orEmpty()}") }, if (found.isEmpty()) 1 else 0)
    }
    if (targets.isEmpty()) return textLines(packages.map { "${it.name} ${it.version}" })
    val lines = targets.map { target ->
        packages.resolve(target)?.let { OutputLine("${it.name} ${it.version}") }
            ?: OutputLine("error: package '$target' was not found", isError = true)
    }
    return CommandResult.Output(lines)
}

private fun pacmanRemove(
    root: Boolean,
    noConfirm: Boolean,
    targets: List<String>,
    packages: List<InstalledPackage>,
    actions: PacmanActions
): CommandResult {
    if (!root) return errorLines(NOT_ROOT)
    if (targets.isEmpty()) return errorLines("error: no targets specified (use -h for help)")
    val chosen = targets.map { packages.resolve(it) ?: return errorLines("error: target not found: $it") }.distinct()
    chosen.firstOrNull { it.name == actions.self }?.let { return errorLines("error: '${it.name}' is this launcher: it can't remove itself") }
    chosen.firstOrNull { it.isSystem }?.let { return errorLines("error: '${it.name}' is part of the system: Android can't uninstall it") }
    val summary = listOf(
        "checking dependencies...",
        "",
        "Packages (${chosen.size}) ${chosen.joinToString("  ") { "${it.name}-${it.version}" }}",
        ""
    ).map { OutputLine(it) }
    val removeAll = {
        val lines = mutableListOf(OutputLine(":: Processing package changes..."))
        chosen.forEachIndexed { index, target ->
            lines += OutputLine("(${index + 1}/${chosen.size}) removing ${target.name}")
            actions.remove(target.name)?.let { lines += OutputLine("error: could not remove '${target.name}': $it", isError = true) }
        }
        CommandResult.Output(lines)
    }
    if (noConfirm) return removeAll().let { CommandResult.Output(summary + it.lines, it.exitCode) }
    return CommandResult.Ask(summary, ":: Do you want to remove these packages? [Y/n]") { answer ->
        if (answer.isEmpty() || answer.equals("y", ignoreCase = true) || answer.equals("yes", ignoreCase = true)) {
            removeAll()
        } else {
            CommandResult.Output(emptyList(), exitCode = 1)
        }
    }
}

private fun pacmanSync(
    root: Boolean,
    modifiers: Set<Char>,
    targets: List<String>,
    packages: List<InstalledPackage>,
    actions: PacmanActions
): CommandResult {
    if ('s' in modifiers) {
        if (targets.isEmpty()) return errorLines("error: no targets specified (use -h for help)")
        val query = targets.joinToString(" ")
        return if (actions.openStore("market://search?q=${URLEncoder.encode(query, "UTF-8")}&c=apps")) {
            textLines(listOf(":: Searching the Play Store for '$query'..."))
        } else {
            errorLines("error: the Play Store isn't installed")
        }
    }
    if (!root) return errorLines(NOT_ROOT)
    if (targets.isEmpty()) {
        if ('y' in modifiers || 'u' in modifiers) return errorLines("error: 'pacman -Syu' is not available: app updates come from the Play Store")
        return errorLines("error: no targets specified (use -h for help)")
    }
    if (targets.size > 1) return errorLines("error: install one package at a time on this phone")
    val target = targets.single()
    val lines = mutableListOf<OutputLine>()
    packages.resolve(target)?.let { lines += OutputLine("warning: ${it.name}-${it.version} is up to date -- reinstalling") }
    // A package name opens its page; anything else is searched
    val uri = if (Regex("^[a-zA-Z][\\w]*(\\.[a-zA-Z][\\w]*)+$").matches(target)) {
        "market://details?id=$target"
    } else {
        "market://search?q=${URLEncoder.encode(target, "UTF-8")}&c=apps"
    }
    if (!actions.openStore(uri)) return errorLines("error: the Play Store isn't installed")
    lines += OutputLine(":: Opening the Play Store for '$target'...")
    return CommandResult.Output(lines)
}

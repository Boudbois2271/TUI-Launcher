// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.aliases.AliasStore
import com.ilanmarchand.tui_launcher.apps.LaunchableApp
import com.ilanmarchand.tui_launcher.apps.loadLaunchableApps

// bash's compgen, for the names a line can start with: -a aliases, -b built-in commands, -c every command
// (aliases, built-ins and app names). An optional word keeps only the names that start with it.
object CompgenCommand : BuiltinCommand {
    override val name = "compgen"
    override val synopsis = "compgen [-abc] [word]"
    override val summary = "Display possible completions depending on the options."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args)
            ?: return errorLines("compgen: unexpected EOF while looking for matching quote")
        return compgen(words, AliasStore(context).all(), builtinCommands, loadLaunchableApps(context))
    }
}

internal fun compgen(
    words: List<String>,
    aliases: Map<String, String>,
    commands: List<BuiltinCommand>,
    apps: List<LaunchableApp>
): CommandResult {
    val actions = mutableSetOf<Char>()
    val operands = mutableListOf<String>()
    for (word in words) {
        if (word.startsWith("-") && word.length > 1) {
            for (letter in word.drop(1)) {
                if (letter !in "abc") {
                    return errorLines(
                        "compgen: -$letter: invalid option",
                        "compgen: usage: compgen [-abc] [word]",
                        exitCode = 2
                    )
                }
                actions += letter
            }
        } else {
            operands += word
        }
    }
    val prefix = operands.firstOrNull().orEmpty()
    val names = linkedSetOf<String>()
    if ('a' in actions || 'c' in actions) names += aliases.keys.sorted()
    if ('b' in actions || 'c' in actions) names += commands.map { it.name }.sorted()
    if ('c' in actions) names += apps.map { it.label }.sortedBy { it.lowercase() }
    val matching = names.filter { it.startsWith(prefix, ignoreCase = true) }
    return textLines(matching, if (matching.isEmpty()) 1 else 0)
}

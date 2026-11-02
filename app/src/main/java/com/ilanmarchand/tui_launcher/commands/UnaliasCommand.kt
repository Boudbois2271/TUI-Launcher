// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.aliases.AliasStore

// bash's unalias: `unalias [-a] name [name ...]`. -a removes every alias.
object UnaliasCommand : BuiltinCommand {
    override val name = "unalias"
    override val synopsis = "unalias [-a] name [name ...]"
    override val summary = "Remove each name from the list of defined aliases."
    override val takesArgs = true

    private const val USAGE = "unalias: usage: unalias [-a] name [name ...]"

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return CommandResult.Output(
            listOf(OutputLine("unalias: unexpected EOF while looking for matching quote", isError = true))
        )
        val store = AliasStore(context)
        var removeAll = false
        var index = 0
        while (index < words.size && words[index].length > 1 && words[index].startsWith("-")) {
            val option = words[index++]
            if (option == "--") break
            for (letter in option.drop(1)) {
                if (letter != 'a') {
                    return CommandResult.Output(
                        listOf(
                            OutputLine("unalias: -$letter: invalid option", isError = true),
                            OutputLine(USAGE, isError = true)
                        )
                    )
                }
                removeAll = true
            }
        }
        if (removeAll) {
            store.removeAll()
            return CommandResult.Output(emptyList())
        }
        val names = words.drop(index)
        if (names.isEmpty()) return CommandResult.Output(listOf(OutputLine(USAGE, isError = true)))
        return CommandResult.Output(
            names.filterNot { store.remove(it) }.map { OutputLine("unalias: $it: not found", isError = true) }
        )
    }
}

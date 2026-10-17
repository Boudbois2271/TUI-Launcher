package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.aliases.AliasStore

// bash's alias: `alias [-p] [name[=value] ...]`. With no arguments or -p it lists every alias.
// name=value defines one, a bare name prints it. Values are used as typed, nothing is checked.
object AliasCommand : BuiltinCommand {
    override val name = "alias"
    override val takesArgs = true

    private const val USAGE = "alias: usage: alias [-p] [name[=value] ... ]"

    // Characters bash refuses in an alias name
    private const val FORBIDDEN = "()<>;&| \t\n\"'`\\$/"

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return CommandResult.Output(
            listOf(OutputLine("alias: unexpected EOF while looking for matching quote", isError = true))
        )
        val store = AliasStore(context)
        val output = mutableListOf<OutputLine>()
        var printAll = words.isEmpty()
        var index = 0
        while (index < words.size && words[index].length > 1 && words[index].startsWith("-")) {
            val option = words[index++]
            if (option == "--") break
            for (letter in option.drop(1)) {
                if (letter != 'p') {
                    return CommandResult.Output(
                        listOf(
                            OutputLine("alias: -$letter: invalid option", isError = true),
                            OutputLine(USAGE, isError = true)
                        )
                    )
                }
                printAll = true
            }
        }
        if (printAll) {
            store.all().toSortedMap().forEach { (name, value) -> output += OutputLine(format(name, value)) }
        }
        for (word in words.drop(index)) {
            val equals = word.indexOf('=')
            if (equals <= 0) {
                val value = store.get(word)
                output += if (value != null) {
                    OutputLine(format(word, value))
                } else {
                    OutputLine("alias: $word: not found", isError = true)
                }
                continue
            }
            val name = word.substring(0, equals)
            if (name.any { it in FORBIDDEN }) {
                output += OutputLine("alias: `$name': invalid alias name", isError = true)
            } else {
                store.set(name, word.substring(equals + 1))
            }
        }
        return CommandResult.Output(output)
    }

    private fun format(name: String, value: String) = "alias $name=${shellQuote(value)}"
}

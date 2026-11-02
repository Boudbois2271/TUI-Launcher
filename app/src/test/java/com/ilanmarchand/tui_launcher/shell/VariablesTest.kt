// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.shell

import com.ilanmarchand.tui_launcher.commands.CommandResult
import com.ilanmarchand.tui_launcher.commands.OutputLine
import com.ilanmarchand.tui_launcher.commands.env
import com.ilanmarchand.tui_launcher.commands.export
import com.ilanmarchand.tui_launcher.commands.splitShellWords
import com.ilanmarchand.tui_launcher.commands.unset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VariablesTest {
    private val values = mapOf("HOME" to "/storage/emulated/0", "NAME" to "Ilan", "SPACED" to "a b", "QUOTED" to "it's \"x\"", "?" to "1")
    private fun expand(text: String) = expandVariables(text) { values[it] }

    // What the command finally sees, after its own word splitting
    private fun words(text: String) = splitShellWords(expand(text))

    @Test
    fun namesAndBraces() {
        assertEquals(listOf("hi", "Ilan"), words("hi \$NAME"))
        assertEquals(listOf("Ilan_x"), words("\${NAME}_x"))
        assertEquals(listOf("/storage/emulated/0/Download"), words("\$HOME/Download"))
        assertEquals(listOf("1"), words("\$?"))
        assertEquals(listOf("[]"), words("[\$UNSET]"))
    }

    @Test
    fun quotingRules() {
        assertEquals(listOf("\$NAME"), words("'\$NAME'"))
        assertEquals(listOf("hello Ilan"), words("\"hello \$NAME\""))
        assertEquals(listOf("\$NAME"), words("\\\$NAME"))
        // Unquoted, a value with blanks splits into words; quoted, it stays one
        assertEquals(listOf("a", "b"), words("\$SPACED"))
        assertEquals(listOf("a b"), words("\"\$SPACED\""))
        // Quotes inside a value stay plain characters
        assertEquals(listOf("it's", "\"x\""), words("\$QUOTED"))
        assertEquals(listOf("it's \"x\""), words("\"\$QUOTED\""))
        assertEquals(listOf("$", "5$"), words("$ 5$"))
    }

    @Test
    fun tilde() {
        assertEquals(listOf("/storage/emulated/0"), words("~"))
        assertEquals(listOf("/storage/emulated/0/notes"), words("~/notes"))
        assertEquals(listOf("a~b", "~"), words("a~b '~'"))
    }

    @Test
    fun targetsAreNotEscaped() {
        assertEquals("it's \"x\".txt", expandVariables("\$QUOTED.txt", escape = false) { values[it] })
    }

    @Test
    fun assignments() {
        assertEquals(listOf("A" to "1", "B" to "x y"), assignmentsIn(listOf("A=1", "B=x y")))
        assertNull(assignmentsIn(listOf("A=1", "echo")))
        assertNull(assignmentsIn(listOf("1A=x")))
        assertNull(assignmentsIn(emptyList()))
    }

    @Test
    fun exportUnsetEnv() {
        val variables = Variables()
        val builtins = mapOf("HOME" to "/h", "USER" to "Boudbois")
        assertEquals(CommandResult.Output(emptyList()), export(listOf("EDITOR=nano", "LATER"), variables, builtins))
        assertEquals(
            CommandResult.Output(listOf("declare -x EDITOR=\"nano\"", "declare -x HOME=\"/h\"", "declare -x LATER", "declare -x USER=\"Boudbois\"").map { OutputLine(it) }),
            export(emptyList(), variables, builtins)
        )
        assertEquals(CommandResult.Output(listOf("EDITOR=nano", "HOME=/h", "USER=Boudbois").map { OutputLine(it) }), env(variables, builtins))
        unset(listOf("EDITOR"), variables)
        assertNull(variables.get("EDITOR"))
        assertEquals(
            CommandResult.Output(listOf(OutputLine("export: `1x=y': not a valid identifier", isError = true))),
            export(listOf("1x=y"), variables, builtins)
        )
    }
}

package com.ilanmarchand.tui_launcher.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandLineTest {
    private fun parsed(vararg items: ListItem) = ParseResult.Parsed(items.toList())
    private fun error(message: String) = ParseResult.SyntaxError(message)

    @Test
    fun singleCommand() {
        assertEquals(parsed(ListItem(null, listOf("nmcli radio wifi"))), parseCommandLine("nmcli radio wifi"))
        assertEquals(parsed(), parseCommandLine(""))
        assertEquals(parsed(), parseCommandLine("   "))
    }

    @Test
    fun pipelinesAndLists() {
        assertEquals(
            parsed(ListItem(null, listOf("contacts list", "grep -i paul", "wc -l"))),
            parseCommandLine("contacts list | grep -i paul | wc -l")
        )
        assertEquals(
            parsed(
                ListItem(null, listOf("a")),
                ListItem(Connector.AND, listOf("b", "c")),
                ListItem(Connector.OR, listOf("d")),
                ListItem(Connector.THEN, listOf("e"))
            ),
            parseCommandLine("a && b | c || d ; e")
        )
        assertEquals(parsed(ListItem(null, listOf("a"))), parseCommandLine("a;"))
    }

    @Test
    fun quotesProtectOperators() {
        assertEquals(parsed(ListItem(null, listOf("echo 'a | b' \"c && d\""))), parseCommandLine("echo 'a | b' \"c && d\""))
        assertEquals(parsed(ListItem(null, listOf("echo a\\;b"))), parseCommandLine("echo a\\;b"))
        assertEquals(parsed(ListItem(null, listOf("'Red & moi'"))), parseCommandLine("'Red & moi'"))
    }

    @Test
    fun comments() {
        assertEquals(parsed(ListItem(null, listOf("echo a"))), parseCommandLine("echo a # note"))
        assertEquals(parsed(ListItem(null, listOf("call *#06#"))), parseCommandLine("call *#06#"))
        assertEquals(parsed(), parseCommandLine("# just a note"))
    }

    @Test
    fun bashSyntaxErrors() {
        assertEquals(error("syntax error near unexpected token `|'"), parseCommandLine("| a"))
        assertEquals(error("syntax error near unexpected token `;'"), parseCommandLine("; a"))
        assertEquals(error("syntax error near unexpected token `&&'"), parseCommandLine("a && && b"))
        assertEquals(error("syntax error near unexpected token `;;'"), parseCommandLine("a ;; b"))
        assertEquals(error("syntax error: unexpected end of file"), parseCommandLine("echo a &&"))
        assertEquals(error("syntax error: unexpected end of file"), parseCommandLine("echo a |"))
        assertEquals(error("unexpected EOF while looking for matching `''"), parseCommandLine("echo 'a"))
    }

    @Test
    fun unsupportedShellFeatures() {
        assertEquals(error("background jobs (&) are not available here"), parseCommandLine("Red & moi"))
        assertEquals(error("redirections (>) are not available here"), parseCommandLine("echo a > file"))
    }
}

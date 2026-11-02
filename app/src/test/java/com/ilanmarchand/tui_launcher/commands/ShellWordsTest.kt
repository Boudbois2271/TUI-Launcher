// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellWordsTest {
    @Test
    fun blanksSeparateWords() {
        assertEquals(listOf("ddg=duckduckgo", "ig=instagram"), splitShellWords("ddg=duckduckgo   ig=instagram"))
        assertEquals(emptyList<String>(), splitShellWords(""))
    }

    @Test
    fun quotesJoinIntoOneWord() {
        assertEquals(listOf("jw=jw library"), splitShellWords("jw='jw library'"))
        assertEquals(listOf("jw=jw library"), splitShellWords("jw=\"jw library\""))
        assertEquals(listOf("jw=jw library"), splitShellWords("jw=jw\\ library"))
        assertEquals(listOf("a=it's"), splitShellWords("a='it'\\''s'"))
        assertEquals(listOf("a=say \"hi\" \$x"), splitShellWords("a=\"say \\\"hi\\\" \\\$x\""))
    }

    @Test
    fun unclosedQuoteIsAnError() {
        assertNull(splitShellWords("a='b"))
        assertNull(splitShellWords("a=\"b"))
    }

    @Test
    fun quotedValuesReadBackUnchanged() {
        for (value in listOf("duckduckgo", "jw library", "it's", "sudo ", "")) {
            assertEquals(listOf("a=$value"), splitShellWords("a=" + shellQuote(value)))
        }
    }
}

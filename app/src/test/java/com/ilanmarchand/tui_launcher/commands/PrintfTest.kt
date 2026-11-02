// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

// Expected outputs were taken from bash 5 on the PC
class PrintfTest {
    private fun out(vararg words: String) = (printf(words.toList()) as CommandResult.Output).let { result ->
        result.lines.map { it.text } to result.exitCode
    }

    @Test
    fun formats() {
        assertEquals(listOf("a-b", "c-") to 0, out("%s-%s\\n", "a", "b", "c"))
        assertEquals(listOf("   42|42   |00042") to 0, out("%5d|%-5d|%05d\\n", "42", "42", "42"))
        assertEquals(listOf("ff FF 10 0xff 010") to 0, out("%x %X %o %#x %#o\\n", "255", "255", "8", "255", "8"))
        assertEquals(listOf("3.14 1.234500e+03 0.0001 1e+10 100") to 0, out("%.2f %e %g %g %g\\n", "3.14159", "1234.5", "0.0001", "1e10", "100"))
        assertEquals(listOf("hw") to 0, out("%c%c\\n", "hello", "world"))
        assertEquals(listOf("a\tb") to 0, out("%b\\n", "a\\tb"))
        assertEquals(listOf("") to 0, out("%s\\n"))
        assertEquals(listOf("+5  5 005") to 0, out("%+d % d %.3d\\n", "5", "5", "5"))
        assertEquals(listOf("65") to 0, out("%d\\n", "'A"))
        assertEquals(listOf("       abc|") to 0, out("%10.3s|\\n", "abcdef"))
        assertEquals(listOf("a", "b") to 0, out("a\\nb"))
        assertEquals(listOf("   7|") to 0, out("%*d|\\n", "4", "7"))
        assertEquals(listOf("  2.2|1.23e+04|") to 0, out("%5.1f|%-8.2e|\\n", "2.25", "12345"))
        assertEquals(listOf("stop") to 0, out("%b", "stop\\chere"))
    }

    @Test
    fun errors() {
        assertEquals(listOf("printf: abc: invalid number", "0") to 1, out("%d\\n", "abc"))
        assertEquals(listOf("printf: `y': invalid format character", "x") to 1, out("x%zy\\n"))
        assertEquals(listOf("printf: usage: printf [-v var] format [arguments]") to 2, out())
    }

    @Test
    fun dateUsesTheSystemZoneName() {
        val now = ZonedDateTime.of(2026, 10, 17, 17, 58, 45, 0, ZoneId.of("Europe/Paris"))
        assertEquals(listOf("Sat Oct 17 17:58:45 CEST 2026"), (date(emptyList(), now, "CEST") as CommandResult.Output).lines.map { it.text })
        assertEquals(listOf("Sat Oct 17 15:58:45 UTC 2026"), (date(listOf("-u"), now, "CEST") as CommandResult.Output).lines.map { it.text })
    }
}

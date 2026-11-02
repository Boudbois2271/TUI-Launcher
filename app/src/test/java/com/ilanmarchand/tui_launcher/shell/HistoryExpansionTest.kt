// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.shell

import com.ilanmarchand.tui_launcher.commands.CommandResult
import com.ilanmarchand.tui_launcher.commands.OutputLine
import com.ilanmarchand.tui_launcher.commands.history
import com.ilanmarchand.tui_launcher.log.stripTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryExpansionTest {
    private val past = listOf("nmcli radio wifi", "echo hello", "wpctl get-volume @DEFAULT_AUDIO_SINK@", "echo bye")

    private fun expanded(line: String) = HistoryExpansion.Expanded(line, true)

    @Test
    fun events() {
        assertEquals(expanded("echo bye"), expandHistory("!!", past))
        assertEquals(expanded("echo bye | wc -c"), expandHistory("!! | wc -c", past))
        assertEquals(expanded("nmcli radio wifi"), expandHistory("!1", past))
        assertEquals(expanded("wpctl get-volume @DEFAULT_AUDIO_SINK@"), expandHistory("!-2", past))
        assertEquals(expanded("echo bye"), expandHistory("!echo", past))
        assertEquals(expanded("nmcli radio wifi off"), expandHistory("!nm off", past))
    }

    @Test
    fun plainExclamationMarks() {
        assertEquals(HistoryExpansion.Expanded("echo hi!", false), expandHistory("echo hi!", past))
        assertEquals(HistoryExpansion.Expanded("echo ! x", false), expandHistory("echo ! x", past))
        assertEquals(HistoryExpansion.Expanded("echo '!!'", false), expandHistory("echo '!!'", past))
        assertEquals(HistoryExpansion.Expanded("echo \\!!", false), expandHistory("echo \\!!", past))
        assertEquals(expanded("echo \"echo bye\""), expandHistory("echo \"!!\"", past))
    }

    @Test
    fun missingEvents() {
        assertEquals(HistoryExpansion.Failed("!zzz: event not found"), expandHistory("!zzz", past))
        assertEquals(HistoryExpansion.Failed("!99: event not found"), expandHistory("!99", past))
        assertEquals(HistoryExpansion.Failed("!!: event not found"), expandHistory("!!", emptyList()))
    }

    @Test
    fun historyList() {
        assertEquals(
            CommandResult.Output(listOf(OutputLine("    3  wpctl get-volume @DEFAULT_AUDIO_SINK@"), OutputLine("    4  echo bye"))),
            history(listOf("2"), past)
        )
        assertEquals(4, (history(emptyList(), past) as CommandResult.Output).lines.size)
        assertEquals(
            CommandResult.Output(listOf(OutputLine("history: x: numeric argument required", isError = true))),
            history(listOf("x"), past)
        )
    }

    @Test
    fun logLinesLoseTheirTimestamp() {
        assertEquals("unown", stripTimestamp("2026-10-17 12:16:46  unown"))
        assertEquals("echo  two  spaces", stripTimestamp("2026-10-17 12:16:46  echo  two  spaces"))
        assertEquals("no timestamp", stripTimestamp("no timestamp"))
    }
}

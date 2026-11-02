// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.completion

import org.junit.Assert.assertEquals
import org.junit.Test

class CompletionTest {
    private val commands = listOf("grep", "ls", "wpctl", "Settings", "Google Play", "gcc-alias")
    private val folder = mapOf(
        "." to listOf(
            FileEntry("docs", true),
            FileEntry("notes", true),
            FileEntry("notes.txt", false),
            FileEntry("nano.log", false),
            FileEntry(".hidden", false),
        ),
        "docs/" to listOf(FileEntry("a.txt", false), FileEntry("ab.txt", false)),
    )

    private fun complete(line: String) = complete(line, commands) { folder[it] }

    @Test
    fun uniqueCommandGetsASpace() {
        assertEquals(Completion.Insert("wpctl "), complete("wp"))
        assertEquals(Completion.Insert("Settings "), complete("set"))
    }

    @Test
    fun commandCompletesAfterAPipe() {
        assertEquals(Completion.Insert("ls | grep "), complete("ls | gr"))
    }

    @Test
    fun multiWordAppLabelCompletesWhole() {
        assertEquals(Completion.Insert("Google Play "), complete("Google P"))
    }

    @Test
    fun ambiguousCommandsListWhenNoPrefixIsCommon() {
        // "g" matches Google Play, gcc-alias and grep: their common start is only "G"
        assertEquals(Completion.Ambiguous(listOf("Google Play", "gcc-alias", "grep")), complete("g"))
    }

    @Test
    fun uniqueFileGetsSpaceAndFolderGetsSlash() {
        assertEquals(Completion.Insert("cat nano.log "), complete("cat na"))
        assertEquals(Completion.Insert("cd docs/"), complete("cd do"))
    }

    @Test
    fun commonPrefixOfFilesIsFilled() {
        // a.txt and ab.txt share "a", so the word "docs/" becomes "docs/a"
        assertEquals(Completion.Insert("cat docs/a"), complete("cat docs/"))
    }

    @Test
    fun noProgressListsTheMatches() {
        assertEquals(Completion.Ambiguous(listOf("docs/a.txt", "docs/ab.txt")), complete("cat docs/a"))
    }

    @Test
    fun hiddenFilesOnlyWhenTheWordStartsWithADot() {
        assertEquals(Completion.Insert("cat .hidden "), complete("cat ."))
        assertEquals(Completion.Ambiguous(listOf("nano.log", "notes/", "notes.txt")), complete("cat n"))
    }

    @Test
    fun emptyWordAfterCommandListsFiles() {
        assertEquals(Completion.Ambiguous(listOf("docs/", "nano.log", "notes/", "notes.txt")), complete("ls "))
    }

    @Test
    fun noMatchChangesNothing() {
        assertEquals(Completion.None, complete("zzz"))
        assertEquals(Completion.None, complete("cat zzz"))
    }

    @Test
    fun unreadableFolderMatchesNothing() {
        assertEquals(Completion.None, complete("cat nope/x"))
    }
}

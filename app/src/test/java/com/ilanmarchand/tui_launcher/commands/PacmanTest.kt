package com.ilanmarchand.tui_launcher.commands

import com.ilanmarchand.tui_launcher.apps.InstalledPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Messages were checked against the real pacman on the PC
class PacmanTest {
    private val packages = listOf(
        InstalledPackage("com.android.settings", "11", "Settings", isSystem = true),
        InstalledPackage("com.deezer.android.app", "7.0.1", "Deezer", isSystem = false),
        InstalledPackage("com.ilanmarchand.tui_launcher", "1.0", null, isSystem = false),
        InstalledPackage("com.instagram.android", "312.0.0", "Instagram", isSystem = false)
    )
    private val removed = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private val actions = PacmanActions(
        self = "com.ilanmarchand.tui_launcher",
        remove = { removed += it; null },
        openStore = { opened += it; true }
    )

    private fun run(vararg words: String, root: Boolean = false) = pacman(words.toList(), root, packages, actions)
    private fun out(vararg lines: String, exitCode: Int = 0) = CommandResult.Output(lines.map { OutputLine(it) }, exitCode)
    private fun err(vararg lines: String) = CommandResult.Output(lines.map { OutputLine(it, isError = true) })

    @Test
    fun query() {
        assertEquals(4, (run("-Q") as CommandResult.Output).lines.size)
        assertEquals(out("com.instagram.android 312.0.0"), run("-Q", "instagram"))
        assertEquals(err("error: package 'zzz' was not found"), run("-Q", "zzz"))
        assertEquals(out("local/com.deezer.android.app 7.0.1", "    Deezer"), run("-Qs", "deez"))
        assertEquals(out(exitCode = 1), run("-Qs", "nothing"))
    }

    @Test
    fun removeNeedsRootAndConfirmation() {
        assertEquals(err("error: you cannot perform this operation unless you are root."), run("-R", "instagram"))
        val asked = run("-R", "instagram", root = true) as CommandResult.Ask
        assertEquals(":: Do you want to remove these packages? [Y/n]", asked.question)
        assertTrue(asked.lines.any { it.text == "Packages (1) com.instagram.android-312.0.0" })
        assertEquals(out(exitCode = 1), asked.answer("n"))
        assertEquals(emptyList<String>(), removed)
        assertEquals(out(":: Processing package changes...", "(1/1) removing com.instagram.android"), asked.answer(""))
        assertEquals(listOf("com.instagram.android"), removed)
    }

    @Test
    fun removeRefusals() {
        assertEquals(err("error: target not found: zzz"), run("-R", "zzz", root = true))
        assertEquals(err("error: 'com.android.settings' is part of the system: Android can't uninstall it"), run("-R", "settings", root = true))
        assertEquals(err("error: 'com.ilanmarchand.tui_launcher' is this launcher: it can't remove itself"), run("-R", "com.ilanmarchand.tui_launcher", root = true))
        val noConfirm = run("-Rns", "deezer", "--noconfirm", root = true) as CommandResult.Output
        assertEquals("(1/1) removing com.deezer.android.app", noConfirm.lines.last().text)
    }

    @Test
    fun syncOpensThePlayStore() {
        assertEquals(err("error: you cannot perform this operation unless you are root."), run("-S", "spotify"))
        assertEquals(out(":: Opening the Play Store for 'spotify'..."), run("-S", "spotify", root = true))
        run("-S", "com.spotify.music", root = true)
        assertEquals(out(":: Searching the Play Store for 'music player'..."), run("-Ss", "music", "player"))
        assertEquals(
            listOf(
                "market://search?q=spotify&c=apps",
                "market://details?id=com.spotify.music",
                "market://search?q=music+player&c=apps"
            ),
            opened
        )
    }

    @Test
    fun optionErrors() {
        assertEquals(err("error: no operation specified (use -h for help)"), run())
        assertEquals(err("pacman: invalid option -- 'z'"), run("-Rz", "x"))
        assertEquals(err("error: only one operation may be used at a time"), run("-QR"))
        assertEquals("usage:  pacman <operation> [...]", (run("-h") as CommandResult.Output).lines.first().text)
    }
}

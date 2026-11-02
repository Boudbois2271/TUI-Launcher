// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.files

import com.ilanmarchand.tui_launcher.shell.RedirectException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Messages follow bash's redirection errors, checked on the PC
class WorkspaceRedirectsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun readAndWrite() {
        val home = temp.newFolder("home")
        File(home, "notes").mkdir()
        val log = File(home, "log.txt").apply { writeText("x\n") }
        val redirects = WorkspaceRedirects(Workspace(home, setOf(log)))
        redirects.write("notes/shop.md", listOf("milk"), append = false)
        redirects.write("notes/shop.md", listOf("eggs"), append = true)
        assertEquals(listOf("milk", "eggs"), redirects.read("notes/shop.md"))
        assertEquals("nosuch: No such file or directory", assertThrows(RedirectException::class.java) { redirects.read("nosuch") }.message)
        assertEquals("notes: Is a directory", assertThrows(RedirectException::class.java) { redirects.write("notes", emptyList(), false) }.message)
        assertEquals("a/b: No such file or directory", assertThrows(RedirectException::class.java) { redirects.write("a/b", emptyList(), false) }.message)
        assertEquals("log.txt: Operation not permitted", assertThrows(RedirectException::class.java) { redirects.write("log.txt", emptyList(), false) }.message)
        assertEquals("x\n", log.readText())
    }
}

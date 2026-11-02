// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.aliases

import org.junit.Assert.assertEquals
import org.junit.Test

class AliasExpansionTest {
    @Test
    fun firstWordIsReplacedAndTheRestKept() {
        val aliases = mapOf("ddg" to "duckduckgo")
        assertEquals("duckduckgo", expandAliases("ddg", aliases))
        assertEquals("duckduckgo -s google drive", expandAliases("ddg -s google drive", aliases))
    }

    @Test
    fun namesAreCaseSensitive() {
        assertEquals("DDG", expandAliases("DDG", mapOf("ddg" to "duckduckgo")))
    }

    @Test
    fun onlyTheFirstWordIsExpanded() {
        assertEquals("open ddg", expandAliases("open ddg", mapOf("ddg" to "duckduckgo")))
    }

    @Test
    fun aliasesChainWithoutLooping() {
        assertEquals("instagram", expandAliases("i", mapOf("i" to "ig", "ig" to "instagram")))
        assertEquals("a", expandAliases("a", mapOf("a" to "b", "b" to "a")))
    }

    @Test
    fun valueEndingWithABlankExpandsTheNextWord() {
        val aliases = mapOf("s" to "sudo ", "ddg" to "duckduckgo")
        assertEquals("sudo duckduckgo -s x", expandAliases("s ddg -s x", aliases))
        assertEquals("sudo -s ddg", expandAliases("s -s ddg", aliases))
    }

    @Test
    fun aliasCanNameABuiltinOrAnythingElse() {
        assertEquals("clear", expandAliases("c", mapOf("c" to "clear")))
        assertEquals("whatever x", expandAliases("w x", mapOf("w" to "whatever")))
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuiltinCommandTest {
    @Test
    fun commandWithoutArgumentsMatchesOnlyItsName() {
        assertEquals(ClearCommand, findBuiltinCommand("clear")?.command)
        assertEquals(ClearCommand, findBuiltinCommand("CLEAR")?.command)
        assertNull(findBuiltinCommand("clear now"))
    }

    @Test
    fun commandWithArgumentsGetsTheRestOfTheLine() {
        assertEquals(BuiltinCall(AliasCommand, "ig instagram"), findBuiltinCommand("alias ig instagram"))
        assertEquals(BuiltinCall(AliasCommand, ""), findBuiltinCommand("alias"))
        assertEquals(BuiltinCall(UnaliasCommand, "ig"), findBuiltinCommand("unalias ig"))
    }

    @Test
    fun aCommandThatNeedsArgumentsLeavesItsBareNameToApps() {
        assertNull(findBuiltinCommand("contacts"))
        assertNull(findBuiltinCommand("Contacts"))
        assertEquals(BuiltinCall(ContactsCommand, "list"), findBuiltinCommand("contacts list"))
        assertEquals(BuiltinCall(CallCommand, ""), findBuiltinCommand("call"))
    }

    @Test
    fun otherNamesAreNotBuiltins() {
        assertNull(findBuiltinCommand("instagram"))
        assertNull(findBuiltinCommand("aliasing ig"))
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneBookTest {
    private val book = listOf(
        ContactNumber("Maman", "06 11 11 11 11", false),
        ContactNumber("Hélène Dupont", "0622222222", false),
        ContactNumber("Annick", "0633333333", false),
        ContactNumber("Anne", "0644444444", false),
        ContactNumber("Paul", "0655555555", false),
        ContactNumber("Paul", "0666666666", true),
        ContactNumber("Zoé", "0677777777", false),
        ContactNumber("Zoé", "+33 6 88 88 88 88", false)
    )

    @Test
    fun phoneNumbers() {
        assertTrue(isPhoneNumber("0612345678"))
        assertTrue(isPhoneNumber("+33 6 12 34 56 78"))
        assertTrue(isPhoneNumber("06.12.34.56.78"))
        assertTrue(isPhoneNumber("*#06#"))
        assertFalse(isPhoneNumber("mom"))
        assertFalse(isPhoneNumber("Jane DOE"))
        assertFalse(isPhoneNumber("1"))
        assertFalse(isPhoneNumber(""))
    }

    @Test
    fun exactNameIgnoringCaseAndAccents() {
        assertEquals(ContactMatch.Found(book[0]), findContactNumber(book, "maman"))
        assertEquals(ContactMatch.Found(book[1]), findContactNumber(book, "helene dupont"))
    }

    @Test
    fun partOfAName() {
        assertEquals(ContactMatch.Found(book[1]), findContactNumber(book, "hélène"))
        assertEquals(ContactMatch.Found(book[2]), findContactNumber(book, "annick"))
    }

    @Test
    fun exactNameWinsOverLongerNames() {
        assertEquals(ContactMatch.Found(book[3]), findContactNumber(book, "anne"))
    }

    @Test
    fun ambiguousOrMissing() {
        assertEquals(ContactMatch.SeveralContacts(listOf("Annick", "Anne")), findContactNumber(book, "ann"))
        assertEquals(ContactMatch.NotFound, findContactNumber(book, "mom"))
    }

    @Test
    fun severalNumbersUseTheDefault() {
        assertEquals(ContactMatch.Found(book[5]), findContactNumber(book, "paul"))
        assertEquals(
            ContactMatch.SeveralNumbers("Zoé", listOf("0677777777", "+33 6 88 88 88 88")),
            findContactNumber(book, "zoe")
        )
    }
}

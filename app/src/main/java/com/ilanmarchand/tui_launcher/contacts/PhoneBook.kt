// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.contacts

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import java.text.Normalizer

data class ContactNumber(val name: String, val number: String, val isDefault: Boolean)

// Every phone number in the address book, sorted by name, each number listed once per name
fun readContactNumbers(context: Context): List<ContactNumber> {
    val projection = arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.IS_SUPER_PRIMARY)
    val rows = mutableListOf<ContactNumber>()
    context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, "${Phone.DISPLAY_NAME} COLLATE NOCASE")
        ?.use { cursor ->
            while (cursor.moveToNext()) {
                rows += ContactNumber(cursor.getString(0).orEmpty(), cursor.getString(1).orEmpty(), cursor.getInt(2) != 0)
            }
        }
    return rows.distinctBy { foldName(it.name) to digitsOf(it.number) }
}

// Saves a contact on the phone itself, outside any account, with one mobile number
fun addContact(context: Context, name: String, number: String) {
    val operations = arrayListOf(
        ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build(),
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, name)
            .build(),
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
            .withValue(Phone.NUMBER, number)
            .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
            .build()
    )
    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
}

// A dialable number: digits with an optional leading +, and spaces, dots, dashes, parentheses, * or #
internal fun isPhoneNumber(text: String): Boolean =
    Regex("""^\+?[0-9*#][0-9*# ().-]*$""").matches(text) && text.count { it.isDigit() } >= 2

internal fun digitsOf(number: String): String = number.filter { it.isDigit() || it == '+' }

// Names compare ignoring case and accents, so "helene" finds "Hélène"
internal fun foldName(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

sealed interface ContactMatch {
    data class Found(val contact: ContactNumber) : ContactMatch
    data object NotFound : ContactMatch
    data class SeveralContacts(val names: List<String>) : ContactMatch
    data class SeveralNumbers(val name: String, val numbers: List<String>) : ContactMatch
}

// The number to call for a name: the contact with exactly that name, else the one whose name contains it.
// A contact with several numbers uses its default number, if one is set.
internal fun findContactNumber(rows: List<ContactNumber>, query: String): ContactMatch {
    val wanted = foldName(query)
    val exact = rows.filter { foldName(it.name) == wanted }
    val matches = exact.ifEmpty { rows.filter { wanted in foldName(it.name) } }
    if (matches.isEmpty()) return ContactMatch.NotFound
    val byName = matches.groupBy { foldName(it.name) }
    if (byName.size > 1) return ContactMatch.SeveralContacts(byName.values.map { it.first().name })
    val numbers = byName.values.first().distinctBy { digitsOf(it.number) }
    if (numbers.size == 1) return ContactMatch.Found(numbers.first())
    numbers.firstOrNull { it.isDefault }?.let { return ContactMatch.Found(it) }
    return ContactMatch.SeveralNumbers(numbers.first().name, numbers.map { it.number })
}

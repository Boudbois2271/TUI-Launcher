// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ilanmarchand.tui_launcher.admin.grantOwnPermission
import com.ilanmarchand.tui_launcher.contacts.ContactMatch
import com.ilanmarchand.tui_launcher.contacts.findContactNumber
import com.ilanmarchand.tui_launcher.contacts.isPhoneNumber
import com.ilanmarchand.tui_launcher.contacts.readContactNumbers

// Starts a phone call. Linux has no standard command for it, so the syntax is the launcher's own:
//   call NUMBER      (call 0612345678, call +33 6 12 34 56 78)
//   call NAME        (call mom, call 'Jane DOE'; case and accents don't matter, part of a name works)
object CallCommand : BuiltinCommand {
    override val name = "call"
    override val synopsis = "call NUMBER | NAME"
    override val summary = "Start a phone call to a number or a contact."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return error("call: unexpected EOF while looking for matching quote")
        val target = words.joinToString(" ")
        if (target.isEmpty()) return error("usage: call NUMBER|NAME")
        val number = if (isPhoneNumber(target)) {
            target
        } else {
            if (!grantOwnPermission(context, Manifest.permission.READ_CONTACTS)) return error("call: Permission denied")
            when (val match = findContactNumber(readContactNumbers(context), target)) {
                is ContactMatch.Found -> match.contact.number
                ContactMatch.NotFound -> return error("call: no contact named '$target'")
                is ContactMatch.SeveralContacts ->
                    return error("call: '$target' matches several contacts: ${match.names.joinToString(", ")}")
                is ContactMatch.SeveralNumbers ->
                    return error("call: ${match.name} has several numbers: ${match.numbers.joinToString(", ")}")
            }
        }
        if (!grantOwnPermission(context, Manifest.permission.CALL_PHONE)) return error("call: Permission denied")
        val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            CommandResult.Output(emptyList())
        } catch (e: ActivityNotFoundException) {
            error("call: no app can place calls")
        } catch (e: SecurityException) {
            error("call: Permission denied")
        }
    }

    private fun error(message: String) = CommandResult.Output(listOf(OutputLine(message, isError = true)))
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.aliases

import android.content.Context

private const val PREFS_NAME = "aliases"

// An alias maps a name to the text that replaces it. Aliases are kept in app preferences, so they survive
// restarts. Names are case-sensitive, as in bash. Call these off the main thread: writes wait to finish.
class AliasStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): Map<String, String> = prefs.all
        .mapNotNull { (name, value) -> (value as? String)?.let { name to it } }
        .toMap()

    fun get(name: String): String? = prefs.getString(name, null)

    fun set(name: String, value: String) {
        prefs.edit().putString(name, value).commit()
    }

    fun remove(name: String): Boolean {
        if (!prefs.contains(name)) return false
        prefs.edit().remove(name).commit()
        return true
    }

    fun removeAll() {
        prefs.edit().clear().commit()
    }
}

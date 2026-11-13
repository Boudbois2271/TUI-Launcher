// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.terminal

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.type
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper

// The keys the prompt takes for itself, before the soft keyboard: Tab completes, Up and Down walk the history.
// The keyboard sends them as key events (Fn + A, Fn + K, Fn + J). Each is acted on at the press, and both the
// press and the release are consumed, so the keyboard never types them.
internal fun Modifier.promptKeys(onTab: () -> Unit, onUp: () -> Unit, onDown: () -> Unit): Modifier =
    onPreInterceptKeyBeforeSoftKeyboard { event ->
        val action = when (event.key) {
            Key.Tab -> onTab
            Key.DirectionUp -> onUp
            Key.DirectionDown -> onDown
            else -> null
        }
        if (action != null && event.type == KeyEventType.KeyDown) action()
        action != null
    }

// The keys the prompt takes for itself. The soft keyboard sends them as key events on the input connection,
// which does not pass through the key modifier above, so KeyTakingInput takes them there.
internal val promptKeyCodes = setOf(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)

// Wraps the input connection the keyboard types into. Each key event goes to take first; the ones it takes
// (it returns true) stop there, the others go on to the field, as before.
internal class KeyTakingInput(base: InputConnection, private val take: (KeyEvent) -> Boolean) : InputConnectionWrapper(base, false) {
    override fun sendKeyEvent(event: KeyEvent): Boolean = take(event) || super.sendKeyEvent(event)
}

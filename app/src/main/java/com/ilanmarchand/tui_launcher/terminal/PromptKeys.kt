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
// which does not pass through the key modifier above, so PromptKeyInput takes them there.
internal val promptKeyCodes = setOf(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)

// Wraps the input connection the keyboard types into. Its key events for the prompt keys are taken, on both the
// press and the release, and onKey runs on the press. Other key events go on to the field, as before.
internal class PromptKeyInput(base: InputConnection, private val onKey: (Int) -> Unit) : InputConnectionWrapper(base, false) {
    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode !in promptKeyCodes) return super.sendKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) onKey(event.keyCode)
        return true
    }
}

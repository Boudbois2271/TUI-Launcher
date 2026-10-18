package com.ilanmarchand.tui_launcher.terminal

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.type

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

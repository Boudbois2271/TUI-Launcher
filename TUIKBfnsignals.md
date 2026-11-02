# TUIKeyboard: signals sent by Fn and Ctrl

What the keyboard ([TUI-Keyboard](https://github.com/Boudbois2271/TUI-Keyboard), `TuiKeyboardService.kt` and `Layout.kt`) sends to the app for each Fn key and for each Ctrl combination. The launcher has to handle these, so this is the list to implement against.

Two kinds of signal:

- **Key event**: `KeyEvent` DOWN then UP, sent with `sendKeyEvent`. Flags `FLAG_SOFT_KEYBOARD`, device `VIRTUAL_KEYBOARD`, no unicode character. The app receives a key code, not text.
- **Text**: `commitText(text, 1)`. The app receives a character.

Source: `sendKey(keyCode, ctrl)` and `commit(text)` in `TuiKeyboardService.kt`. Ctrl sets `META_CTRL_ON | META_CTRL_LEFT_ON`. Without Ctrl, the meta state is 0.

## Fn layer

Fn is a tap to lock, or a hold that lasts while pressed. The layout is in `Layout.fn`. The positions below are the key under each label, with the base-layer letter it sits under.

| Key (under) | Label | Signal | With Ctrl held |
|---|---|---|---|
| Q | Esc | key `KEYCODE_ESCAPE` | key `KEYCODE_ESCAPE` + Ctrl |
| W | Home | key `KEYCODE_MOVE_HOME` | key `KEYCODE_MOVE_HOME` + Ctrl |
| E | End | key `KEYCODE_MOVE_END` | key `KEYCODE_MOVE_END` + Ctrl |
| R | PgUp | key `KEYCODE_PAGE_UP` | key `KEYCODE_PAGE_UP` + Ctrl |
| T | PgDn | key `KEYCODE_PAGE_DOWN` | key `KEYCODE_PAGE_DOWN` + Ctrl |
| A | Tab | key `KEYCODE_TAB` | key `KEYCODE_TAB` + Ctrl |
| D | Del | key `KEYCODE_FORWARD_DEL` | key `KEYCODE_FORWARD_DEL` + Ctrl |
| H | ← | key `KEYCODE_DPAD_LEFT` | key `KEYCODE_DPAD_LEFT` + Ctrl |
| J | ↓ | key `KEYCODE_DPAD_DOWN` | key `KEYCODE_DPAD_DOWN` + Ctrl |
| K | ↑ | key `KEYCODE_DPAD_UP` | key `KEYCODE_DPAD_UP` + Ctrl |
| L | → | key `KEYCODE_DPAD_RIGHT` | key `KEYCODE_DPAD_RIGHT` + Ctrl |
| Z row, left | ⇧ Shift | none, toggles the one-shot Shift state | none |
| Z row, right | ⌫ Backspace | key `KEYCODE_DEL`, repeats every 50 ms after 400 ms held | key `KEYCODE_DEL`, no Ctrl (Ctrl ignored) |
| Bottom, Enter | ⏎ | key `KEYCODE_ENTER` | key `KEYCODE_ENTER` + Ctrl |
| Bottom, Space | space | text `" "` | text `" "` (Ctrl ignored) |
| Bottom, Ctrl | Ctrl | none, holds Ctrl | none |
| Bottom, Fn | Fn | none, layer toggle | none |
| Bottom, Sym | Sym | none, switches to the Sym layer | none |

The digit row is blank in Fn. Fn sends no text except the space bar.

## Ctrl combinations

Ctrl is hold-to-combine. It is set on press and cleared on release. It applies to every layer, so it also works in Fn and Sym.

| Key pressed with Ctrl | Signal |
|---|---|
| Any letter, a to z (base layer) | key `KEYCODE_A` to `KEYCODE_Z` + Ctrl |
| Letter with accents (a, c, e, i, n, o, s, u, y), short tap | same as the plain letter: key + Ctrl |
| Letter with accents, long press | accent popup opens, and the accent under the finger is committed as text. Ctrl is not sent |
| Shift + letter, with Ctrl held | key + Ctrl, no Shift meta. The one-shot Shift is used up |
| Fn keys (Esc, Tab, Home, End, PgUp, PgDn, Del, arrows, Enter) | key + Ctrl, see the Fn table |
| Backspace | key `KEYCODE_DEL`, Ctrl ignored |
| Space | text `" "`, Ctrl ignored |
| Digits, punctuation, Sym page | text, Ctrl ignored (Ctrl+1 types `1`) |
| Shift, Fn, Sym, Ctrl | the key does its usual job |

Notes:

- Ctrl + letter is only the key event. The app has to turn `KEYCODE_x` + Ctrl meta into Ctrl+x. That includes Ctrl+A/C/R/V/X in the launcher's text field and in nano.
- `Layout.kt` says Fn "never types text", but Space in Fn commits `" "`. Decide whether this is a bug or intended.
- Ctrl+Shift+letter drops Shift, so Ctrl+Shift+C arrives as Ctrl+C. The app will not see Ctrl+Shift+C.
- Ctrl hold has not been tested on the phone, because `adb input` cannot hold one key while tapping another.

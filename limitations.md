# Limitations and open questions

Things touched but not finished, known issues, and doubts. Remove an entry once it is solved.

## Keyboard contract (TUI-Keyboard)

- Handled from `TUIKBfnsignals.md`: Tab and the arrows in the prompt, the arrows in nano, and Ctrl+C at
  nano's save question. Not yet handled: Ctrl+letter elsewhere (Ctrl+A/C/R/V/X in the prompt and in
  nano), Esc, Home/End, PgUp/PgDn, Del. Unused keys are taken so they don't hide the keyboard.
- The keyboard drops Shift when Ctrl is held, so Ctrl+Shift+letter can't be told apart from
  Ctrl+letter.

## Shell

- No background jobs: `&` is refused, and `jobs` and `notify-send` don't exist yet.
- `sed -i` is not implemented (it says so). Use `sed … file > other`.
- `seq` stops at 100000 numbers.
- No functions, `if`/`for`/`while`, command substitution `$(…)` or here-documents.
- No redirection to another output (`2>&1`).
- `~` is the shared storage (`/storage/emulated/0`), not the app's own folder.

## Device features

- Mobile data can't be switched on or off as device owner on Android 11. Options under consideration:
  Shizuku, Android's Internet panel, or nothing.
- Wi-Fi switching on MIUI needs a one-time `appops` grant (MIUI asks every time otherwise, even a
  device owner). See the README.
- `grim` saves to `Pictures/Screenshots` only. It can't take a file name, because screenshots go
  through the accessibility service.
- `uptime`'s load average and `uname`'s kernel version depend on Android letting the app read
  `/proc/loadavg` and `/proc/version`.
- `wpctl` controls media volume only, in Android's 15 steps.
- Going home from MIUI's Notes app leaves the keyboard closed; Notes seems to hide it after the
  launcher is in front. Other apps are fine.
- Coming back to the launcher, the prompt line jumps up, down, then up again before the keyboard
  settles.
- Several features need device owner or the accessibility service. Without them the commands say
  so instead of working.

## Not verified on a device

- Font size and general feel, only checked over adb screenshots.
- Ctrl from TUI-Keyboard beyond Ctrl+C in nano: `adb input` can't hold one key while tapping another.
- Only tested on one phone (Redmi Note 9T, Android 11, MIUI). Other Android versions or skins may
  differ, especially around device owner, Wi-Fi and storage permissions.

## Temporary code

- `help launch` (lists every launchable app label) and `log` (last 15 log entries) are development
  aids, to be removed.

# CLAUDE.md

## Project

TUI Launcher is an Android home launcher where the whole screen is a terminal: a prompt, typed
commands and text output. Typing an app's name and pressing Enter launches it. On top of that it has
a small bash-like shell with built-in versions of common Linux tools.

It is developed and tested on a Xiaomi Redmi Note 9T (Android 11 / API 30, MIUI). Other devices may
work but are untested.

Companion project: [TUI-Keyboard](https://github.com/Boudbois2271/TUI-Keyboard), a PC-style input
method that sends Esc, Tab, arrows and Ctrl combinations to the launcher. The keys it sends are
specified in `TUIKBfnsignals.md` in this repo.

## Files

| Path | What it is |
|---|---|
| `README.md` | Public overview: what it is, build and install. Keep it in sync when features change. |
| `app/src/main/java/.../tui_launcher/` | All the code, Kotlin + Jetpack Compose. Packages below. |
| `TUIKBfnsignals.md` | **Contract with TUI-Keyboard**: the key events and text each keyboard key sends. Change it first when the contract changes, then update both projects. |
| `limitations.md` | Known gaps, unfinished features, things not verified on a device. Read it before starting work, update it before finishing. |
| `LICENSE` | GPL-3.0. Every source file carries `SPDX-License-Identifier: GPL-3.0-or-later`. |
| `CLAUDE.local.md` | Private, gitignored: machine paths, current state, commit hashes, handoff notes (may not exist on your machine). |

Packages under `tui_launcher/`:

| Package | Role |
|---|---|
| `terminal/` | The terminal screen, the prompt, key handling, nano's editor screen. |
| `shell/` | Parsing: lists, pipes, redirections, variables, wildcards, history expansion. |
| `commands/` | Built-in commands, one file each, registered in one place (`BuiltinCommand.kt`). |
| `aliases/` | bash-style aliases, expanded on the first word of each command. |
| `apps/` | Loading launchable apps (`LauncherApps`) and matching typed names. |
| `completion/` | Tab completion. |
| `log/` | The command log (one timestamped line per submitted command). |
| `files/` | File access for `ls`, `cd`, `cat`, … with `~` = shared storage. |
| `admin/` | Device admin / device owner receiver and the features that need it. |
| `accessibility/` | The accessibility service used for screen lock and screenshots. |
| `contacts/`, `system/` | Contacts and calls; device info (identity, battery, memory, …). |
| `ui/theme/` | Colour and font tokens. |

## Hard rules

- No ads, no analytics, no tracking.
- No network access. Do not add the INTERNET permission or any networking library.
- Dark only. Flat look: no shadows, gradients, blur or rounded cards. No animations.
- Kotlin and Jetpack Compose only. No XML layouts and no View system.
- Touch keyboard first, no vim-style keys. Hardware-style keys sent by TUI-Keyboard (see
  `TUIKBfnsignals.md`) are the exception.
- Do not change the build setup (AGP, Kotlin, Compose BOM, SDK levels) unless the maintainer asks.
- Build only what is asked for. Features are added one at a time.

## Behaviour

- The prompt is `BBois@Note9T >` (`system/Identity.kt`; `whoami`, `hostname` and fastfetch agree).
- An app name followed by Enter launches it, matched on the label, case-insensitively.
- Apps are not sorted, grouped, pinned or put in folders.
- Every submitted command is appended to `terminal-log.txt` in the app's external files folder, one
  timestamped line each. It is the user's notebook: never remove it or stop it writing. Only
  submitted commands are logged, not keystrokes and not answers to a command's questions (such as
  pacman's `y`). Up/Down and `history` read from it.
- Commands copy the real Linux tool: syntax, output, error messages, exit codes. Use the launcher's
  own syntax only where Linux has nothing (`call`, `contacts`, `<app> -s QUERY`). No extra checks or
  refusals that bash wouldn't make.
- Each built-in command is a self-contained unit registered in one place, so adding one is small.
- It must work as the default home app: the manifest has `HOME` and `DEFAULT` next to `LAUNCHER`.
- Optional device owner mode (`adb shell dpm set-device-owner
  com.ilanmarchand.tui_launcher/.admin.AdminReceiver`) unlocks `reboot`, Wi-Fi control, brightness
  writes, silent uninstall and keeping itself as the home app. `dpm remove-active-admin` (built in,
  and over adb on debug builds) gives it back.

## Look

- Font: JetBrains Mono Nerd Font for all text, bundled in `app/src/main/res/font/` (Regular, Bold,
  Italic, Bold Italic, lowercase file names).
- Colours, defined as named tokens in `ui/theme/Color.kt` and used everywhere (no hex in screens):
  background `#000000`, foreground `#aeb6f4`, accents `#ff9a00`, `#f57c08`, `#dd770b`.
- Inspiration: htop, btop, fastfetch. Box-drawing outlines (`┌─ tabs ──┐`) only if asked.

## Code

- Official Kotlin style (set in `gradle.properties`). Keep it compatible with minSdk 26.
- Load apps with `LauncherApps.getActivityList`; resolving labels one by one through
  `PackageManager` took about 2 s on the test phone.
- Keep dependencies minimal. When a library is needed, say so and say why.
- New source files start with `// SPDX-License-Identifier: GPL-3.0-or-later`.
- Nothing personal in tracked files: no home paths, usernames beyond the prompt's `BBois`, contact
  names, phone numbers, network names or account details. That goes in `CLAUDE.local.md`.

## Build and run

- `./gradlew installDebug` with a phone connected over USB debugging. If the shell has no JDK on
  `PATH`, point `JAVA_HOME` at Android Studio's bundled JBR.
- Unit tests: `./gradlew testDebugUnitTest`. Lint: `./gradlew lintDebug`.
- On MIUI, installs need "Install via USB" in Developer options.
- After every change, build and install; if the build fails, fix it and run it again.

## Git

- Branch `master`, remote `origin` = https://github.com/Boudbois2271/TUI-Launcher.
- After each working step, once `installDebug` succeeds, commit it. Small commits, clear messages.
- Never commit `local.properties`, build output, `debug.keystore`, `CLAUDE.local.md`, `PLAN.md` or a
  copy of `terminal-log.txt`. `.gitignore` already excludes them.

## End of session

1. Update `TUIKBfnsignals.md` if the keyboard contract changed.
2. Move unfinished or doubtful items to `limitations.md`.
3. Write the handoff (where we are, what's next) in `CLAUDE.local.md`.

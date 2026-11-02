# TUI Launcher

A terminal-style Android home launcher. The whole screen is a bash-like shell: type an app's name to
launch it, or run built-in Linux-style commands. Kotlin and Jetpack Compose, with no network access.

```
BBois@Note9T > spotify
BBois@Note9T > nmcli d w l | grep -i box
BBois@Note9T > alias ig=instagram
BBois@Note9T > brightnessctl set 40%
```

## Features

- **Launch apps by name.** Type the label and press Enter, case-insensitive. Tab completes. Aliases
  work like bash (`alias name='value'`, `unalias`).
- **A small bash.** `;`, `&&`, `||`, pipes, redirections (`>`, `>>`, `<`, `2>`), variables, `export`,
  wildcards, `history`, `!!`, `!n` and Up/Down history.
- **Text tools** that behave like GNU's: `echo`, `printf`, `grep`, `sed`, `head`, `tail`, `wc`,
  `sort`, `uniq`, `tr`, `cut`, `tee`, `seq`, `tac`, `rev`, `nl`.
- **Files** on shared storage (`~`): `ls`, `cd`, `pwd`, `cat`, `mkdir`, `rm`, `touch`, `mv`, `cp`,
  and `nano` as a touch editor.
- **System info**: `fastfetch`, `date`, `uptime`, `uname`, `free`, `df`, `cal`, `acpi`.
- **Phone control** with Linux names: `nmcli` (Wi-Fi), `wpctl` (volume), `brightnessctl` (screen
  and flashlight), `playerctl`, `grim` (screenshot), `loginctl lock-session`, `reboot`, `xdg-open`.
  `sudo pacman -Q/-R/-S` lists, removes and finds apps.
- **Phone extras** where Linux has no equivalent: `contacts list|add`, `call NUMBER|NAME`, and
  `<app> -s QUERY` to search inside an app.
- `help` lists every built-in command, and `help <command>` explains one.
- Every submitted command is logged to `terminal-log.txt` in the app's files folder.
- Dark, flat, no animations. JetBrains Mono Nerd Font.
- **No INTERNET permission**, no ads, no analytics, no tracking.

Commands copy the real Linux tools' syntax, output and error messages, as closely as a phone allows.
Gaps are listed in [`limitations.md`](limitations.md).

## Build and install

Requirements: Android Studio (its bundled JDK works for Gradle), and a phone with USB debugging on.
minSdk 26 (Android 8.0). Developed and tested on a Redmi Note 9T with Android 11 / MIUI.

```sh
./gradlew installDebug
```

Then press Home and choose TUI Launcher as the default home app.

### Optional setup

Some commands need extra access. Without it, they say what's missing.

```sh
# Device owner: reboot, Wi-Fi control, brightness, silent app removal, stays the home app.
# Only works on a phone with no accounts added (remove them first, re-add after).
adb shell dpm set-device-owner com.ilanmarchand.tui_launcher/.admin.AdminReceiver
# To undo: run `dpm remove-active-admin com.ilanmarchand.tui_launcher/.admin.AdminReceiver`
# in the launcher, or the same command through `adb shell` on a debug build.

# All files access, for ls/cat/nano outside the app's own folder (some skins hide the setting)
adb shell appops set --uid com.ilanmarchand.tui_launcher MANAGE_EXTERNAL_STORAGE allow

# MIUI only: let nmcli switch Wi-Fi without a confirmation dialog every time
adb shell appops set com.ilanmarchand.tui_launcher 10001 allow
```

`loginctl lock-session` and `grim` need the launcher's accessibility service, which you turn on once
in Settings → Accessibility.

## Companion keyboard

[TUI-Keyboard](https://github.com/Boudbois2271/TUI-Keyboard) is a PC-style keyboard for this
launcher: number row, Esc, Tab, arrows, Ctrl combinations. The keys it sends are specified in
[`TUIKBfnsignals.md`](TUIKBfnsignals.md). The launcher works with any keyboard.

## License

[GPL-3.0-or-later](LICENSE).

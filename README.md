# Zapio

Android app for controlling Android TV devices on your own home network over ADB:
install APKs, browse and transfer files, run shell commands, take screenshots and
screen recordings, and use your phone as a remote control with text input.

**Status:** early development. The app starts and shows its first screens; connecting to a TV comes next.

## Planned features

- Connect to a TV on the local network (ADB over Wi-Fi), device discovery
- Install APK files with progress
- Two-pane file manager (phone ↔ TV)
- Shell terminal with quick-command buttons
- Screenshot and screen recording, saved to a folder of your choice
- Remote control (D-pad, OK, back, home, volume) and text input

## Download

Signed APKs are published under [Releases](../../releases). Each new release installs over the previous one.

## Building

```
./gradlew assembleDebug lintDebug :adb:test
```

The project has two parts: `adb` (plain Kotlin, everything that talks to the TV, built on
[dadb](https://github.com/mobile-dev-inc/dadb)) and `app` (the Android app, Jetpack Compose).
GitHub Actions builds every change, runs lint and tests, and runs the `adb` part against an
Android emulator. Pushes to `main` publish a signed release.

## Requirements on the TV

Developer options → ADB debugging (sometimes called "network debugging") must be enabled.
Only use this on devices you own and on a network you trust.

## License

Apache License 2.0, see [LICENSE](LICENSE).

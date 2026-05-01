# Whistle — Direct Bluetooth Re-pairing

Small Android app (package `com.ideonate.whistle`) to re-bond known Bluetooth
devices on demand, skipping the discovery scan that Settings → Pair-new-device
runs every time.

The workflow it's built for: forget a headset in system settings to stop it
auto-connecting, and tap "Pair & Connect" in Whistle to re-bond it directly
when you actually want to use it. Optionally, flag a device for auto-forget
and a daily background job will Forget it for you.

> The repo, gradle project, and signing keystore are still named `bttame` for
> historical reasons — only the Android package, app name, and user-facing
> surfaces use Whistle.

## Features

- **Multiple devices.** Add any currently-bonded device from your system
  pairing list; switch between them with a tap.
- **Pair & Connect.** Bonds straight to the saved MAC, no discovery scan.
  Modal dialog walks you through it: "Connecting…" → "Turn on the headset"
  / "Put it in pairing mode" → "Connected.". Configurable wait before the
  prompt appears (default 3s).
- **Forget.** Removes the bond from system Bluetooth — the same effect as
  forgetting in Settings, but one tap.
- **Auto-forget overnight.** Per-device toggle. A daily AlarmManager job at
  the time you set (default 03:00) calls Forget on any flagged device that
  isn't currently connected. Misses are fine — it'll catch up the next day.

## Prerequisites (one-time, macOS)

```sh
brew install openjdk@17 gradle
brew install --cask android-commandlinetools
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

Env vars (already in `~/.zshrc` on this machine):

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
```

First-run only — generate the gradle wrapper jar:

```sh
gradle wrapper --gradle-version 8.7
```

## Build

Debug (unsigned, fast iteration):

```sh
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Release (signed, for installing on the phone properly):

```sh
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Release builds need a keystore — see "Release signing" below.

## Install on a tethered Pixel

Phone needs **Settings → System → Developer options → USB debugging** on,
and must show as `device` in `adb devices` (tap "Allow" on the RSA prompt).

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

`-r` reinstalls over an existing install without wiping data. If you
previously installed a debug build, `adb` will refuse to replace it with the
release build (different signature). Uninstall first:

```sh
adb uninstall com.ideonate.whistle
adb install app/build/outputs/apk/release/app-release.apk
```

## Release signing (one-time)

Keystore lives outside the repo at `~/.android/bttame-release.jks` (path and
alias kept from the original `bttame` keystore — only the property keys in
`local.properties` were renamed to `whistle*`). Credentials live in
`local.properties` (gitignored). To regenerate:

```sh
PW=$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32)
keytool -genkeypair \
  -keystore ~/.android/bttame-release.jks \
  -alias bttame -keyalg RSA -keysize 2048 -validity 36500 \
  -storepass "$PW" -keypass "$PW" \
  -dname "CN=Dan, OU=whistle, O=whistle, L=, ST=, C=GB"
```

Then add to `local.properties`:

```
sdk.dir=/opt/homebrew/share/android-commandlinetools
whistleStoreFile=/Users/dan/.android/bttame-release.jks
whistleStorePassword=<PW>
whistleKeyAlias=bttame
whistleKeyPassword=<PW>
```

Back up `~/.android/bttame-release.jks` somewhere safe — losing it means
future updates require uninstalling the old APK first (can't update across
signing identities).

## Usage

1. Pair the target headset once via system Bluetooth settings (so it shows
   up in the bonded list).
2. Open Whistle → tap **+** in the top bar → pick the headset.
3. Forget the headset in system settings to disable auto-connect (or leave
   it bonded and use the auto-forget toggle to handle this nightly).
4. When you want to use the headset: put it in pairing mode if needed, tap
   **Pair & Connect**. The dialog will guide you through it.

The Settings screen (gear icon) lets you change the daily auto-forget time,
trigger the forget job manually, and tune the wait-before-prompt timing.

## CI

`.github/workflows/build.yml` builds a debug APK on push and uploads it as
an artifact. No signing config — debug only.

# bttame

Tiny Android app to fast-pair a known Bluetooth device by MAC, skipping the
~12 s discovery scan that normal Settings → Pair-new-device does.

Workflow: forget the device in system settings to stop auto-connect, then tap
this app's "Pair & Connect" button to re-bond directly when you actually want
to use it.

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

Release builds need a keystore — see "Release signing" below for one-time setup.

## Install on a tethered Pixel

Phone needs **Settings → System → Developer options → USB debugging** on, and
must show as `device` in `adb devices` (tap "Allow" on the RSA prompt).

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

`-r` reinstalls over an existing install without wiping data. **Note:** if you
previously installed a debug build, `adb` will refuse to replace it with the
release build (different signature). Uninstall first:

```sh
adb uninstall com.bttame
adb install app/build/outputs/apk/release/app-release.apk
```

## Release signing (one-time)

Keystore lives outside the repo at `~/.android/bttame-release.jks`. Credentials
live in `local.properties` (gitignored). To regenerate:

```sh
PW=$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32)
keytool -genkeypair \
  -keystore ~/.android/bttame-release.jks \
  -alias bttame -keyalg RSA -keysize 2048 -validity 36500 \
  -storepass "$PW" -keypass "$PW" \
  -dname "CN=Dan, OU=bttame, O=bttame, L=, ST=, C=GB"
```

Then add to `local.properties`:

```
sdk.dir=/opt/homebrew/share/android-commandlinetools
bttameStoreFile=/Users/dan/.android/bttame-release.jks
bttameStorePassword=<PW>
bttameKeyAlias=bttame
bttameKeyPassword=<PW>
```

Back up `~/.android/bttame-release.jks` somewhere safe — losing it means any
future install of an updated APK will require uninstalling the old one first
(can't update across signing identities).

## Usage

1. Pair the target headset once via system Bluetooth settings (so it appears
   in the bonded list).
2. Open bttame → "Change device" → pick the headset → it remembers the MAC.
3. Forget the headset in system settings to disable auto-connect.
4. When you want to use them: put the headset in pairing mode, tap "Pair &
   Connect" in bttame. Bonding goes straight to that MAC, no discovery scan.

## CI

`.github/workflows/build.yml` builds a debug APK on push and uploads it as an
artifact. No signing config — debug only.

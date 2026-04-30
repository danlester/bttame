# CLAUDE.md

## What this project is

A minimal Android app whose single job is to skip Bluetooth discovery when
re-bonding a known device. Built for a Pixel 7 + Sony WF-C710N workflow:
forget in system settings to suppress auto-connect, tap the app to re-bond
on demand.

## Key design choices (don't undo without thinking)

- **Public APIs only on the connect path.** `BluetoothAdapter.getRemoteDevice(mac)`
  + `BluetoothDevice.createBond()`. No reflection, no `BLUETOOTH_PRIVILEGED`.
  This is the whole reason the app works at all — earlier we ruled out the
  `setConnectionPolicy` / `disconnect` route because those are privileged on
  modern Android.
- **`removeBond()` via reflection** is the one exception (Forget button).
  Hidden but historically callable from app code without privileged perm. If
  Android ever locks this down, fall back to deep-linking the user to system
  Bluetooth settings.
- **No Compose, no Hilt, no extra abstractions.** ~200 lines of Kotlin total.
  The whole point is to be small enough that the build environment is the
  hard part, not the code.
- **`minSdk = 31`** — `BLUETOOTH_CONNECT` runtime permission was introduced
  in Android 12 (API 31). Don't lower it without re-checking the permission
  model.

## Build environment

- JDK 17 at `/opt/homebrew/opt/openjdk@17` (system also has JDK 23 — AGP 8.5
  needs 17).
- Android SDK at `/opt/homebrew/share/android-commandlinetools`, platform 34,
  build-tools 34.0.0.
- Gradle wrapper pinned to 8.7 (`gradle/wrapper/gradle-wrapper.properties`).
  System gradle is 9.5 — only used to bootstrap the wrapper, never to build.
- `JAVA_HOME` and `ANDROID_HOME` are set in `~/.zshrc`.

Build: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

## Files of note

- `app/src/main/java/com/bttame/MainActivity.kt` — UI, perm flow, connect/forget.
- `app/src/main/java/com/bttame/PickerActivity.kt` — first-run device chooser
  (lists currently-bonded devices via `adapter.bondedDevices`).
- `app/src/main/AndroidManifest.xml` — only `BLUETOOTH_CONNECT` is declared.
- `.github/workflows/build.yml` — CI builds debug APK; regenerates wrapper
  jar each run so we don't have to commit it. Also runs `gradle wrapper`
  inline (the wrapper jar IS committed locally because `gradle` was available).
- `app/src/main/res/drawable/ic_launcher_{background,foreground}.xml` +
  `mipmap-anydpi-v26/ic_launcher{,_round}.xml` — adaptive icon. Foreground is
  the Material Bluetooth glyph in white, background is dark navy. minSdk 31
  means we don't need legacy mipmap PNGs.

## Release signing

Self-signed keystore at `~/.android/bttame-release.jks` (outside repo).
Credentials in `local.properties` (gitignored). The release buildType reads
those four `bttame*` properties; if they're absent the signing config is
empty and `assembleRelease` will fail loudly. CI does **not** sign release
builds — it only builds debug.

If `~/.android/bttame-release.jks` is lost, you can't update existing
installs across the signing-identity change — must `adb uninstall com.bttame`
first.

## Things that are out of scope (we considered and rejected)

- Blocking auto-connect via app-side disconnect on `ACTION_ACL_CONNECTED`:
  reflection on `BluetoothA2dp.disconnect()` is privileged on Android 13+ and
  unreliable. MacroDroid does this; we deliberately don't.
- Snapshotting/restoring link keys from `/data/misc/bluedroid/bt_config.conf`:
  requires root.
- Deep-linking to the classic `BluetoothDeviceDetailsFragment` to expose the
  per-profile Media-audio toggle: `SubSettings` is locked to system UID on
  Pixel, can't be launched from third-party apps.

## Known caveats

- A system "Pair with X?" dialog may still appear during `createBond()`
  depending on the headset's SSP behavior. One-tap, but it's there.
- Skipping Fast Pair pairing means no Pixel battery widget / Spatial Audio
  account binding for the device. Headset still works fully.
- There's a small race window between the headset being put in pairing mode
  and bttame issuing `createBond()` — practically irrelevant.

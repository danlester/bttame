# CLAUDE.md

## What this project is

A small Android app — **Whistle**, package `com.ideonate.whistle` — that
re-bonds known Bluetooth devices on demand without going through Settings →
Pair-new-device (which always runs a discovery scan first). Built around a
Pixel 7 + Sony WF-C710N workflow: forget the headset in system settings to
suppress auto-connect, tap Whistle to re-bond when you actually want it.
Also handles the inverse: a daily background job that calls Forget on flagged
devices so they don't auto-connect tomorrow.

The repo dir, gradle project, and signing keystore are still named `bttame` —
only the Android namespace, applicationId, theme, prefs, broadcast action,
and user-facing strings use Whistle. Don't rename the repo for cosmetic
parity; it's intentional.

## Key design choices (don't undo without thinking)

- **Public APIs on the connect path.** `BluetoothAdapter.getRemoteDevice(mac)`
  + `BluetoothDevice.createBond()`. No `BLUETOOTH_PRIVILEGED`. The
  `setConnectionPolicy` / `disconnect` route is privileged on modern Android
  and was ruled out — that's why this app works at all.
- **One reflection call on the connect path: `createBond(int transport)`** to
  force `TRANSPORT_BREDR`. Without it the OS sometimes picks LE for dual-mode
  headsets and the bond hangs. Falls back to public `createBond()` if the
  hidden overload is ever blocked.
- **`removeBond()` via reflection** is the other exception (Forget button +
  the daily auto-forget job). Hidden but historically callable without
  privileged perm. If Android ever locks this down, fall back to deep-linking
  the user to system Bluetooth settings.
- **`isConnected()` via reflection** is used in two places: the auto-forget
  job (skip currently-connected devices) and the pairing dialog (treat ACL up
  as success once already bonded). Same fallback story.
- **No Compose, no Hilt, no extra abstractions.** Plain views + ViewBinding.
  The point is to stay small enough that the build environment is the hard
  part, not the code.
- **`minSdk = 31`** — `BLUETOOTH_CONNECT` runtime permission was introduced
  in Android 12 (API 31). Don't lower without re-checking the permission
  model.
- **`AlarmManager.setInexactRepeating` for auto-forget**, not WorkManager.
  Inexact is fine — missing one night is harmless, the job runs again the
  next day. Inexact also means we don't need `SCHEDULE_EXACT_ALARM`.

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

- `MainActivity.kt` — device list, status row, Connect / Forget / Remove,
  per-device auto-forget checkbox.
- `PairingDialog.kt` — modal pairing flow shown after Connect: spinner →
  banner prompt ("turn it on" / "put it in pairing mode") → "Connected".
  Listens for `ACTION_BOND_STATE_CHANGED` + `ACTION_ACL_CONNECTED`, retries
  `createBond()` until the user cancels or it succeeds.
- `PickerActivity.kt` — Add-device chooser, lists `adapter.bondedDevices`.
- `SettingsActivity.kt` + `SettingsStore.kt` — daily forget time, manual
  "run now", "cancel bond before pairing" toggle, "initial wait" slider
  (the already-bonded ACL grace period before we prompt the user).
- `AutoForgetScheduler.kt` + `AutoForgetReceiver.kt` — daily AlarmManager
  job that calls `removeBond()` on each device flagged with auto-forget,
  skipping any that are currently connected.
- `DeviceStore.kt` — JSON-blob in SharedPreferences for the device list +
  active selection.
- `DeviceIcons.kt` — maps `BluetoothClass` to drawable + resolves
  `device.alias ?: device.name` for display.
- `AndroidManifest.xml` — only `BLUETOOTH_CONNECT` is declared.
- `.github/workflows/build.yml` — CI builds debug APK; regenerates wrapper
  jar each run so we don't have to commit it.
- `res/drawable/ic_launcher_{background,foreground}.xml` +
  `mipmap-anydpi-v26/ic_launcher{,_round}.xml` — adaptive icon. minSdk 31
  means no legacy mipmap PNGs.

## Release signing

Self-signed keystore at `~/.android/bttame-release.jks` (outside repo, name
unchanged from before the Whistle rename). Credentials in `local.properties`
(gitignored). The release buildType reads four `whistle*` properties; if
absent, signing config is empty and `assembleRelease` fails loudly. CI does
**not** sign release builds — it only builds debug.

If the keystore is lost, existing installs can't be updated across the
signing-identity change — must `adb uninstall com.ideonate.whistle` first.

## Things that are out of scope (considered and rejected)

- App-side disconnect on `ACTION_ACL_CONNECTED` to block auto-connect:
  reflection on `BluetoothA2dp.disconnect()` is privileged on Android 13+
  and unreliable. The auto-forget job replaces it — different mechanism,
  same goal.
- Snapshotting/restoring link keys from `/data/misc/bluedroid/bt_config.conf`:
  requires root.
- Deep-linking to `BluetoothDeviceDetailsFragment` to expose the per-profile
  Media-audio toggle: `SubSettings` is locked to system UID on Pixel.

## Known caveats

- A system "Pair with X?" dialog may still appear during `createBond()`
  depending on the headset's SSP behavior. One-tap, but it's there.
- Skipping Fast Pair means no Pixel battery widget / Spatial Audio account
  binding. Headset still works fully.
- Auto-forget is best-effort: if Bluetooth is off, the device is already
  connected, or the alarm is delayed past midnight, we just wait for the
  next day's run.

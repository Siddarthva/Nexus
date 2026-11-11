# NEXUS TV Remote: Release Notes

## Version 1.0.0 (versionCode 1)

First release build: signed, with R8 minification and resource shrinking enabled. The app ID is `com.example.nexusremote`. minSdk is 26 (Android 8.0) and targetSdk is 37 (Android 17).

> **Hardware status:** this release has **not** been run on a TV or on a phone. See "Known limitations" and `docs/hardware-test-checklist.md`.

### Features

- **Pair with TV:** step-by-step instructions for Haier and other Google TVs, then Wireless Debugging pairing with IP address, pairing port, connect port and six-digit code. Inputs are validated, and pairing and connection progress are shown.
- **Home:** the active TV with live connection status, Connect / Disconnect / Pair again, and quick links. Shows a setup prompt when no TV is saved.
- **Remote:** a large circular D-pad with OK, plus Back, Home, Apps, volume up/down, mute, play/pause, rewind, fast-forward, and Power with an optional confirmation.
  - Holding the D-pad or volume repeats about every 150 ms. Queued repeats are dropped when you lift your finger.
  - Optional haptics and click sound.
  - Controls are disabled while disconnected. Failed commands show a message.
- **Apps:** the TV's launchable apps, with search and favourites. Tap to launch.
- **Keyboard:** send text to the focused text field on the TV. Supports letters, digits, space and basic punctuation, up to 300 characters.
- **Settings:** saved TVs (reconnect, rename, edit connect port, forget with confirmation), haptics, sound and power-confirmation toggles, an "I can't find this setting" help section, and a privacy statement.
- **Quick Settings tile:** opens the Remote screen. Nothing runs in the background.
- **Reconnect after a TV reboot:** if the saved connect port stops answering, the app looks up the new one over mDNS and retries once.
- **Security:**
  - Only a fixed allowlist of shell commands can be sent.
  - Each TV gets its own RSA-2048 host key, encrypted with an Android Keystore AES-256-GCM key. Forget deletes the TV's key.
  - The pairing code is never stored.
  - App data is excluded from backups.
  - No analytics, crash reporting or ads. No data leaves the device.

### Permissions

| Permission | Why it is needed |
|---|---|
| `android.permission.INTERNET` | Opens TCP/TLS sockets to the TV's Wireless Debugging pairing and connect ports on your local Wi-Fi. Nothing is sent to the internet. |
| `android.permission.ACCESS_NETWORK_STATE` | Checks network status so the app can show a clear "can't reach the TV" error. |
| `android.permission.ACCESS_LOCAL_NETWORK` | Android 17 (API 37) blocks connections to local-network devices for apps targeting API 37+ unless this permission is granted. It is requested at runtime only on Android 17 and newer, and only when you pair or connect. Older Android versions ignore it. |

The merged manifest also contains `com.example.nexusremote.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. AndroidX Core (`androidx.core:core`) adds it automatically. It is a *signature*-level permission the app defines for itself, to protect its own non-exported broadcast receivers on older Android versions. It grants no access to device data or features, and you are never asked to approve it.

### Known limitations

- The TLS connection to the TV accepts any certificate from the TV. The phone does not verify the TV's identity (see docs/adr-001-adb-library.md).
- The pairing code exists briefly as a String in the text field before it is copied, and cannot be wiped.
- Pairing, TLS connect, shell commands, app listing, and mDNS port lookup are UNVERIFIED on hardware.
- The release build has not been tested on a device. Run docs/hardware-test-checklist.md on the target TV before distributing.

Other limitations:

- Only the Haier Google TV is the target device, and it has not been tested. R8-minified behaviour has likewise not been checked at runtime.
- **Still UNVERIFIED-ON-HARDWARE** (full list in `README.md`):
  - how wrong codes, expired codes and revoked authorization are detected and mapped to error codes
  - app launch via `monkey`
  - key interoperability with adbd
  - the TCP probe before connecting
  - detecting an idle connection drop
  - the Android 17 local-network permission prompt
  - the Haier menu path in the pairing instructions
- The TV cannot be powered on over ADB once it is asleep or off.
- Text input is basic ASCII only: no emoji, accents or non-Latin scripts.
- App names come from a small built-in list. Other apps show a name derived from the package name, and no icons are shown.
- adb-kt's TLS connect has no internal connect timeout. A timed probe runs first, but a TV that accepts TCP and then stalls can tie up a background thread.
- Automated tests (see `docs/test-plan.md`) use fakes for all ADB traffic and do not verify behaviour on a real TV.
- The ADB library (adb-kt v1.0.13, Apache-2.0) is young, has one maintainer, and is distributed through JitPack.

### Build provenance

- AGP 9.5.1, Gradle 9.6.0, Kotlin 2.4.21, Compose BOM 2026.09.00.
- R8 keep rules are in `app/proguard-rules.pro`. adb-kt also ships its own BouncyCastle keep rules.
- Signed with the release key `nexus-release`. The keystore lives outside the repository.

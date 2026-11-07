# Seefi for Android

A native Android app that receives photos from Wi-Fi SD cards (Eye-Fi Mobi and Pro/X2
series). Connect your phone to your card's network and your shots are transferred
straight to the device and saved to your gallery - the Android counterpart of
[seefi-ios](https://github.com/unicornops/seefi-ios).

## Features

- **Receive photos** from Wi-Fi SD cards (Mobi and Pro/X2 series) while the app runs
  a small foreground service
- Photos are saved to **Pictures/Seefi** through MediaStore, so they appear in the
  system gallery and other apps
- **In-app gallery** to browse, open and delete received photos
- Card configuration for **Pro/X2 upload keys** and the card's Wi-Fi credentials
- Best-effort **Wi-Fi network suggestion** so the OS can join the card's network

## Requirements

- Android 10 (API 29) or newer
- A physical device on the card's Wi-Fi network (an emulator has no Wi-Fi radio)

## Protocol

The app implements the Wi-Fi SD card SOAP protocol on port 59278:

- `StartSession` - authentication handshake
- `GetPhotoStatus` - card verification before upload
- `Upload` - multipart TAR file upload
- `MarkLastPhotoInRoll` - end of roll notification

Credential computation follows the Mobi (zero key) and Pro (`upload_key`)
variants.

## Development

```sh
./gradlew assembleDebug     # build
./gradlew testDebugUnitTest # run unit tests
./gradlew installDebug      # install on a connected device
```

The Android SDK is auto-downloaded by Gradle when `ANDROID_HOME` is set and
licenses are accepted. `version.txt` at the repository root is the release
version (kept in sync by [release-please](https://github.com/googleapis/release-please)).

## Releases

Releases are automated with [release-please](https://github.com/googleapis/release-please-action).
Use [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, etc.)
in your commits; release-please creates Release PRs and, when merged, builds the
release APK and attaches it to the GitHub release.

The APK is signed when repository secrets are configured:

| Secret | Description |
|--------|-------------|
| `SEEFI_KEYSTORE_BASE64` | A keystore (.jks/.keystore), base64-encoded |
| `SEEFI_KEYSTORE_PASSWORD` | Keystore password |
| `SEEFI_KEY_ALIAS` | Key alias |
| `SEEFI_KEY_PASSWORD` | Key password (defaults to the keystore password) |

Without the secrets, an unsigned release APK is attached (signed with debug keys
is intentionally avoided). To create a base64 keystore:

```sh
keytool -genkey -v -keystore seefi.keystore -alias seefi -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 seefi.keystore
```

## References

- [eyefiserver2](https://github.com/dgrant/eyefiserver2)
- [node-eyefimobiserver](https://github.com/michaelbrandt/node-eyefimobiserver)
- [EyeFi Protocol](https://github.com/tachang/EyeFiServer/blob/master/Documentation/EyeFi%20Protocol.txt)

## License

See LICENSE file (GPL-3.0), same as seefi-ios.

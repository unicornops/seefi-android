# Seefi Android - AI Agent Instructions

Project-level guidance for AI coding assistants.

## Project Overview

Seefi for Android receives photos from Wi-Fi SD cards (Eye-Fi Mobi and Pro/X2
series). The phone joins the card's network; the card then pushes photos to the
app's EyeFi server on TCP port 59278.

## Architecture

- `protocol/` - pure Kotlin, no Android dependencies, fully JVM-testable:
  - `EyeFiServer` - minimal HTTP/1.1 server (ServerSocket) implementing the
    SOAP protocol: StartSession, GetPhotoStatus, MarkLastPhotoInRoll, multipart
    Upload
  - `CredentialCalculator` - MD5 credentials (Mobi zero key / Pro upload_key)
  - `TarExtractor` - ustar reader extracting image entries
  - `MultipartParser` - lenient multipart/form-data parser
  - `SoapMessages` - SOAP request parsing and response rendering
- `data/` - `SettingsRepository` (DataStore, JSON), `PhotoStore` (MediaStore
  Pictures/Seefi), card/Wi-Fi models
- `service/` - `ReceiveServerService` foreground service owning the server
- `server/` - `ServerState` UI-facing state shared between service and UI
- `wifi/` - `WifiCardConnector` (WifiNetworkSuggestion)
- `ui/` - Jetpack Compose (Material 3): Receive / Gallery / Settings tabs

## Tech Stack

- Kotlin, Jetpack Compose, Material 3, MVVM (ViewModel + StateFlow)
- Manual constructor DI via `AppContainer` (no DI framework)
- DataStore Preferences, MediaStore, kotlinx.serialization, Coil

## Conventions

- `minSdk = 29` - rely on scoped storage; no legacy storage branches
- Keep `protocol/` free of `android.*` imports; unit-test it on the JVM
- UI strings live in `res/values/strings.xml`; protocol wire strings stay inline
- Conventional Commits - release-please drives CHANGELOG and version.txt

## Versioning

`version.txt` at the root is the single version source;
`app/build.gradle.kts` derives `versionName`/`versionCode` from it.
Do not edit the version by hand - release-please manages it via release PRs.

## Testing

```sh
./gradlew testDebugUnitTest
```

- `protocol/` has exhaustive JVM unit tests including a socket-level
  integration test (`EyeFiServerIntegrationTest`) that runs the real server
  on an ephemeral port
- Device/emulator behaviour (Wi-Fi, MediaStore, foreground service) needs a
  physical device; the card protocol itself never does

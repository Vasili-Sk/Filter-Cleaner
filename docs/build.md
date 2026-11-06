# Build Filter Cleaner

## Requirements

- JDK 17.
- Android SDK 35 and Build Tools 35.0.0.
- `ANDROID_HOME`, or an untracked `local.properties` file with `sdk.dir`.

The Gradle wrapper provides Gradle 8.11.1. On Windows, use `gradlew.bat` in place of `./gradlew`.

## Development build

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Signed release build

Set these environment variables in the build process. Do not commit their values:

| Variable | Value |
| --- | --- |
| `FILTER_CLEANER_KEYSTORE_PATH` | Path to the private PKCS12 keystore. |
| `FILTER_CLEANER_STORE_PASSWORD` | Keystore password. |
| `FILTER_CLEANER_KEY_ALIAS` | Signing key alias. |
| `FILTER_CLEANER_KEY_PASSWORD` | Private key password. |

Set all four variables, or leave all four unset. Without them, Gradle can build an unsigned release; do not publish that APK.

```sh
./gradlew testDebugUnitTest testReleaseUnitTest lintRelease assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

Before publication:

1. Verify the APK with Android Build Tools `apksigner verify`.
2. Check its package, version, and minimum Android version with `aapt dump badging`.
3. Confirm that the APK is not debuggable.
4. Confirm the release certificate fingerprint.
5. Create a SHA-256 checksum for the exact file to upload.

## Key storage and updates

Keep the signing key and its passwords outside the repository. Back them up securely. Do not upload them as release assets, paste them into logs, or add them to source control.

All future releases must use the same release key for normal Android updates.

The v0.2.2 preview used a debug key. Version 1.0.0 uses a dedicated release key. A preview installation must be removed before the release can be installed. Do not uninstall a connected user's app automatically.

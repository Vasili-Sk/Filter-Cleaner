# Filter Cleaner

An MIT-licensed Android app that resets the NFC usage counter on compatible Xiaomi air-purifier filters. It uses raw Android `NfcA` commands. It does not need NFC Tools or a password website.

**The app does not physically clean or restore the filter. Replace the filter when necessary.**

## Use

1. Open **Filter Cleaner** with NFC switched on.
2. Touch the filter tag with your phone. The app detects it automatically.
3. Read the detected serial number, product code, and raw usage counter.
4. Keep the tag in position and tap **Cleanup filter** once.
5. Wait for **Reset verified**. The app writes zero to page 8 and reads it back.

There is no separate scan button, confirmation dialog, or second tag tap. The Cleanup filter button is the explicit reset confirmation. Discovery and presence checks never write.

The app disables the button when the tag is removed, the counter is zero, or a previous reset result is uncertain. After an uncertain result, touch the same tag again for a successful automatic read before another reset.

The screen contains only the status, filter details, and **Cleanup filter** button. The product code is informational, not a compatibility restriction.

If a read fails, the button changes to **Retry read**. Authentication is not retried automatically for that UID.

## Requirements

- Android 8.0 (API 26) or later.
- An NFC-capable phone with NFC switched on.
- A compatible Xiaomi filter tag with a seven-byte UID, the documented password scheme, and a usage counter at page 8.

Only the `NfcA (ISO 14443-3A)` interface is selected. The app does not check chip versions, restrict manufacturers, or require known product codes. NfcA support alone does not prove a compatible memory layout. Do not use this operation on unrelated NFC tags.

## What the data means

The serial shown is the NFC UID used to calculate the password. The raw counter is an unsigned little-endian value from page 8. Zero means no usage is recorded; it does not indicate filter condition.

A reliable hours or remaining-life conversion is not established. The app does not invent one. See [`docs/counter-data.md`](docs/counter-data.md) for the research and the distinction between NFC data and the purifier's hours API.

## Build and test

Use JDK 17 and an Android SDK with API 35. The official wrapper uses Gradle 8.11.1.

Set `ANDROID_HOME` to the SDK directory, or set `sdk.dir` in an untracked `local.properties` file.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

- Standard APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Current local named copy: `dist/Filter-Cleaner-debug.apk`.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If streamed installation fails on the phone, use `adb install --no-streaming -r` with the APK path.

The APK is debug-signed. A public release needs a separate release signing key. Do not commit signing keys.

## Safeguards and privacy

- Password authentication and strict response-length checks.
- Only one permitted write command: `A2 08 00 00 00 00`.
- Write ACK validation and read-back verification.
- No writes to passwords, lock bytes, configuration, or other pages.
- No automatic authentication or write retries.
- Serialized NFC I/O. Presence polling reads only `30 08` on the existing authenticated connection.
- Cancellation and backgrounding invalidate the connection and later commands.
- No Internet permission, analytics, password logging, or stored tag history.
- Android can retain the UID of an interrupted reset in saved activity state for recovery.

## Validation

53 unit tests pass. Android lint reports no issues. The app is installed on the connected Android 15 phone. Authentication and read-only capture succeeded on serial `5396F86401A700`, product `JDA0`, counter `21,861,465`. No physical reset or purifier result has been verified.

See [`docs/validation.md`](docs/validation.md) and [`docs/reset-procedure.md`](docs/reset-procedure.md).

## License

[MIT](LICENSE). Code and tests are original. Protocol references do not relicense external code. No GPL implementation code or tests are included.

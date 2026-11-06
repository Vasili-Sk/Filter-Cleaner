# Filter Cleaner — validation

## Software checks

The Android app builds with JDK 17, Gradle 8.11.1, SDK 35, and Build Tools 35.0.0.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

- 39 protocol tests pass.
- 7 interrupted-reset recovery tests pass.
- 7 tag-data snapshot tests pass.
- No skipped tests, failures, or errors.
- Android lint reports no issues.
- The debug APK is signed. It is not a production release.
- App label: `Filter Cleaner`. Application ID: `dev.filtercleaner`.
- Minimum API: 26. Target API: 35.
- App permission: `android.permission.NFC` only.

The build environment required command-local HTTP/HTTPS proxy settings. No proxy configuration was added to the project.

## Phone and tag checks

The APK was installed and opened on the connected `PJZ110` phone, running Android 15. Streamed installation failed; non-streamed installation succeeded.

The current automatic-scan flow authenticated and read the physical tag:

- UID: `5396F86401A700`.
- Product code from optional pages 4–5: `JDA0`.
- Page 8: `59 94 4D 01`.
- Unsigned little-endian counter: `21,861,465`.
- Pages 9–11: zero.

The read-only data panel in an earlier diagnostic build was opened through ADB UI automation. That panel was subsequently removed from the simplified screen. No Cleanup filter tap or reset command was sent. Pages containing passwords, configuration, and lock bytes were not read or written by the diagnostic capture.

The earlier chip-version restriction rejected `00 53 04 02 01 00 0F 03`. That restriction was removed. The app now selects NfcA directly and does not require a chip or product allowlist.

## Coverage and limits

Protocol tests cover password values, raw command order, two-byte authentication responses, unsigned counters, strict read lengths, no automatic retries, authenticated polling, already-zero counters, write ACKs, and uncertain write outcomes.

Recovery tests verify that another UID cannot clear an unresolved reset or replace its affected UID.

The automatic flow has been checked on the phone for tag discovery, authenticated reads, a detected serial/counter, an enabled Cleanup filter button, and optional raw data display. Version 0.2.2 shows the product code in the main details card; it builds, passes 53 tests, and is installed on the phone. ADB UI inspection confirms that the removed headings are absent and Cleanup filter is the last content item. Physical reset, removal/rotation stress tests, and the purifier's response are not yet verified. Unit tests do not replace those checks.

The raw counter cannot yet be reliably converted to hours. See [`counter-data.md`](counter-data.md).

## Remaining hardware tests

1. Keep the known tag against the phone. Confirm its detected UID.
2. If a reset is appropriate, tap **Cleanup filter** once.
3. Confirm **Reset verified** and a zero counter.
4. Remove and retouch the tag. Confirm that the automatic read still shows zero.
5. Install the filter and check the purifier display.
6. Check removal, screen lock, and rotation during reads and during a reset. An interrupted write must not be reported as verified.

A zero counter does not prove that the physical filter is clean or safe.

## APK paths

- Build output: `app/build/outputs/apk/debug/app-debug.apk`.
- Local named copy: `dist/Filter-Cleaner-debug.apk`.

The named copy is ignored by version control and must be refreshed after another build.

# Filter Cleaner — validation

## Software checks

The Android app builds with JDK 17, Gradle 8.11.1, SDK 35, and Build Tools 35.0.0.

```sh
./gradlew testDebugUnitTest testReleaseUnitTest lintRelease assembleRelease
```

- 39 protocol tests pass.
- 7 interrupted-reset recovery tests pass.
- 7 tag-data snapshot tests pass.
- All 53 tests pass for both debug and release builds.
- No skipped tests, failures, or errors.
- Release lint reports no issues.
- Version 1.0.0 is a release build, signed with a dedicated release key. It is not debuggable.
- APK signing verification passes. Certificate SHA-256: `9a67d23bfd14b1e29231145c8588c3ea66b56dc79cf586e624acda42dbff45be`.
- Release signing uses environment variables. See [`build.md`](build.md).
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

The automatic flow has been checked on the phone for tag discovery, authenticated reads, a detected serial/counter, an enabled Cleanup filter button, and optional raw data display. Version 0.2.2 shows the product code in the main details card; it builds, passes 53 tests, and is installed on the phone. ADB UI inspection confirms that the removed headings are absent and Cleanup filter is the last content item. The user subsequently confirmed that the filter reset is verified and works. This is user-reported hardware validation, separate from the assistant's observed read-only checks. Removal/rotation stress tests are not yet verified. Unit tests do not replace those checks.

The raw counter cannot yet be reliably converted to hours. See [`counter-data.md`](counter-data.md).

## Hardware test checklist

1. Keep the known tag against the phone. Confirm its detected UID.
2. If a reset is appropriate, tap **Cleanup filter** once.
3. Confirm **Reset verified** and a zero counter.
4. Remove and retouch the tag. Confirm that the automatic read still shows zero.
5. Install the filter and check the purifier display.
6. Check removal, screen lock, and rotation during reads and during a reset. An interrupted write must not be reported as verified.

A zero counter does not prove that the physical filter is clean or safe.

## APK paths

- Release build output: `app/build/outputs/apk/release/app-release.apk`.
- Local named release copy: `dist/Filter-Cleaner.apk`.
- The preview APK used an Android debug key. It cannot be updated in place with the new release key.
- Remove the preview before installing the release. Keep the release key for future updates.

The named copy is ignored by version control and must be refreshed after another build.

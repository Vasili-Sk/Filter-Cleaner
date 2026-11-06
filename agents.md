# Filter Cleaner — agent context

## Mandatory agent rules

- Edit this file only with explicit permission from the user.
- Keep all responses to the user concise.
- Write all responses to the user in ASD-STE100 Simplified Technical English.
- Use **Filter Cleaner** as the app and repository name everywhere. Use `dev.filtercleaner` for the Android namespace and application ID.
- The name does not mean that the app cleans the physical filter. It resets only the NFC usage counter.

## Purpose

Build a small, standalone Android app that resets the usage counter on compatible Xiaomi air-purifier filter NFC tags. It should use Android's `NfcA` API directly, without NFC Tools, a web password generator, or a Flipper Zero.

Resetting the NFC counter **does not clean, repair, or restore filter media**. Never suggest that a reset makes a worn filter safe or measures its remaining filtration capacity.

## Current status

- New repository; no working app or hardware validation yet.
- Root Gradle scaffolding exists in `build.gradle.kts` and `settings.gradle.kts`.
- Intended stack: Kotlin, Android SDK, `NfcA`, a small native Android UI.
- Initial build configuration selects Android Gradle Plugin 8.9.2 and Kotlin 2.1.20.
- Intended build baseline: JDK 17, Gradle 8.11.1, compile/target SDK 35, minimum SDK 26. These app-level settings are not implemented yet.
- Do not claim an APK, successful build, or successful physical reset until actually verified.
- User requested these context/protocol documents before further implementation or builds.

## Scope and workflow

1. Read and validate a supported NFC tag without writing to it.
2. Show its UID, product code, and raw usage counter; distinguish zero from nonzero rather than inventing a remaining-life percentage.
3. Ask for explicit reset confirmation.
4. Require the same tag again if the read and reset are separate scans.
5. Authenticate, zero **only page 8**, and read back to verify.
6. Report verified success, rejection, or an uncertain write outcome accurately.

See [`docs/reset-procedure.md`](docs/reset-procedure.md) for the exact algorithm, commands, response checks, memory map, and references. Treat it as the protocol specification, not this short overview.

## Implementation boundaries

- Keep password derivation and protocol sequencing in pure Kotlin, separate from Android NFC/lifecycle code, so they can be unit-tested with a fake transport.
- Planned engine path: `app/src/main/java/dev/filtercleaner/nfc/FilterProtocol.kt`.
- Planned test path: `app/src/test/java/dev/filtercleaner/nfc/FilterProtocolTest.kt`.
- Run NFC I/O off the UI thread. Close connections on cancellation/backgrounding and suppress stale callbacks.
- Use reader mode for foreground scanning. Do not silently write when a tag approaches the phone.
- Fail closed on unsupported tags, failed authentication, malformed responses, or unknown filter families.
- Initially permit writes only for observed product codes `AP11`, `JDA0`, and `JD26`. This is a conservative app policy, not a universal list of Xiaomi filters.
- Do not write lock, configuration, password, PACK, manufacturer, or UID pages.
- Never brute-force passwords or automatically retry authentication. Tag configurations can limit failed attempts.
- Do not log derived passwords or upload tag identifiers. No network permission is needed for the app's NFC functionality.
- Hardware compatibility varies by tag and phone. Published purifier-model lists are community reports, not this project's test results.

## Validation expectations

- Unit-test password golden vectors, unsigned byte handling, counter endianness, exact commands, response lengths, and all write guards.
- Test rejected authentication, unsupported versions/product codes, NAKs, tag loss, already-zero counters, and failed verification.
- Distinguish a pre-write failure from a write that may have succeeded before contact was lost.
- Once tooling and app scaffolding exist, run `./gradlew testDebugUnitTest lintDebug assembleDebug` (`gradlew.bat` on Windows).
- A successful mocked test/build does not replace a real NFC reset and purifier check. Record hardware testing separately.

## Sources and licensing

- This project uses the MIT license. See `LICENSE`.
- Implement the protocol independently from documented data formats, NFC commands, and mathematical operations.
- Do not copy or translate GPL source code or tests into this project. The MIT license does not replace an upstream license.
- Use the sources below for protocol research. Check the applicable license before copying any material.

- Complete Flipper implementation and protocol notes: <https://github.com/khmm12/flipper-xiaomi-filter-reset> (`src/core/`, `src/nfc/`, `docs/protocol.md`, `test/`). GPL-3.0-or-later.
- Original reverse engineering and tag dumps: <https://github.com/Flamingo-tech/xiaomi-air-purifier-reverse-engineering>.
- Password generator/manual workflow: <https://github.com/logikaljay/xiaomi-air-purifier-filter-reset>.
- User-supplied article explaining the password and phone workflow: <https://unethical.info/2024/01/24/hacking-my-air-purifier/>.

Keep source references for protocol facts. Write original implementation code and tests. Do not assume every referenced repository has the same license.

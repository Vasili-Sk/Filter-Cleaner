# Filter Cleaner — NFC reset procedure

## Scope

Use Android `NfcA (ISO 14443-3A)` directly. This is the I/O class specified in the user-supplied article:

<https://unethical.info/2024/01/24/hacking-my-air-purifier/>

`NfcA.transceive` sends the raw command bytes. It does not require an NDEF tag. Android handles radio framing and CRC bytes. Do not append CRC bytes to the commands below.

The app does not issue `GET_VERSION`, detect chip models, check a manufacturer byte, or restrict product codes. These earlier app restrictions were removed at the user's request. They were app logic, not restrictions imposed by Android.

NfcA support alone does not prove a compatible memory layout. Use this operation only on a Xiaomi filter tag that uses the documented password scheme and a usage counter at page 8. Do not use it on payment cards, access cards, or unrelated NFC tags.

A reset changes only the recorded counter. It does not clean, repair, or test the filter media.

## Password calculation

Obtain the seven raw UID bytes from Android's `Tag.id`. Do not reverse them or hash their printed hexadecimal representation.

```text
h = SHA1(uidBytes)                    // 20 bytes
base = unsigned(h[0])
password[0] = h[(base +  0) mod 20]
password[1] = h[(base +  5) mod 20]
password[2] = h[(base + 13) mod 20]
password[3] = h[(base + 17) mod 20]
```

In Kotlin, convert the first digest byte with `h[0].toInt() and 0xFF` before use as an index.

| UID | Password |
| --- | --- |
| `04A03CAA1E7080` | `CD91AFCC` |
| `04112233445566` | `EC9805C8` |
| `0404D972BA6C81` | `103AF4A2` |
| `047FD6E2EA6C80` | `7D295EE7` |
| `04FA3CAA1E7080` | `95BF2720` |
| `5396F86401A700` | `67DCBB76` |

For the user-supplied serial `5396F86401A700`, the SHA-1 digest is `AD62DB6EE1A4BBDA0BE776AC606723724B45DC0B`. The resulting password matches the supplied command:

```text
1B67DCBB76,3008,A20800000000
```

Do not brute-force passwords or try alternate byte orders automatically. A tag can limit failed authentication attempts.

## Commands and response checks

| Operation | Command | Response |
| --- | --- | --- |
| Authenticate | `1B` + four password bytes | Exactly two PACK bytes. The value is tag-specific; it need not be `00 00`. |
| Read counter | `30 08` | Exactly 16 bytes: pages 8 through 11. |
| Zero counter | `A2 08 00 00 00 00` | One ACK byte with low nibble `0xA`. |
| Verify | `30 08` | Exactly 16 bytes; the first four bytes must be zero. |

A one-byte NAK, a malformed response, or an I/O exception is not authentication success. A failed authentication must stop the operation without a write.

Page 8 is a four-byte unsigned little-endian usage counter. Decode it into a Kotlin `Long`. For example, `DA 67 01 00` is `92122`. The maximum value is `4294967295`.

Zero means that the tag records no usage. The app must not calculate a remaining-life percentage. Counter units and the purifier's model-specific conversion are not established for all models.

## Read-only check

1. Open the app. Foreground NFC reader mode starts automatically.
2. Accept only a tag for which Android provides `NfcA`.
3. Require a seven-byte UID for the documented calculation.
4. Connect on a worker thread.
5. Calculate the password and send `PWD_AUTH` once.
6. Validate the two-byte PACK response.
7. Send `30 08` and validate the 16-byte response.
8. Decode the counter from the first four bytes.
9. Optionally read pages 4–11 to obtain the product code. Failure of this optional read is not a product restriction.
10. Show the UID and counter. Keep the connection open while the tag is held in place.
11. Poll `30 08` on the same serialized worker to detect loss of contact. Do not authenticate again during these polls.

This automatic path must not send `A2` or `GET_VERSION`. The product code is informational only.

## Confirmed reset

1. Complete the automatic read-only check.
2. Show the UID and counter, with the counter-only warning and **Cleanup filter** button.
3. The user taps **Cleanup filter**. This is the explicit confirmation; no dialog or second tag tap is required.
4. Require the same active connection and UID. Disable the button while writing.
5. On the same worker, repeat authentication and the counter read before writing.
6. If the counter is already zero, do not write.
7. Otherwise, send `A2 08 00 00 00 00` once.
8. Validate the ACK.
9. Send `30 08` again. Require four zero bytes at the start of the response.
10. Report a verified reset only after step 9 succeeds.
11. Continue read-only presence checks until the tag is removed or the app is paused.
12. Reinstall the filter and check the purifier display separately.

For the user-supplied UID:

```text
1B 67 DC BB 76        -> two PACK bytes             Authenticate
30 08                 -> 16 bytes                  Counter before reset
A2 08 00 00 00 00     -> 0A                        Zero page 8
30 08                 -> 00 00 00 00 ...           Verify
```

Do not write the UID, lock bytes, capability container, password, PACK, configuration pages, or other data pages.

## Cancellation and failures

- Stop on failed authentication or a malformed pre-write read. Do not retry automatically.
- After any write attempt, a lost connection or invalid response means that the result is not verified. Do not claim that the tag is unchanged.
- Require a successful read-only check of the affected UID before another reset. A check of a different tag must not clear this requirement.
- Android saved activity state can retain that UID during activity recreation. Do not upload it or log the password.
- Close the connection on cancellation or backgrounding. Reject stale callbacks and block later commands from the cancelled session.
- Never automatically repeat a write.

## Observed hardware data

A read-only diagnostic on the connected Android 15 phone received this version response from the user's tag:

```text
00 53 04 02 01 00 0F 03
```

The original exact-NXP version restriction rejected it before authentication. This is why the app no longer uses a chip-version restriction. The response alone does not verify the counter layout, password, or successful reset.

## References and license

- User-supplied phone workflow and password explanation: <https://unethical.info/2024/01/24/hacking-my-air-purifier/>
- Original reverse engineering and tag dumps: <https://github.com/Flamingo-tech/xiaomi-air-purifier-reverse-engineering>
- JavaScript password generator: <https://github.com/logikaljay/xiaomi-air-purifier-filter-reset>
- Additional protocol research and published test values: <https://github.com/khmm12/flipper-xiaomi-filter-reset>

This project uses the MIT license. Implementation code and tests are original. Protocol references do not relicense upstream code. Do not copy or translate GPL implementation code or tests into the MIT project.

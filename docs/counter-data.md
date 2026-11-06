# Filter counter data and time units

## What the app can read

The NFC operation `30 08` returns four pages. The first four bytes are page 8: an unsigned 32-bit little-endian usage counter.

Examples from the published reverse-engineering data:

| Page 8 | Counter | Reported filter life in the source |
| --- | ---: | --- |
| `DA 67 01 00` | 92,122 | 99% |
| `A4 29 02 00` | 141,732 | 98% |
| `00 00 00 00` | 0 | Fresh/reset |

These samples show an increasing usage value. They do not establish its time unit or a universal full-life threshold. Do not divide it by 3,600 and label the result as hours without further evidence.

The tag's UID can be shown as the serial number used for password calculation. It is not necessarily the same as a manufacturing serial stored elsewhere on the tag.

## What the sources establish

The user-supplied article identifies page 8 as the reset field and explains the UID-derived password. It does not define the counter unit:

<https://unethical.info/2024/01/24/hacking-my-air-purifier/>

The original reverse-engineering notes identify:

- Page 4: factory ID.
- Page 5: product ID.
- Page 6: manufacturing time.
- Page 7: manufacturing serial.
- Page 8: resettable usage value.

The manufacturing time is not elapsed filter-use time:

<https://github.com/Flamingo-tech/xiaomi-air-purifier-reverse-engineering/tree/main/Filter%20information>

## Hours exist in the purifier API

The public MIOT specification for `xiaomi.airp.mp4` defines a separate **Filter** service:

- `filter-used-time`: unit `hours`, range 0–3500.
- `filter-left-time`: unit `hours`, range 0–5000.
- `filter-life-level`: unit `percentage`.

Source JSON:

<https://miot-spec.org/miot-spec-v2/instance?type=urn:miot-spec-v2:device:air-purifier:0000A007:xiaomi-mp4:1>

This confirms that the purifier can report hours. It does not establish a conversion from NFC page 8 to those API properties. Reading the purifier API would require a separate device connection and is outside the current offline NFC app.

## Actual read from the connected filter

The read-only capture succeeded with UID `5396F86401A700`:

- Pages 4–5 identify product code `JDA0`.
- Page 6 is `00 19 12 05`. A BCD `20YY-MM-DD` interpretation gives **2019-12-05**. This is a candidate manufacturing date, not a verified label value or elapsed use.
- Page 8 is `59 94 4D 01`, which decodes to **21,861,465**.
- Pages 9–11 are zero.

If the counter unit were seconds, the value would be **6,072.63 hours**, or **253.03 days** of continuous operation. This is a conditional calculation, not a confirmed runtime measurement. The app does not display it as hours.

The published binary dumps contain page-6 values `00 20 11 16`, `00 20 11 08`, and `00 20 11 02`. Those are consistent with BCD dates in November 2020, which supports the candidate date interpretation. It still needs confirmation against the physical filter label.

A single NFC snapshot cannot identify the counter's time scale. A paired purifier used-time report or controlled before/after measurements would be needed to test that hypothesis.

## Display policy

Show the detected UID, raw usage counter, and a zero/nonzero state. Do not claim to measure dirt, filter condition, remaining filtration capacity, hours, or life percentage from that counter.

A model-specific time conversion would require documented firmware behavior or paired observations of NFC values and the purifier's used-time report over controlled intervals. Until then, the app must label the counter as raw data.

# Filter Cleaner

Reset the recorded usage counter on compatible Xiaomi air purifier filter tags.

**This app does not clean or repair the filter. Replace the filter when necessary.**

## Download

**[Download Filter Cleaner for Android](https://github.com/Vasili-Sk/Filter-Cleaner/releases/latest/download/Filter-Cleaner.apk)**

Open the downloaded APK file on your phone to install the app.

If you have the preview app, remove it before you install this release.

## Requirements

- Android 8.0 or later.
- A phone with NFC.
- A compatible Xiaomi filter with an NFC tag.

No account or Wi-Fi connection is necessary.

## Use

1. Switch on NFC on your phone.
2. Open **Filter Cleaner**.
3. Hold the filter tag against the phone's NFC antenna.
4. Read the serial number, product code, and usage counter.
5. Tap **Cleanup filter**. Keep the tag in position.
6. Wait for **Reset verified**.

The counter shows recorded usage, not filter condition. The app does not estimate hours or remaining filter life.

If a read fails, tap **Retry read**. If a reset is not verified, read the same tag again before another reset.

## References

These projects provided information about the tag data and reset procedure:

- [Flamingo-tech: Xiaomi air purifier reverse engineering](https://github.com/Flamingo-tech/xiaomi-air-purifier-reverse-engineering)
- [logikaljay: Xiaomi air purifier filter reset](https://github.com/logikaljay/xiaomi-air-purifier-filter-reset)
- [khmm12: Flipper Xiaomi filter reset](https://github.com/khmm12/flipper-xiaomi-filter-reset)
- [Phone reset guide](https://unethical.info/2024/01/24/hacking-my-air-purifier/)

## License

[MIT](LICENSE).

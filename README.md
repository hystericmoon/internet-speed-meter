# Speed Meter (spike)

Android spike: live download/upload speed rendered as the notification small icon,
so it shows in the status bar.

- `SpeedMeterService` — `specialUse` foreground service, polls `TrafficStats` every 1 s,
  pauses while the screen is off.
- `SpeedIconRenderer` — draws ↓ (top) and ↑ (bottom) into a 24dp bitmap.
- Idle hiding — after 5 s below 1 KB/s the notification is reposted on an
  `IMPORTANCE_MIN` channel. The "Transparent-icon fallback" switch in the app instead
  keeps the normal channel and posts a blank icon (`SpikeFlags`).

## Installing from CI

Every push runs `.github/workflows/build-debug-apk.yml` and publishes the APK to the
**debug-latest** release. On your phone's browser open
<https://github.com/hystericmoon/internet-speed-meter/releases/download/debug-latest/speed-meter-debug.apk>
(or Releases → "Latest debug build") and open the downloaded file. The same APK is
also attached to each Actions run as a zipped artifact. Builds are signed with the committed `debug.keystore`, so a
new build installs over the previous one.

## Building locally

`./gradlew assembleDebug` (JDK 17+, Android SDK 35).

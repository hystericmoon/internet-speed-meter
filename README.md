# Speed Meter (spike)

Android spike: live download/upload speed rendered as the notification small icon,
so it shows in the status bar.

- `SpeedMeterService` — `specialUse` foreground service, polls `TrafficStats` every 1 s,
  pauses while the screen is off.
- `SpeedIconRenderer` — draws ↓ (top) and ↑ (bottom) into a 24dp bitmap.
- Idle hiding — after 5 s below 1 KB/s the notification is reposted on an
  `IMPORTANCE_MIN` channel. The "Transparent-icon fallback" switch in the app instead
  keeps the normal channel and posts a blank icon (`SpikeFlags`).

- UI — shadcn/ui's zinc tokens, radii and type (Geist) recreated natively in Compose
  (`ui/Theme.kt`, `ui/Components.kt`); shadcn itself is a React library.

## Auto-update

CI writes `version.json` (versionCode = run number, SHA-256 of the APK) next to the APK
in the `debug-latest` release. The app checks it on launch, and every 6 h on Wi-Fi
in the background (WorkManager). Installs go through `PackageInstaller`:

1. The first update asks you to allow "Install unknown apps" for Speed Meter and to
   confirm the install.
2. After that Speed Meter is the installer of record, so on Android 12+ updates install
   silently. If the system still wants confirmation, you get a notification.
3. If the meter was running, `PackageReplacedReceiver` restarts it after the update.

Android only accepts an update signed with the same key (the committed `debug.keystore`),
and the app also checks the SHA-256 before installing.

## Installing from CI

Every push runs `.github/workflows/build-debug-apk.yml` and publishes the APK to the
**debug-latest** release. On your phone's browser open
<https://github.com/hystericmoon/internet-speed-meter/releases/download/debug-latest/speed-meter-debug.apk>
(or Releases → "Latest debug build") and open the downloaded file. The same APK is
also attached to each Actions run as a zipped artifact. Builds are signed with the committed `debug.keystore`, so a
new build installs over the previous one.

## Building locally

`./gradlew assembleDebug` (JDK 17+, Android SDK 35).

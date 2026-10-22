# Speed Meter

An Android app that shows your live download and upload speed **as the status bar icon**,
so it's always visible next to the clock and battery. It hides itself when the network
is idle, and keeps itself up to date from this repository's builds.

Kotlin · Jetpack Compose · minSdk 26 (Android 8.0) · targetSdk 35 (Android 15)

| Running, update downloading | Dark, first update needs permission | Notifications denied |
|---|---|---|
| ![](app/src/test/screenshots/com.example.speedmeter.ScreenshotTest.runningDownloadingUpdate.png) | ![](app/src/test/screenshots/com.example.speedmeter.ScreenshotTest.runningDarkNeedsInstallPermission.png) | ![](app/src/test/screenshots/com.example.speedmeter.ScreenshotTest.stoppedNotificationsDenied.png) |

These images are the screenshot-test baselines, so they always match the current code.

---

## Contents

- [Install on your phone](#install-on-your-phone)
- [Using the app](#using-the-app)
- [Settings](#settings)
- [How it works](#how-it-works)
- [Auto-update](#auto-update)
- [Design system](#design-system)
- [Accessibility](#accessibility)
- [Testing on a device](#testing-on-a-device)
- [Development](#development)
- [CI and releases](#ci-and-releases)
- [Project structure](#project-structure)
- [Known limitations and open questions](#known-limitations-and-open-questions)

---

## Install on your phone

1. On your phone, open
   <https://github.com/hystericmoon/internet-speed-meter/releases/download/debug-latest/speed-meter-debug.apk>
   (or the repo's **Releases → Latest debug build**).
2. Open the downloaded file. If asked, allow your browser to install unknown apps.
3. Open **Speed Meter** and tap **Start meter**. Allow notifications when asked; on
   Android 13+ the speed can't appear in the status bar without them.

You only do this once. After that the app updates itself (see [Auto-update](#auto-update)).

---

## Using the app

| Where | What you can do |
|---|---|
| **App** | Start/stop the meter, see live speed, change settings, install updates |
| **Status bar** | Two lines: download on top, upload below, e.g. `9.6M` over `287K` |
| **Notification** | Full speeds (e.g. `↓ 9.6 Mbps   ↑ 287 kbps`) and a **Stop** button. Tap it to open the app |
| **Quick Settings tile** | Tap to start/stop. Its subtitle shows live download speed while the shade is open |

The status badge in the app always spells out the state:

| Badge | Meaning |
|---|---|
| **Running** | Measuring; speed shows in the status bar |
| **Idle** | Measuring, but traffic is below your idle threshold, so it's hidden from the status bar |
| **Paused** | Screen is off; measuring is suspended to save battery |
| **Stopped** | Meter is off |

**It stays on.** Once started, the meter comes back by itself after a reboot, after
Android kills it, and after an update. Only **Stop** (in the app, notification or tile)
turns it off for good.

### Adding the Quick Settings tile

- **Android 13+**: App → **App** card → **Quick Settings tile → Add**. The system asks to confirm.
- **Android 12 and older**: pull down Quick Settings, tap the pencil/edit icon, and drag
  **Speed meter** into your tiles.

---

## Settings

### Display

| Setting | Options | Default | Notes |
|---|---|---|---|
| **Units** | Mbps · MB/s | **Mbps** | Mbps (megabits, decimal) is how ISPs and speed tests quote speed. MB/s (megabytes, binary) is what file managers and downloads show. 1 MB/s ≈ 8.4 Mbps. |
| **Hide when idle** | Never hide · Below 8 kbps · Below 100 kbps · Below 1 Mbps | **Below 8 kbps** | Hides from the status bar after **5 seconds** below the threshold (download + upload combined). Comes back the moment traffic rises above it. Raise it if background sync keeps the icon flickering on. |
| **Transparent-icon fallback** | On · Off | Off | How the meter hides (see below). Only shown when hiding is on. |

**Why Mbps by default:** people check the meter against their plan ("100 Mbps") and
speed-test results. Showing MB/s would read 8× smaller than they expect. This follows
Nielsen's *match between the system and the real world* heuristic.

**Two ways to hide**, which is what this spike is testing:

- **Off (default):** the notification moves to a *minimum-importance* channel, which
  Android keeps out of the status bar. Clean, but some phones ignore it for
  foreground-service notifications.
- **On:** stays on the normal channel and draws a fully transparent icon. Works more
  widely, but usually leaves an empty gap in the status bar.

### App

| Setting | Default | Notes |
|---|---|---|
| **Automatic updates** | On | Checks every 6 h on Wi-Fi with battery not low, then downloads and installs. Opening the app always checks, on any network. |
| **Quick Settings tile** | – | Adds the tile (Android 13+) or explains how to add it by hand. |
| **Check now** | – | Checks for an update immediately. |

---

## How it works

```
 TrafficStats (total rx/tx bytes)
        │  every 1 s, only while the screen is on
        ▼
 RateSampler ── bytes/s + "idle for ≥ 5 s?" ──┐
                                              ▼
 SpeedMeterService (foreground, type specialUse)
        ├─ SpeedIconRenderer → 24dp bitmap, two lines → notification small icon (status bar)
        ├─ notification title: full speeds, Stop action
        ├─ idle? → repost on MIN channel, or transparent icon (fallback)
        └─ StateFlow ──► MainActivity (Compose UI) and the Quick Settings tile
```

- **Foreground service (`specialUse`).** Required to keep a notification permanently
  up. Android 14+ needs a declared type; none of the standard types fit a speed meter,
  so it's `specialUse` with a written justification in the manifest.
- **Sampling.** Reads total device bytes from `TrafficStats` once per second and
  divides by the real elapsed time. Counter resets (interface changes) read as zero
  rather than negative.
- **Screen off.** Polling stops entirely. On screen-on the first sample only resets the
  baseline, so data used while the screen was off doesn't show up as one huge spike.
- **Status bar icon.** Android treats small icons as an alpha mask and tints them
  itself, so the bitmap is white on transparent. Text is squeezed horizontally if a
  value is too wide. Every value is at most 4 characters (`0.0K`, `512K`, `9.6M`, `34M`).
- **Low churn.** The notification is only reposted when something visible changes.
- **Staying alive.** `START_STICKY` after a kill. `MeterRestartReceiver` restarts it on
  `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` (both exempt from Android's background-start
  limits). If Android still refuses, opening the app restarts it.

---

## Auto-update

```
 push to default branch ─► CI builds APK (versionCode = run number)
                           └─► release "debug-latest": speed-meter-debug.apk + version.json
                                                                   │
 app open / every 6 h on Wi-Fi ◄── reads version.json ─────────────┘
        │ newer versionCode?
        ▼
 download APK ─► verify SHA-256 ─► PackageInstaller session
        │
        ├─ silent (Android 12+, after the first self-update)
        ├─ system confirm dialog (app open)
        └─ "Tap to finish installing" notification (app in background)
        ▼
 app replaced ─► MeterRestartReceiver restarts the meter if it was on
```

- **First update:** Android asks you once to allow *Install unknown apps* for Speed Meter
  (the app explains this before sending you to Settings), then asks you to confirm.
- **After that:** Speed Meter is the "installer of record", so on Android 12+ updates
  install silently. Some phones still ask; you then get a notification.
- **Safety:** Android only accepts an update signed with the same key (the committed
  debug key), and the app checks the file's SHA-256 against `version.json` first.
- **Feed:** `https://github.com/hystericmoon/internet-speed-meter/releases/download/debug-latest/version.json`

```json
{
  "versionCode": 4,
  "versionName": "0.1.4",
  "commit": "7ce0a7f",
  "apkUrl": "https://github.com/…/releases/download/debug-latest/speed-meter-debug.apk",
  "sha256": "6d99fc20…"
}
```

---

## Design system

shadcn/ui is a React library, so it can't run in a native app. Its design language is
recreated in Compose instead, using the same token names so it maps 1:1 to the web kit.

- **Tokens** (`ui/Theme.kt`): shadcn *zinc* light and dark palettes: `background`,
  `foreground`, `card`, `primary`, `secondary`, `muted`, `mutedForeground`, `border`,
  `input`, `ring`, `destructive`.
- **Radius:** `sm 4 · md 6 · lg 8 · xl 12` dp (shadcn `--radius: 0.5rem`). Buttons use `md`, cards `xl`.
- **Type:** Geist (bundled, OFL licence in `licenses/`) on Tailwind's scale. Live speeds
  use Geist Mono so digits don't jitter as they change.
- **Elevation:** none. Hairline borders, as in shadcn.
- **Components** (`ui/Components.kt`, `ui/Selection.kt`):

| Component | Variants / notes |
|---|---|
| `ShadButton` | default · secondary · outline · ghost · destructive. 44dp visual, 48dp touch target |
| `ShadCard`, `CardHeader` | 1dp border, 12dp radius, 20dp padding |
| `ShadBadge` | default · secondary · outline · destructive, optional status dot |
| `ShadSwitch`, `SettingRow` | 44×24 track. The whole row is the toggle |
| `ShadToggleGroup` | Tabs-style segmented control for 2–3 options (Units) |
| `ShadRadioGroup` | Vertical radio list (Hide when idle) |
| `ShadProgress` | 8dp pill, determinate or indeterminate |
| `ShadAlert` | default · destructive |
| `ShadSeparator` | 1dp `border` line |

Light and dark follow the system setting. Material You (wallpaper colours) is
intentionally off: shadcn is deliberately neutral.

---

## Accessibility

| Area | What's done |
|---|---|
| **Contrast** | Muted text `#71717A` on white = 4.8:1 (AA). shadcn's default red `#EF4444` is 3.8:1 and fails AA for text, so destructive is darkened to `#DC2626` (4.8:1) in light mode and `#F87171` in dark. Radio outlines use `mutedForeground` to meet the 3:1 non-text contrast rule. |
| **Touch targets** | Every control is at least 48dp, including segments and radio rows. |
| **Screen readers** | Each speed reads as one phrase ("Download 9.6 Mbps"). Setting rows are a single switch / radio node. Groups use `selectableGroup`. Update status and progress are live regions. The title is a heading. |
| **Not colour-only** | Status is always text (Running / Idle / Paused / Stopped). |
| **Text scaling** | Tested at 200% (WCAG 1.4.4): above 150%, download and upload stack vertically so values can't collide. |
| **Still to verify** | TalkBack on a real device; the status-bar icon itself has no text alternative beyond the notification content. |

---

## Testing on a device

The spike's open questions can only be answered on real hardware. Please report
**phone model, Android version, and what you saw**:

- [ ] **Icon legibility:** can you read both lines at arm's length, on light and dark wallpaper?
- [ ] **Idle hiding (default):** stay idle 5 s. Does the icon disappear, or does a "running" indicator appear instead?
- [ ] **Transparent fallback:** turn it on and go idle. How big is the gap?
- [ ] **Screen off/on:** after unlocking, does the meter resume without a false spike?
- [ ] **Units:** switch Mbps ↔ MB/s. Do the app, notification and icon all follow within a second?
- [ ] **Threshold:** pick "Below 1 Mbps" while streaming a little. Does it hide as expected?
- [ ] **Reboot:** with the meter on, restart the phone. Does it come back?
- [ ] **Tile:** add it, start/stop from the shade, check the subtitle speed.
- [ ] **Update:** push any change. Is the first update (allow installs + confirm) clear? Is the second silent?
- [ ] **TalkBack:** move through the screen. Does everything make sense spoken?

---

## Development

Requirements: JDK 17+, Android SDK 35.

```bash
./gradlew assembleDebug            # build app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # unit + screenshot tests (compare mode off)
./gradlew verifyRoborazziDebug     # tests + fail if any screenshot changed
./gradlew recordRoborazziDebug     # re-record screenshot baselines after an intended UI change
./gradlew lintDebug                # Android lint
```

### Tests (30)

| Test | Covers |
|---|---|
| `SpeedFormatTest` | Mbps/MB/s conversion, rounding, unit switching, 4-character limit for the icon, threshold labels |
| `RateSamplerTest` | Rate maths, counter resets, 5 s idle hiding, "never hide", changing the threshold at runtime, screen-off reset |
| `UpdateInfoTest` | Parsing `version.json`, only higher versions count as updates |
| `ScreenshotTest` | 9 states of the main screen: light/dark, errors, update flow, MB/s, tile states, 200% font |

Screenshot tests run on the JVM (Robolectric + Roborazzi), no emulator needed.
Baselines are in `app/src/test/screenshots/`.

---

## CI and releases

`.github/workflows/build-debug-apk.yml` runs on **every push**:

1. `verifyRoborazziDebug lintDebug assembleDebug`: tests, screenshot comparison, lint, APK.
2. On failure: uploads `screenshot-diffs` (before / after / diff images) and `reports`.
3. Uploads the APK as a run artifact (any branch).
4. **Default branch only:** updates the `debug-latest` release (APK + `version.json`),
   which is what phones auto-update from. Other branches never reach the updater.

**After an intended UI change** (no computer needed): Actions → *Build debug APK* →
*Run workflow* → tick **Re-record screenshot baselines**. CI records new baselines and
commits them to the branch.

Versioning: `versionCode` = GitHub run number, `versionName` = `0.1.<run>`. Local builds
are `versionCode 1`, so any CI build counts as newer.

Signing: `debug.keystore` is committed on purpose so every build has the same signature
and installs over the previous one. It's a debug key, not a secret, and must not be used
for a store release.

---

## Project structure

```
app/src/main/java/com/example/speedmeter/
├── SpeedMeterService.kt      foreground service: polling, notification, idle hiding
├── RateSampler.kt            pure rate + idle logic (unit-tested)
├── SpeedFormat.kt            Mbps / MB/s formatting, compact icon form
├── SpeedIconRenderer.kt      draws the two-line status bar bitmap
├── SpeedMeterTileService.kt  Quick Settings tile
├── MeterRestartReceiver.kt   restart after reboot / self-update
├── AppPrefs.kt               persisted settings, IdleThreshold presets
├── SpeedMeterApp.kt          Application: update channel + background update schedule
├── MainActivity.kt           permissions, settings intents, wiring
├── MainScreen.kt             the Compose screen
├── ui/
│   ├── Theme.kt              shadcn tokens, radius, Geist type
│   ├── Components.kt         Button, Card, Badge, Switch, Progress, Alert, Separator
│   └── Selection.kt          ToggleGroup, RadioGroup
└── update/
    ├── UpdateInfo.kt         version.json model
    ├── UpdateInstaller.kt    fetch, download + SHA-256, PackageInstaller
    ├── UpdateManager.kt      update state for the UI
    ├── UpdateWorker.kt       6-hourly background update
    ├── InstallResultReceiver.kt  install result → confirm prompt or notification
    └── UpdateNotifications.kt
```

---

## Known limitations and open questions

- **Final hiding method is undecided.** Both methods stay until device testing shows
  which one works (see [Testing on a device](#testing-on-a-device)). If neither does,
  the fallback plan is to never hide and show a dimmed `0K` instead.
- **Total device traffic.** `TrafficStats` totals include all apps and interfaces
  (Wi-Fi, mobile, VPN, tethering), so VPN traffic may be counted twice on some devices.
- **Restart can be refused.** Some Android versions block a foreground-service restart
  from the background after a kill; the meter then comes back on the next boot or app
  open. Aggressive OEM battery savers (Xiaomi, Huawei, some Samsung modes) may stop it
  outright. Exclude Speed Meter from battery optimisation if that happens.
- **Silent updates vary.** Depends on Android version and manufacturer; some phones ask
  for confirmation every time.
- **Dismissable notification.** On Android 14+ users can swipe the notification away; it
  reappears on the next speed change.
- **Dependencies are pinned** to late-2024 releases (AGP 8.7, Compose BOM 2024.12),
  which build cleanly with targetSdk 35. Newer versions exist.
- **Debug builds only.** No release signing, minification or Play Store setup yet.

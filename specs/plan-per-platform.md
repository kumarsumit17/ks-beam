---
type: Plan
title: Per-platform optimization plan
description: Proposed plan, awaiting the owner's approval, for making each KS Beam build (macOS, Windows, Android phone, Google TV) its own app tuned to its OS for look, input, networking, OS integrations, performance, packaging and accessibility.
tags: [plan, platforms, macos, windows, android, google-tv, accessibility, performance]
---

# Per-platform optimization plan

**Status: proposed on 2026-11-25, waiting for the owner's approval.** Nothing in section 4 is built before the owner approves it and answers section 3.

Goal: each build is the best casting app on its platform, as the suite rule "Each platform build is optimized for its OS" asks (KS-Suite [platform folders](https://github.com/kumarsumit17/KS-Suite/blob/main/specs/platform-folders.md)), not one lowest-common-denominator build. The casting core and the receiver protocol stay in `shared/src/jvmSharedMain` (dependency-free JVM code, [Constraints](constraints.md)), and the sender interface stays shared. What differs per OS goes in that platform's folder, or in `shared/src/androidMain` / `shared/src/desktopMain` when two builds need it.

KS Beam differs from a media player in one way that shapes this plan: **while a cast runs, the sender is a file server.** The TV reads the file from the phone or computer for the whole film. So on every sender the main job is to keep that server reachable and fast (no sleep, no Wi-Fi power saving, no frozen process, no blocked port), and only then come look and convenience. [Plan to v1](plan-v1.md) milestones 2 and 3 stay the feature roadmap. This plan is about how each build does them on its OS, and it brings some milestone 2 items forward where an OS needs them.

## 1. Audit: what each build does today (v1.1.0, 2026-11-25)

| | macOS (`desktop/mac`) | Windows (`desktop/windows`) | Android phone (`mobile/android`) | Google TV (`tv/google-tv`) |
|---|---|---|---|---|
| Role | Sender | Sender | Sender | Receiver |
| Entry point | `Main.kt`: screen menu bar, app name, `AppleLanguages` | `Main.kt`: `Locale.getDefault()` only | `MainActivity`: system file picker, multicast lock | `ReceiverActivity`: receiver while on screen |
| Look | Shared Compose UI in a 560×680 window; default title bar; no menus beyond the app menu | Same, light system title bar | Edge-to-edge, shared UI, fixed dark scheme | 10-foot ready screen, `PlayerView` with its own controls |
| Input | Mouse only: **no keyboard shortcuts** (space, arrows: [Constraints](constraints.md) say "to add with milestone 2"), no drag and drop, `FileDialog` | Mouse only, the same gaps | Touch; no share target (milestone 2) | D-pad through `PlayerView`'s controller; language menu takes focus |
| Choosing files | `FileDialog` (native), one file | `FileDialog` (the AWT Windows dialog), one file | `OpenDocument`, one file | n/a |
| While casting | Nothing keeps the Mac awake: **idle sleep stops the file server** and the TV stalls | Same: **Windows sleep stops the server** | **No foreground service, no Wi-Fi lock**: once the app leaves the screen the cached-app freezer can freeze the server (Android 12+), and Wi-Fi power saving can stutter it. Not measured yet | Screen kept on by `PlayerView` (`keepScreenOn`); the receiver stops when the app leaves the screen (`onStop`) |
| Network permissions | **No `NSLocalNetworkUsageDescription`** in the Info.plist: macOS 15+ asks for Local Network access with no explanation, and refusing it breaks discovery. Not verified on this Mac | **No firewall rule**: the first SSDP reply and the TV's requests to the file server hit Windows Defender Firewall's prompt, and casting fails until the user allows it (untested; Windows is beta) | INTERNET, Wi-Fi and multicast permissions. **Android 17 (target SDK 37) local-network protection is not checked**: discovery may need the new local network permission | Same as phone; incoming connections only |
| Media controls | None outside the window | None | **None**: no notification or lock-screen controls while casting (milestone 2) | **No media session**: the remote's media keys reach only `PlayerView` in front; no Assistant "pause"; no audio focus attributes |
| Receiver playback | n/a | n/a | n/a | Media3 `ExoPlayer.Builder` defaults: no frame-rate matching, passthrough and HDR unchecked; photos decoded with `BitmapFactory`, EXIF rotation not applied |
| Packaging | dmg (arm64), jlink `java.prefs java.desktop java.xml jdk.unsupported`, ProGuard off | msi, the same modules, ProGuard off, per-machine install with dir chooser | APK + AAB, **R8 off**, no native libraries (8.0 MB APK) | APK + AAB, R8 off (10.4 MB APK, Media3 included); versionCode + 1,000,000,000 |
| Version | One tag for all four builds (`vX.Y.Z`) | Same | Same | Same + offset |
| Accessibility | Buttons have text; the seek slider has no label or time value; renderer rows have no role | Same, and **`jdk.accessibility` is not in the runtime**, so Narrator and NVDA cannot see the app | TalkBack reads Compose text; slider unlabelled | Ready screen readable; `PlayerView` controls are TalkBack-ready; photos have no description |

Other findings: KS Beam has no in-app updater (an open owner question in [Plan to v1](plan-v1.md)). Nothing here depends on one. The desktop interface is a phone layout in a window: one column, max 720 dp, which works but is not a desktop app.

## 2. The plan, per platform

Each item names its folder. A "new dependency" is reported as [Dependencies](dependencies.md) requires. Platform frameworks come first, and the core stays dependency-free.

### 2.1 macOS (`desktop/mac/`, desktop code in `shared/src/desktopMain`)

- **Keep the cast alive**
  - While a cast runs, an `IOPMAssertionCreateWithName` assertion (`PreventUserIdleSystemSleep`) keeps the Mac awake. The display may still sleep. The assertion is released on stop and when the app quits. Through JNA, a new dependency, or through `caffeinate -i -w <pid>` started as a child process, which needs no dependency (decision 4).
  - The Info.plist gets `NSLocalNetworkUsageDescription` ("KS Beam finds TVs and projectors on your network and sends them your file."), through Compose's `macOS { infoPlist { extraKeysRawXml } }`. To verify: on macOS 15+, that the prompt shows it and that discovery and the file server work after "Allow".
- **Look and input**
  - A Mac window: a unified transparent title bar (`apple.awt.transparentTitleBar`, `apple.awt.fullWindowContent`), and a two-pane layout when the window is wide: renderers on the left, the file and controls on the right.
  - Menu bar to macOS conventions: File ▸ Open… (⌘O), Open Recent, Playback ▸ Play/Pause (Space), Back 10 s (←), Forward 10 s (→), Stop (⌘.), Window, Help. The same shortcuts match the TV remote's D-pad actions ([Constraints](constraints.md)).
  - Drag a file onto the window or the Dock icon to cast it to the selected renderer, and Finder "Open With" (`document-types` for video, audio and image types, `Desktop.setOpenFileHandler`).
- **Performance**: measure cold start, time to the first renderer found and file-server throughput on this Mac (section 4). Try ProGuard on the release build and keep it only if the casting tests pass. Report dmg size per release.
- **OS integrations**: media keys and Now Playing while casting (`MPRemoteCommandCenter`, `MPNowPlayingInfoCenter`) only if decision 4 brings JNA in. The keyboard media keys then control the TV even with the app in the background. Otherwise it waits.
- **Accessibility**: VoiceOver labels on the slider (read as "12:30 of 1:42:00") and on renderer rows (button role, selected state), Tab focus through every control, Reduce Motion honoured.

### 2.2 Windows (`desktop/windows/`, desktop code in `shared/src/desktopMain`)

- **Keep the cast alive**
  - `SetThreadExecutionState(ES_SYSTEM_REQUIRED | ES_CONTINUOUS)` while a cast runs, through JNA (decision 4). Without JNA there is no clean way (`powercfg` overrides need admin), so this item needs JNA.
  - **Firewall**: the installer adds an inbound rule for KS Beam on private networks only (WiX custom action or `netsh advfirewall` at install, decided when built). The app also explains the firewall prompt the first time discovery finds nothing. The owner checks it by hand on Windows (never blocks a release).
  - Network choice: with several adapters (Wi-Fi, Ethernet, a VPN, Hyper-V's virtual switch), search and serve on the one that has a default route on a private network, and offer a choice in Settings when there are more.
- **Look and input**
  - The two-pane layout as on macOS, a dark title bar matching the app (`DwmSetWindowAttribute` `DWMWA_USE_IMMERSIVE_DARK_MODE`, JNA), and the Windows file dialog through `FileDialog`, kept.
  - Keys: Ctrl+O, Space, ←/→, Ctrl+. or Esc to stop, Alt+F4; drag and drop onto the window; "Open with KS Beam" through file associations in the msi (`windows { fileAssociation(…) }`), with one running instance that receives the file.
- **Packaging**: per-user install by default (`perUserInstall`, no admin prompt) unless the firewall rule needs admin. That choice comes with the firewall step. Try ProGuard as on macOS.
- **Accessibility**: add `jdk.accessibility` to the runtime modules (Narrator, NVDA), the same labels and Tab focus as macOS, and Windows High Contrast detected and followed.

### 2.3 Android phone (`mobile/android/`, shared Android code in `shared/src/androidMain`)

- **Keep the cast alive** (milestone 2's notification, brought forward because Android needs it to cast at all with the screen off)
  - A foreground service of type `connectedDevice` (the phone is driving a TV) runs the file server while a cast is active. It shows a notification with the renderer, the file, play/pause, ±10 s and stop. It stops with the cast. It needs `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_CONNECTED_DEVICE`, and a Play Console declaration when the Play build comes.
  - A `WifiManager.WifiLock` (`WIFI_MODE_FULL_LOW_LATENCY` on Android 10+) while casting, so Wi-Fi power saving does not stall the stream.
  - Local network permission: check Android 17's local-network protection on the target SDK 37 emulator, and ask for the permission with an explanation before the first search if discovery needs it.
- **Media controls**: the notification's controls through the platform's own `android.media.session.MediaSession` (no new dependency). They show on the lock screen and in quick settings and answer Bluetooth headset buttons. The session forwards commands to the TV over AVTransport.
- **Look and input (touch)**: Material You dynamic colour on Android 12+ outside the cast panel, predictive back, larger touch targets for ±10 s, and the volume keys change the TV's volume while the cast panel is open (`RenderingControl`, milestone 2).
- **OS integrations**: KS Beam as a share target (`ACTION_SEND` for `video/*`, `audio/*`, `image/*`, milestone 2), and `OpenMultipleDocuments` for a queue when the queue exists.
- **Performance and packaging**: R8 and resource shrinking on the release build; a baseline profile for start-up (Compose brings `profileinstaller`); target cold start under 800 ms on a mid-range phone; report APK size per release.
- **Accessibility**: TalkBack labels and custom actions on the slider ("forward 10 seconds"), font scale up to 200% without clipping, touch targets at least 48 dp.

### 2.4 Google TV (`tv/google-tv/`)

- **Keep receiving** (milestone 2): the receiver moves into a foreground service (`mediaPlayback` type) so it keeps answering while another app is on screen, and comes forward when a cast starts. It needs `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Whether it may run all the time or only after the app was opened is decision 2.
- **Playback quality**
  - **Frame-rate matching**: `setVideoChangeFrameRateStrategy`, default "seamless only", as Google TV recommends.
  - **Audio**: `AudioAttributes` (`USAGE_MEDIA`, `CONTENT_TYPE_MOVIE`) with audio focus, and AC-3, E-AC-3 and DTS passthrough checked over HDMI (`AudioCapabilities`).
  - **HDR**: keep `PlayerView` on a `SurfaceView` so HDR10, HLG and Dolby Vision reach the display; check it on the owner's TV.
  - **Photos**: EXIF rotation applied, and the decode sized to the display (4K TVs get 4K, not a fixed size).
- **Input (D-pad, 10-foot)**: the remote's play/pause, ←/→ (10 s, hold for 30 s steps) and Back stop with clear rules; controls hide after 3 s; every screen checked at 32–100 inch, as the suite's testing rule asks.
- **OS integrations**: a media session (`androidx.media3:media3-session`, new dependency, decision 4) so the remote's media keys, the Google Assistant ("pause", "skip forward 30 seconds") and the TV's now-playing card work.
- **Performance and packaging**: R8 on the release build; cold start under 1.5 s on a Chromecast with Google TV class device; the receiver ready (SSDP alive sent) within 1 s of the app opening.
- **Accessibility**: TalkBack reads the ready screen and the device name; captions follow the TV's caption settings (`CaptioningManager`) once subtitles exist; photos get the file name as their description.

### 2.5 Shared (`shared/`)

- **One cast session model**: `BeamState`'s cast (renderer, file, position, playing) feeds every platform's controls (the macOS menu, the Android notification and its media session, the desktop keys), so they all show the same state and send the same commands.
- **Keyboard map**: one map in `jvmSharedMain` (Space play/pause, ←/→ 10 s, Esc stop), used by both desktops and matching the TV remote.
- **Keep-alive hook**: `BeamPlatform` gains `castLock(): AutoCloseable` (like today's `searchLock`), held for the whole cast. macOS and Windows keep the computer awake with it, and Android starts the foreground service and the Wi-Fi lock.
- **Wide layout**: the two-pane layout is shared (`BoxWithConstraints`), used by both desktops and by phones in landscape or tablets.
- New interface text goes into `shared/src/commonMain/lang/en.json` and `hi.json` with the Hindi style guide; the language-file check runs in the tests.

## 3. Decisions for the owner

1. **Own version per platform.** The owner chose one tag per platform for KS JaiPlayer (`mac-vX.Y.Z`, `windows-vX.Y.Z`, `android-vX.Y.Z`, `google-tv-vX.Y.Z`), and every app follows it in its own plan. For KS Beam: (a) the same, once ks-utility's release workflow takes a `platform` input (KS Beam has no updater, so only the workflow part applies), or (b) one version for all four for now.
2. **Receiver in the background on Google TV**: (a) a foreground service started when the app is opened, kept until the TV restarts; (b) also started at boot (`RECEIVE_BOOT_COMPLETED`), so the TV is always ready to receive; or (c) as today, only while KS Beam is on screen.
3. **Windows firewall rule at install**: (a) the msi adds the private-network rule (needs a per-machine install and the admin prompt), or (b) a per-user install, and the app explains Windows' own prompt on first use.
4. **New dependencies**: JNA on the desktops (for the keep-awake calls, the macOS media keys and Now Playing, and the Windows dark title bar), or no JNA: keep-awake through `caffeinate` on macOS and the Windows items without native calls, with no media keys on desktop. And `androidx.media3:media3-session` for the TV receiver's media session; the phone uses the platform `MediaSession` and needs nothing new.

## 4. Build order and testing

Each step is one or more commits on `dev`, tested on its platform before the next. Nothing is pushed or tagged without the owner's OK.

| Step | What | Where | Tested on |
|---|---|---|---|
| 1 | Versioning per platform (if 3.1 a) | this repo's workflow, after ks-utility's | a rehearsal run per platform |
| 2 | Keep the cast alive on every sender: `castLock`, Android foreground service, Wi-Fi lock, notification and media session; macOS and Windows keep-awake; macOS Local Network text; Android 17 local-network check | `shared/`, `mobile/android`, `desktop/*` | Phone emulator (cast continues with the screen off, measured against the TV emulator through the Mac); this Mac with the display asleep; Windows by the owner |
| 3 | Google TV receiver: foreground service (decision 2), frame-rate matching, audio focus and passthrough, HDR check, photos, media session | `tv/google-tv` | Every TV emulator 32–100 inch (`scripts/emulators.sh`), cast from the Mac; the owner's TV for passthrough, HDR and frame rate |
| 4 | Desktop look and input: two-pane layout, keyboard map, menus, drag and drop, Open With, file associations, Windows network choice and firewall | `shared/`, `desktop/mac`, `desktop/windows` | This Mac (`:desktop:mac:run`, the dmg); Windows CI build and the owner's hand check |
| 5 | Phone look and OS integrations: dynamic colour, predictive back, share target, volume keys (with milestone 2's volume) | `mobile/android`, `shared/src/androidMain` | Phone emulator |
| 6 | Packaging and performance: R8 and baseline profiles, ProGuard check, `jdk.accessibility`, sizes and start-up measured per platform | each build | Each platform as above |
| 7 | Accessibility pass on all four, interface text in English and Hindi | `shared/`, each platform | TalkBack (phone, TV), VoiceOver (Mac), Narrator by the owner |

Measurements for every step, on the platform itself: cold start, time to the first renderer found, time from "cast" to the first frame on the TV, and file-server throughput while seeking. Windows cannot be run from this Mac, so the Windows items are checked by its CI build and the owner's hand check, which never blocks a release.

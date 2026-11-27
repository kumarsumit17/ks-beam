# 2026-11-27

* **Release**: `v1.5.0` (all platforms): plan step 7, screen readers, large text, Windows High Contrast, and the config-change fix.
* **Release**: `v1.4.0` (all platforms): plan step 6, R8 and ProGuard (phone APK 1.0 MB, TV APK 1.8 MB, dmg 58 MB, msi 48 MB), Windows screen readers.
* **Update**: [Per-platform optimization plan](plan-per-platform.md): step 7 built (screen-reader labels, states and actions; large-text layout; Windows High Contrast). Fix: a change of text size, language or display size during a cast no longer stops it.
* **Update**: links to the KS-Suite repository now name `kumasumit/ks-suite` (renamed, lower case).

# 2026-11-26

* **Update**: `ks-utility` moved from `v0.13.2` to `v0.14.0` before the release (only the Tauri apps' release workflows were added); the release workflow follows.
* **Update**: [Per-platform optimization plan](plan-per-platform.md): step 6 built (R8 on both Android apps, ProGuard on the desktops, `jdk.accessibility` on Windows, a start-up check of the installed Windows app in CI, start-up measured); step 8 added for the macOS Now Playing and media keys.
* **Update**: `ks-utility` moved from `v0.10.1` to `v0.13.2` (the suite rule: the newest tag before each release). Only Hindi terms changed in between; the stricter language check passes with no change to KS Beam's files. The release workflow follows to `@v0.13.2`.
* **Creation**: [Releases](releases.md) added: the suite rule "Build, test, release, no waiting" as KS Beam applies it, platform tags, and the owner's hand checks after a release.
* **Release**: `v1.3.0` (all platforms): plan step 5 and the apostrophe and recast fixes. APKs and AABs signed with the suite key and attested; dmg and msi.
* **Update**: the repository moved to [github.com/kumasumit/ks-beam](https://github.com/kumasumit/ks-beam) (with KS-Suite and ks-utility); links, the macOS Help menu and the receiver's `manufacturerURL` follow.
* **Update**: [Casting](casting.md) and [Per-platform optimization plan](plan-per-platform.md): step 5 built. Share to KS Beam on Android; the TV's volume (RenderingControl) from the slider, the desktops' up/down keys and the phone's volume buttons; Material You colours; predictive back. Fixes: a replaced cast's late `Stop` could stop the new file; SOAP requests retry once after a dropped connection; apostrophes showed a backslash (language files now converted with `lang.mjs compose`).

# 2026-11-25

* **Update**: [Per-platform optimization plan](plan-per-platform.md): step 1 back and built under the suite's platform-tag rule: ks-utility `v0.10.1`, `release.yml` for `android-v`, `google-tv-v`, `mac-v`, `windows-v` and plain tags, a platform input for rehearsals; no bridge release (no updater).
* **Release**: `v1.2.0`, with per-platform plan steps 2–4 (casts stay alive, the Google TV receiver in the background, desktop menus, keys and drag and drop). Phone and TV APKs and AABs signed with the suite key and attested; dmg and msi. Windows users of 1.1.0 or earlier uninstall first (now a per-user install).
* **Update**: [Per-platform optimization plan](plan-per-platform.md): step 4 built. Shared desktop keys (Space, arrows, Esc), drop a file to cast, a file waits for a TV to be chosen, two panes while casting, quitting stops the TV first. macOS: menu bar, title bar over the content, Finder "Open With" as an alternative app. Windows: single instance, dark title bar, Ctrl+O, firewall explanation (the owner's choice: no rule at install), per-user install (a per-machine install from v1.1.0 or earlier must be uninstalled first). No Windows file associations (they would take over the default app).
* **Update**: [Per-platform optimization plan](plan-per-platform.md): decision 1 settled by the suite rule (one plain `vX.Y.Z` tag for every build, no platform tags or bridge releases for now); step 1 dropped.
* **Update**: [Receiver](receiver.md) and [Per-platform optimization plan](plan-per-platform.md): step 3 built. The receiver runs in `ReceiverService`, a foreground service started when KS Beam opens (the owner's choice), with a Media3 session, audio focus, seamless-only frame-rate matching, ±10 s remote seeking (30 s held), photos upright and sized to the display. A cast brings KS Beam forward when "Display over other apps" is allowed; otherwise a background video waits paused. New dependency (owner's decision): `androidx.media3:media3-session` 1.11.1 (Apache-2.0), TV app only, same version as the other Media3 libraries; it adds `androidx.media:media` and `androidx.lifecycle:lifecycle-service` (both Apache-2.0) and nothing else.
* **Update**: [Casting](casting.md) and [Per-platform optimization plan](plan-per-platform.md): step 2 built. A cast now holds a cast lock on every sender: an Android `connectedDevice` foreground service with a media notification, a platform `MediaSession`, a wake lock and Wi-Fi locks; an IOKit power assertion on macOS; `SetThreadExecutionState` on Windows. macOS gets `NSLocalNetworkUsageDescription`; both Android apps declare `ACCESS_LOCAL_NETWORK` and ask for it on Android 17. New dependency (owner's decision): JNA 5.19.1 (Apache-2.0), desktop builds only, nothing else in its tree; the same version as ks-jaiplayer.
* **Update**: [Per-platform optimization plan](plan-per-platform.md) approved by the owner; its decisions are asked when the step that needs them starts.
* **Creation**: [Per-platform optimization plan](plan-per-platform.md) added (proposed, awaiting the owner): an audit of each build and what changes per OS, following the suite rule that each platform build is optimized for its OS. Key findings: no sender keeps the cast alive (sleep, the Android freezer, Wi-Fi power saving), no macOS Local Network text, no Windows firewall rule, no media controls outside the app.
* **Update**: `ks-utility` moved from `v0.6.0` to `v0.9.0` (more languages, the translation tools; [ks-utility translation tools](https://github.com/kumasumit/ks-utility/blob/main/specs/translating.md)).

# 2026-11-24

* **Update**: [Overview](overview.md) and [Receiver](receiver.md): the one Android APK is split into a phone app (`mobile/android`, the sender) and a Google TV app (`tv/google-tv`, the receiver, `-google-tv.apk`), with the same package name, version and keystore; the TV `versionCode` adds 1,000,000,000. ks-utility moved to `v0.6.0`. No one-APK bridge release came before the split: KS Beam has no in-app updater, so no installed copy picks a file by itself.

# 2026-11-23

* **Release**: `v1.0.0` was tagged but never released: its run stopped at the keystore check because the signing secrets were still being set; nothing was published. The first release is `v1.0.1`, the same code.
* **Creation**: [Receiver](receiver.md) added: the same APK is a DLNA renderer on Google TV (SSDP, description, AVTransport, ConnectionManager, RenderingControl, Media3 playback). [Overview](overview.md), [Plan to v1](plan-v1.md) (receiver and releases moved into milestone 1) and [Constraints](constraints.md) (cleartext HTTP on the local network) updated.
* **Creation**: [Overview](overview.md), [Casting](casting.md), [Plan to v1](plan-v1.md), [Constraints](constraints.md) and [Dependencies](dependencies.md) added: KS Beam casts files from Android, Windows and macOS to DLNA TVs and projectors; a Google TV receiver is planned.

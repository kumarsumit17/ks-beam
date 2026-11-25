# 2026-11-25

* **Update**: [Per-platform optimization plan](plan-per-platform.md) approved by the owner; its decisions are asked when the step that needs them starts.
* **Creation**: [Per-platform optimization plan](plan-per-platform.md) added (proposed, awaiting the owner): an audit of each build and what changes per OS, following the suite rule that each platform build is optimized for its OS. Key findings: no sender keeps the cast alive (sleep, the Android freezer, Wi-Fi power saving), no macOS Local Network text, no Windows firewall rule, no media controls outside the app.
* **Update**: `ks-utility` moved from `v0.6.0` to `v0.9.0` (more languages, the translation tools; [ks-utility translation tools](https://github.com/kumarsumit17/ks-utility/blob/main/specs/translating.md)).

# 2026-11-24

* **Update**: [Overview](overview.md) and [Receiver](receiver.md): the one Android APK is split into a phone app (`mobile/android`, the sender) and a Google TV app (`tv/google-tv`, the receiver, `-google-tv.apk`), with the same package name, version and keystore; the TV `versionCode` adds 1,000,000,000. ks-utility moved to `v0.6.0`. No one-APK bridge release came before the split: KS Beam has no in-app updater, so no installed copy picks a file by itself.

# 2026-11-23

* **Release**: `v1.0.0` was tagged but never released: its run stopped at the keystore check because the signing secrets were still being set; nothing was published. The first release is `v1.0.1`, the same code.
* **Creation**: [Receiver](receiver.md) added: the same APK is a DLNA renderer on Google TV (SSDP, description, AVTransport, ConnectionManager, RenderingControl, Media3 playback). [Overview](overview.md), [Plan to v1](plan-v1.md) (receiver and releases moved into milestone 1) and [Constraints](constraints.md) (cleartext HTTP on the local network) updated.
* **Creation**: [Overview](overview.md), [Casting](casting.md), [Plan to v1](plan-v1.md), [Constraints](constraints.md) and [Dependencies](dependencies.md) added: KS Beam casts files from Android, Windows and macOS to DLNA TVs and projectors; a Google TV receiver is planned.

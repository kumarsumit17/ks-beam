---
type: Specification
title: Overview
description: What KS Beam is for, the platforms it runs on, how it reaches TVs and projectors, and its tech stack.
tags: [overview, platforms, stack]
---

# KS Beam

KS Beam sends videos, music and photos from a phone or computer to a TV or projector on the same network. It is free, with no ads, analytics or account.

## Platforms

| Platform | Folder | Role |
|---|---|---|
| Android phone | `mobile/android` | Sender |
| Windows | `desktop/windows` | Sender |
| macOS | `desktop/mac` | Sender |
| Google TV, Android TV and projectors | `tv/google-tv` | Receiver ([Receiver](receiver.md)): a separate Google TV app, same package name |
| iOS | `mobile/ios` | Not a target |

Code used by more than one platform is in `shared/` (Kotlin Multiplatform): the casting core and the whole interface.

On Android there are two apps from v1.1.0, with the same package name (`com.ks.beam`), version and signing key: the phone app (`mobile/android`, `ks-beam-v<version>.apk`) is the sender and the Google TV app (`tv/google-tv`, `ks-beam-v<version>-google-tv.apk`) is the receiver. Each has its own interface and launcher entry, so TV changes cannot break the phone app. The role comes from the app installed, never from the device. The TV app's `versionCode` adds 1,000,000,000 (the suite's versioning spec), so the phone app is never installed over it. Up to v1.0.1 one APK did both, switching at runtime. There was no one-APK bridge release first: the suite rule asks for one only so in-app updaters pick the right file, and KS Beam has no updater.

## How it reaches a TV or projector

Through **DLNA / UPnP AV** ([Casting](casting.md)): the TV or projector is a *media renderer*. KS Beam finds renderers on the local network, serves the chosen file from the phone or computer over HTTP, and tells the renderer to play that address. Most smart TVs (Samsung, LG, Sony, Philips, Hisense, TCL, Panasonic) and many Android-based projectors have a renderer built in, so nothing is installed on the TV.

Not used: Google Cast and AirPlay (their senders need proprietary SDKs or licences), and Miracast (screen mirroring through Wi-Fi Direct, which apps cannot drive on modern Android, Windows or macOS). TVs and projectors without a DLNA renderer but with Google TV or Android TV get one from KS Beam itself: the [receiver](receiver.md).

## Stack

- Kotlin Multiplatform with Compose Multiplatform for the interface on all three senders.
- The casting core (discovery, control, the file server, the receiver protocol) is plain JVM code with no third-party dependencies: `java.net` sockets, `HttpURLConnection` and the platform's XML parser.
- The receiver plays with Media3 (`media3-exoplayer`, `media3-ui`, Apache-2.0), the one third-party library beyond Compose, AndroidX and coroutines; the same one ks-jaiplayer uses.
- Package root `com.ks.beam`, application ID `com.ks.beam`.
- English and Hindi through `ks-utility` (language files in `shared/src/commonMain/lang/`).

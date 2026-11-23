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
| Google TV | `tv/google-tv` | Receiver (planned, [plan](plan-v1.md) milestone 3) |
| iOS | `mobile/ios` | Not a target |

Code used by more than one platform is in `shared/` (Kotlin Multiplatform): the casting core and the whole interface.

## How it reaches a TV or projector

Through **DLNA / UPnP AV** ([Casting](casting.md)): the TV or projector is a *media renderer*. KS Beam finds renderers on the local network, serves the chosen file from the phone or computer over HTTP, and tells the renderer to play that address. Most smart TVs (Samsung, LG, Sony, Philips, Hisense, TCL, Panasonic) and many Android-based projectors have a renderer built in, so nothing is installed on the TV.

Not used: Google Cast and AirPlay (their senders need proprietary SDKs or licences), and Miracast (screen mirroring through Wi-Fi Direct, which apps cannot drive on modern Android, Windows or macOS). TVs and projectors without a DLNA renderer will be covered by the KS Beam receiver for Google TV.

## Stack

- Kotlin Multiplatform with Compose Multiplatform for the interface on all three senders.
- The casting core (discovery, control, the file server) is plain JVM code with no third-party dependencies: `java.net` sockets, `HttpURLConnection` and the platform's XML parser.
- Package root `com.ks.beam`, application ID `com.ks.beam`.
- English and Hindi through `ks-utility` (language files in `shared/src/commonMain/lang/`).

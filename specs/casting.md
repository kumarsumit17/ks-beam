---
type: Specification
title: Casting
description: How KS Beam finds DLNA media renderers, serves a file to them and controls playback, with the exact messages it sends.
tags: [casting, dlna, upnp, network]
---

# Casting

Code: `shared/src/jvmSharedMain/kotlin/com/ks/beam/core/`. Standards: UPnP Device Architecture 1.1, UPnP AV `AVTransport:1`, DLNA guidelines for the `contentFeatures` and `protocolInfo` values.

## 1. Find renderers (`SsdpDiscovery`)

- Send an SSDP `M-SEARCH` to `239.255.255.250:1900` with `ST: urn:schemas-upnp-org:service:AVTransport:1` and `MX: 2`, from every IPv4 network interface that is up, not loopback and supports multicast, twice (UDP may drop one).
- Collect the unicast replies for 3 seconds. Each reply's `LOCATION` header names the device description; replies with the same `USN` device part are one device.
- Fetch each description (`DeviceDescription.parse`): `friendlyName`, `manufacturer`, `modelName`, `UDN`, and the `controlURL` of the `AVTransport` service, resolved against `URLBase` or the description's address. Devices without an `AVTransport` service are skipped. The parser refuses DOCTYPE declarations and does not expand entities.
- Android needs a Wi-Fi multicast lock while searching (`CHANGE_WIFI_MULTICAST_STATE`), released when the search ends.

## 2. Serve the file (`MediaServer`)

- A small HTTP/1.1 server on a random port, bound to the address of the interface that reaches the renderer (found by connecting a UDP socket to the renderer's address; nothing is sent).
- Each file gets an address `/m/<token>/<name>`, where `<token>` is 128 random bits. Only registered tokens are answered; anything else gets `404`. Only `GET` and `HEAD` are accepted.
- `Range: bytes=a-b`, `a-` and `-n` are supported (`206`, `Content-Range`); an unsatisfiable range gets `416`. Every response has `Accept-Ranges: bytes`, `Content-Length`, the file's MIME type, `transferMode.dlna.org: Streaming` and `contentFeatures.dlna.org` with `DLNA.ORG_OP=01` (byte seeking).
- The server is stopped when casting stops or the app closes; files are read in place, never copied.
- **While a cast runs the server must stay reachable**, so each sender takes a cast lock (`BeamPlatform.castLock`) from the start of the cast to its end ([Per-platform plan](plan-per-platform.md), step 2):
  - Android phone: `CastService`, a `connectedDevice` foreground service (the process is never frozen), a partial wake lock (capped at 6 hours) and Wi-Fi locks. Its media notification and a platform `MediaSession`, marked as remote playback, give the lock screen and quick settings play/pause, ±10 s, the seek bar and stop. Headset and keyboard media keys do not reach it: Android sends them only to an app playing audio on the phone.
  - macOS: an IOKit power assertion (`PreventUserIdleSystemSleep`); the display may sleep.
  - Windows: `SetThreadExecutionState(ES_SYSTEM_REQUIRED)` on a thread held for the cast.
  - The cast ends when the interface goes (window closed, app swiped away), which releases the lock.

## 3. Play (`AvTransport`)

SOAP 1.1 POSTs to the control URL, `SOAPACTION: "<serviceType>#<Action>"`, `InstanceID` 0:

| Step | Action | Arguments |
|---|---|---|
| Load | `SetAVTransportURI` | `CurrentURI` = the file's address, `CurrentURIMetaData` = DIDL-Lite (below) |
| Start | `Play` | `Speed` = `1` |
| Pause / resume | `Pause` / `Play` | |
| Stop | `Stop` | |
| Seek | `Seek` | `Unit` = `REL_TIME`, `Target` = `H:MM:SS` |
| Progress (every second while casting) | `GetPositionInfo`, `GetTransportInfo` | reads `RelTime`, `TrackDuration`, `CurrentTransportState` |

- DIDL-Lite: one `item` with `dc:title`, `upnp:class` (`object.item.videoItem`, `object.item.audioItem.musicTrack` or `object.item.imageItem.photo`) and a `res` with `protocolInfo="http-get:*:<mime>:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"` and `size`. Some TVs refuse a file without this metadata.
- A SOAP fault is shown with its UPnP error code (for example 714, "illegal MIME type": the TV cannot play that format).
- Requests time out after 5 seconds.

## 4. Formats

The renderer decodes the file, so what plays depends on the TV. KS Beam sends the file as it is (no transcoding) with a MIME type from its extension: mp4, m4v, mkv, webm, mov, avi, ts for video; mp3, m4a, aac, flac, wav, ogg for audio; jpg, png, gif, webp for photos. MP4 (H.264, AAC) and MP3 play on nearly every TV.

---
type: Specification
title: Receiver
description: The KS Beam receiver on Google TV, a DLNA media renderer that KS Beam and other DLNA apps can cast to, with the messages it answers and how it plays.
tags: [receiver, google-tv, dlna, upnp]
---

# Receiver

The Google TV app (`tv/google-tv`, `ks-beam-v<version>-google-tv.apk`, a separate APK from the phone app with the same package name) is the **receiver** on Google TV and Android TV ([Overview](overview.md)). The receiver makes a TV or Android projector a DLNA *MediaRenderer*, so KS Beam on a phone or computer, or any app that casts with DLNA, can play on it.

Code: the protocol in `shared/src/jvmSharedMain/kotlin/com/ks/beam/core/receiver/` (tested on the desktop JVM, including casting from KS Beam's own sender); playback, the screen and the activity (`ReceiverActivity`) in `tv/google-tv/`.

## 1. Being found (`SsdpAdvertiser`)

- Joins `239.255.255.250:1900` on every LAN interface (with a Wi-Fi multicast lock on Android).
- Answers `M-SEARCH` for `ssdp:all`, `upnp:rootdevice`, its UDN, `urn:schemas-upnp-org:device:MediaRenderer:1` and its three service types, with a unicast reply after a short random delay (bounded by `MX`).
- Sends `ssdp:alive` NOTIFYs on start and every 60 seconds, and `ssdp:byebye` when it stops.
- The UDN is a random UUID saved on first start, so senders keep seeing the same device. The name is the TV's device name from Settings, else its model.

## 2. Being controlled (`RendererServer`)

HTTP on a random port, listening on every interface; the address announced is the TV's LAN address.

| Path | What |
|---|---|
| `GET /desc.xml` | Device description (`MediaRenderer:1`, manufacturer `KS-Suite`, model `KS Beam`, `DMR-1.50`) |
| `GET /avt.xml`, `/cm.xml`, `/rc.xml` | Service descriptions for the actions below |
| `POST /avt/control` | AVTransport: `SetAVTransportURI`, `Play`, `Pause`, `Stop`, `Seek` (`REL_TIME`, `ABS_TIME`), `GetPositionInfo`, `GetTransportInfo`, `GetMediaInfo`, `GetTransportSettings`, `GetDeviceCapabilities`, `GetCurrentTransportActions` |
| `POST /cm/control` | ConnectionManager: `GetProtocolInfo` (the formats in section 3), `GetCurrentConnectionIDs`, `GetCurrentConnectionInfo` |
| `POST /rc/control` | RenderingControl: `GetVolume`, `SetVolume`, `GetMute`, `SetMute` (the player's volume, not the TV's) |

- `SetAVTransportURI` accepts only `http://` and `https://` addresses (never `file:` or `content:`); the title and MIME type come from the DIDL-Lite metadata when present.
- Unknown actions get UPnP error 401, bad arguments 402. Event subscriptions get `501`: senders poll, as KS Beam's own sender does.
- Request bodies are limited to 256 KB; the XML parser refuses DOCTYPEs.

## 3. Playing (`tv/google-tv`)

- Video and audio through Media3 ExoPlayer (`media3-exoplayer`, `media3-ui`, Apache-2.0), shown full screen with Media3's own controls. Formats: MP4, MKV, WebM, MOV, MPEG-TS, AVI; MP3, AAC/M4A, FLAC, WAV, Ogg, as far as the TV's decoders allow.
  - Audio focus (`USAGE_MEDIA`, movie content): the Assistant or another app pauses it.
  - Frame-rate matching only where the switch is seamless (`VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS`), as Google TV recommends.
  - HDR reaches the display: `PlayerView` keeps its default `SurfaceView`. AC-3, E-AC-3 and DTS passthrough are ExoPlayer's own (`AudioCapabilities`); both are checked on a real TV, not on the emulator.
- **Remote:** with the controls hidden, left and right seek 10 s a press and 30 s steps while held; OK shows the controls, which hide after 3 s; with them shown the D-pad moves between them (the seek bar steps 10 s).
- **Media session** (`media3-session`): the remote's play/pause keys, Google Assistant and the TV's now-playing card reach the player.
- Photos (JPEG, PNG, GIF, WebP) are downloaded (at most 40 MB), decoded no larger than the display's real resolution (a 4K TV gets 4K), turned upright from their EXIF orientation, and shown fitted to the screen with the file name as their description.
- A file the TV can't play shows "This TV can't play …" instead of a blank screen.
- **Back** stops what is showing and returns to the ready screen. The ready screen shows the name to pick and the language button (D-pad focusable).
- **Receiving in the background** (the owner's choice): opening KS Beam starts `ReceiverService`, a `connectedDevice` foreground service that owns the player, the receiver and the session, and keeps receiving after KS Beam closes, until the TV restarts or the system ends it. It does not start at boot.
- A cast that arrives while another app is on screen brings KS Beam forward, which Android allows only with **"Display over other apps"** (`SYSTEM_ALERT_WINDOW`). Without it the ready screen shows a line and an "Allow" button that opens that setting, and a video cast in the background waits paused (the sender shows paused) until KS Beam is opened; a song plays. When KS Beam leaves the screen during a video, the video pauses.

## Limits

- Discovery needs multicast on the network; some guest Wi-Fi networks block it.
- The Android emulator is behind its own NAT and can't be discovered from the Mac; it is tested by forwarding its port (`adb forward`) and casting with `EmulatorCastTest`.

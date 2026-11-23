---
type: Plan
title: Plan to v1
description: The milestones from the first DLNA sender on Android, Windows and macOS to a KS Beam receiver on Google TV, and what each milestone must pass.
tags: [plan, milestones]
---

# Plan to v1

## Milestone 1: cast a file (this milestone)

- Shared casting core: discovery, the file server, playback control ([Casting](casting.md)), with unit tests that run against a fake renderer.
- Interface on Android, Windows and macOS: the renderers found, choose a file, cast, play/pause, seek ±10 s and by the bar, stop. Language setting (Same as the system, English, हिंदी).
- Done when: `./gradlew :shared:allTests check` passes; the Android app installs and runs on the phone emulator; the macOS app runs; a file plays on a real DLNA TV or projector (owner's hand check).

## Milestone 2: everyday use

- Photo slideshows and a queue of files, remembered last renderer, volume (`RenderingControl`), a notification with controls on Android while casting, casting from other apps through Android's share menu.
- CI (`windows-2025` and Android) and the first release through ks-utility's shared release workflow.

## Milestone 3: KS Beam receiver for Google TV

- `tv/google-tv`: a DLNA renderer app, so TVs and projectors without one (Google TV, Android projectors) can receive. Playback through Media3.
- Screen mirroring from Android (`MediaProjection`) and the desktops to the receiver, if the owner wants it: a KS Beam protocol of its own, since DLNA has none.

## Open questions for the owner

- Whether the KS Beam receiver should instead be part of `ks-jaiplayer` (a "receive casts" mode in the player). That would be a cross-repo contract.
- App updates (ks-utility `app-update`): KS Beam already uses the local network; an internet check would be its only internet use.

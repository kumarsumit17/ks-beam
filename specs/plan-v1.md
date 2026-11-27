---
type: Plan
title: Plan to v1
description: The milestones from the first DLNA sender and Google TV receiver to background receiving and screen mirroring, and what each milestone must pass.
tags: [plan, milestones]
---

# Plan to v1

## Milestone 1: cast a file, and receive on Google TV (released as v1.0.1; v1.0.0 was never released)

- Shared casting core: discovery, the file server, playback control ([Casting](casting.md)), with unit tests that run against a fake renderer.
- Interface on Android, Windows and macOS: the renderers found, choose a file, cast, play/pause, seek ±10 s and by the bar, stop. Language setting (Same as the system, English, हिंदी).
- The receiver on Google TV, brought forward from milestone 3 so all four platforms have something to install ([Receiver](receiver.md)).
- CI (Android, Windows) and releases through ks-utility's shared release workflow.
- Done when: `./gradlew :shared:desktopTest check` passes; the Android app installs and runs on the phone emulator; the macOS app runs; a file plays on a real DLNA TV or projector (owner's hand check).

## Milestone 2: everyday use

- Photo slideshows and a queue of files, remembered last renderer, volume (`RenderingControl`), a notification with controls on Android while casting, casting from other apps through Android's share menu.
- The receiver keeps running in the background (a foreground service) and can be started from the TV's cast menu.

## Milestone 3: screen mirroring

- Screen mirroring from Android (`MediaProjection`) and the desktops to the receiver, if the owner wants it: a KS Beam protocol of its own, since DLNA has none.

## Open questions for the owner

- Whether ks-jaiplayer should also offer a "receive casts" mode, or hand KS Beam's received videos to its player. Either would be a cross-repo contract.
- ~~App updates~~: decided 2026-11-27. The owner chose to add ks-utility's updater on every platform (phone, Google TV, Mac, Windows), not the `no-updater` exception. Built in v1.7.0 ([Constraints](constraints.md)).

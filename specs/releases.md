---
type: Policy
title: Releases
description: How KS Beam is released: build, test, release without waiting, one version per platform or one for all, and the owner's hand checks afterwards.
tags: [release, versioning, policy]
---

# Releases

The suite rule "Build, test, release, no waiting" (KS-Suite [release cycle](https://github.com/kumasumit/ks-suite/blob/main/specs/release-cycle.md)), as KS Beam applies it.

## Build, test, release

- Every feature or fix is built, tested on the emulators (`scripts/emulators.sh` in the KS-Suite workspace) or in CI, pushed to `dev` and released with the next tag. Nobody is asked before a tag.
- Never commit or release while a test or CI fails.
- A feature raises MINOR, a fix raises PATCH.
- At a release `main` fast-forwards to `dev`, then the tag is pushed; the release workflow (`.github/workflows/release.yml`, ks-utility's shared workflow) builds, signs, attests and publishes. The published files are checked afterwards: names, version codes, the suite certificate, attestations.
- The release notes say what changed in plain words and anything a user must do (for example, Windows users of 1.1.0 or earlier uninstall first).

## Versions

- `android-vX.Y.Z` releases only the phone app, `google-tv-vX.Y.Z` only the Google TV app, `mac-vX.Y.Z` and `windows-vX.Y.Z` only that desktop build; a plain `vX.Y.Z` releases all four. A change to shared code (the casting core, the interface, the language files) goes out with a plain tag.
- A platform's next version is higher than both its own last tag and the last plain tag (KS-Suite [versioning and signing](https://github.com/kumasumit/ks-suite/blob/main/specs/versioning-signing.md), "Platform tags").
- KS Beam has no in-app updater, so it needed no bridge release.

## The owner's hand checks

- Checks on real devices (a real TV and phone on one network, the Mac window, Windows) come after the release and never block it. What they find goes into the next version.

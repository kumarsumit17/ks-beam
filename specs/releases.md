---
type: Policy
title: Releases
description: How KS Beam is released: DEV builds on dev, automated and hand tests, the owner's approval per platform, then production; one version per platform or one for all.
tags: [release, versioning, policy]
---

# Releases

The suite rule "DEV, test, approval, production" (KS-Suite [release cycle](https://github.com/kumasumit/ks-suite/blob/main/specs/release-cycle.md), from 2026-11-27, replacing "build, test, release, no waiting"), as KS Beam applies it. `v1.7.2` on ks-utility `v0.18.4` was KS Beam's initial-rollout release under the old rule.

## DEV, test, approval, production

- All work is on `dev`, which starts from `main` (the latest production release). Every feature or fix is tested automatically: the tests, CI, and the emulators (`scripts/emulators.sh` in the KS-Suite workspace).
- A DEV build is a rehearsal of the release workflow on `dev` (a manual run with a version and a platform: no attestation, no release). The owner tests it by hand on real devices; fixes go into new DEV builds.
- A production tag (plain or platform) is pushed only after the owner's explicit approval, in the conversation, for KS Beam and for that platform. Approval for one platform never covers another. The one exception is a platform's first-ever release (automated tests and signing are enough).
- A new ks-utility version moves the pin on `dev` (the bump workflow) but never starts a production release.
- Never commit or release while a test or CI fails.
- A feature raises MINOR, a fix raises PATCH.
- Android builds are always signed with the suite key and the signature is verified; the macOS and Windows builds may ship unsigned. Every release has `SHA256SUMS`.
- At a release `main` fast-forwards to `dev`, then the tag is pushed; the release workflow (`.github/workflows/release.yml`, ks-utility's shared workflow) builds, signs, attests and publishes. The published files are checked afterwards: names, version codes, the suite certificate, attestations.
- The release notes say what changed in plain words, anything a user must do (for example, Windows users of 1.1.0 or earlier uninstall first), and the ks-utility version the release was built on.

## Versions

- `android-vX.Y.Z` releases only the phone app, `google-tv-vX.Y.Z` only the Google TV app, `mac-vX.Y.Z` and `windows-vX.Y.Z` only that desktop build; a plain `vX.Y.Z` releases all four. A change to shared code (the casting core, the interface, the language files) goes out with a plain tag.
- A platform's next version is higher than both its own last tag and the last plain tag (KS-Suite [versioning and signing](https://github.com/kumasumit/ks-suite/blob/main/specs/versioning-signing.md), "Platform tags").
- KS Beam's updater (from v1.7.0) is ks-utility's, which already reads platform tags, so no bridge release was needed: older versions have no updater. Each app asks only for its own file: the phone app for `ks-beam-v<version>.apk`, the TV app for `…-google-tv.apk`, the Mac for the arm64 `.dmg`, Windows for the x64 `.msi`. Release asset names must keep those markers.

## The owner's hand checks

- Checks on real devices (a real TV and phone on one network, the Mac window, Windows) are made on the DEV build, before the owner approves that platform's production release.

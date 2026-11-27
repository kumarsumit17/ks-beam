---
type: Policy
title: Constraints
description: The privacy and network rules every change to KS Beam must keep.
tags: [privacy, network, policy]
---

# Constraints

- **Local network only.** KS Beam talks only to devices on the local network: SSDP discovery, the renderers' description and control addresses, and its own file server. Its one internet request is the update check (the owner's decision, 2026-11-27): ks-utility's updater asks GitHub for KS Beam's releases at most once a day (it can be turned off, and a build from source never checks by itself), and the download of a newer version on request, verified against its SHA-256 before it is opened. Nothing else is sent.
- **Only what the user chose is served.** The file server answers only the file being cast, at an address with a 128-bit random token, and stops when casting stops. It never lists folders.
- **Nothing leaves the network.** No analytics, crash reporting, ads or accounts (suite rule). The renderer receives the file's name as its title and nothing else about the user.
- **No copies.** Files are read in place and never copied or uploaded.
- **Plain HTTP on the local network.** DLNA has no encryption: discovery, descriptions, control and the media travel as plain HTTP between devices on the same network, so Android allows cleartext traffic (`network_security_config.xml`). Anyone on the same network can see what is being cast; the random-token addresses keep them from fetching other files.
- **Android permissions:** `INTERNET` (sockets, required even for the local network), `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `ACCESS_LOCAL_NETWORK` (Android 17) and `REQUEST_INSTALL_PACKAGES` (the updater hands the verified APK to the system installer; the user allows "install unknown apps" first). No storage permission: files are chosen with the system file picker.

---
type: Policy
title: Constraints
description: The privacy and network rules every change to KS Beam must keep.
tags: [privacy, network, policy]
---

# Constraints

- **Local network only.** KS Beam talks only to devices on the local network: SSDP discovery, the renderers' description and control addresses, and its own file server. It makes no internet connection (an app-update check would be the only exception, and needs the owner's decision first).
- **Only what the user chose is served.** The file server answers only the file being cast, at an address with a 128-bit random token, and stops when casting stops. It never lists folders.
- **Nothing leaves the network.** No analytics, crash reporting, ads or accounts (suite rule). The renderer receives the file's name as its title and nothing else about the user.
- **No copies.** Files are read in place and never copied or uploaded.
- **Android permissions:** `INTERNET` (sockets, required even for the local network), `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` and `CHANGE_WIFI_MULTICAST_STATE`. No storage permission: files are chosen with the system file picker.

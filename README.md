# KS Beam

Cast videos, music and photos from your phone or computer to a TV or projector. Part of [KS-Suite](https://github.com/kumarsumit17/KS-Suite).

| | |
|---|---|
| **Platform** | Android phone · Windows · macOS (send) · Google TV and Android TV (receive) |
| **Tech stack** | Kotlin Multiplatform, Compose Multiplatform; DLNA / UPnP AV with no third-party casting library |
| **Status** | First release v1.0.0: milestone 1 of the [plan](specs/plan-v1.md) |

## How it works

KS Beam finds TVs and projectors on your Wi-Fi that accept DLNA (most smart TVs from Samsung, LG, Sony, Philips, Hisense, TCL and Panasonic, and many Android projectors), then plays the file you choose on them. The file stays on your device and is streamed over your local network; nothing goes to the internet. Details: [specs/casting.md](specs/casting.md).

**Google TV or Android TV without DLNA?** Install the same APK on the TV and open KS Beam there: it becomes a receiver ([specs/receiver.md](specs/receiver.md)).

If your TV isn't found, turn on its media sharing / DLNA setting and check that it's on the same Wi-Fi as your phone or computer. On Windows and macOS, allow KS Beam through the firewall when asked, so the TV can fetch the file.

## Build

```bash
git submodule update --init
./gradlew check                          # tests and language files
./gradlew :desktop:mac:run               # macOS
./gradlew :mobile:android:installDebug   # Android
```

## License

MIT. See [LICENSE](LICENSE).

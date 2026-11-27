# ks-beam

Part of KS-Suite; the suite-wide rules in the workspace `CLAUDE.md` apply. Casts videos, music and photos from an Android phone, Windows or macOS to TVs and projectors over DLNA / UPnP. Package `com.ks.beam`.

## Specs
- All specs for this project live in `specs/` in the repository root, as an Open Knowledge Format (OKF) v0.2 bundle. Never create spec files anywhere else. Start at `specs/index.md`.
- One topic per file. Every file except `index.md` and `log.md` starts with YAML frontmatter: `type` (required: `Specification`, `Policy`, `Plan`, `Playbook` or `Reference`), `title`, `description` (one sentence), `tags`.
- When a spec is added: list it in `specs/index.md` with its description. When a spec is added or changed: add a line under today's date (`# YYYY-MM-DD`, newest first) in `specs/log.md`.
- Link between specs with relative markdown links (`[Casting](casting.md)`), so they also work on GitHub.

## Stack
- Kotlin Multiplatform + Compose Multiplatform. JDK 17, Gradle Kotlin DSL, catalog at `gradle/libs.versions.toml`.
- Casting: DLNA / UPnP AV with no third-party library (`specs/casting.md`). No Google Cast SDK, no AirPlay.
- Work follows `specs/plan-v1.md`, one milestone at a time.

## Module layout
| Module | Purpose |
|---|---|
| `shared/src/jvmSharedMain` | Casting core (`core/`: SSDP, device description, AVTransport, file server) and the whole interface (`ui/`) |
| `shared/src/commonMain/lang` | Language files (`en.json`, `hi.json`) only |
| `shared/src/desktopMain` | Window, file dialog and settings for macOS and Windows |
| `shared/src/androidMain` | What both Android apps share: the manifest (permissions, name, icon, languages, network rules), content-URI files, settings and language |
| `shared/src/desktopTest` | Core tests against a fake renderer and a fake SSDP responder |
| `shared/src/jvmSharedMain/.../core/receiver` | Receiver protocol: SSDP announcer, description and control server (`specs/receiver.md`) |
| `mobile/android/` | The phone app (`com.ks.beam`), the sender: its activity (file picker, multicast lock), `CastService` (foreground service, media notification, wake and Wi-Fi locks while casting) and launcher entry |
| `tv/google-tv/` | The Google TV app (`com.ks.beam`, a separate APK), the receiver: `ReceiverService` (foreground service from app open: player, receiver, Media3 session), Media3 playback, the ready screen, the Leanback entry and banner |
| `desktop/mac/`, `desktop/windows/` | Desktop entry points (`DesktopOptions`: menus, title bar, Open With, single instance on Windows), keep-awake while casting (JNA: IOKit, kernel32), Now Playing and the media keys on macOS (`NowPlaying.kt`, JNA to the Objective-C runtime) and native packaging (.dmg, .msi) |

## Constraints
- Local network only, except the daily update check to GitHub (`specs/constraints.md`); the file server answers only the file being cast, at a random-token address (`specs/constraints.md`).
- A running cast holds the platform's cast lock (`BeamPlatform.castLock`): the sender is the TV's file server, so it must not sleep or be frozen (`specs/casting.md`, section 2).
- The core stays dependency-free JVM code: `java.net`, `HttpURLConnection`, the platform XML parser (DOCTYPEs refused).
- Icons: KS Beam's own everywhere, never a framework default (the suite's "App icons" rule): the adaptive launcher icon (`shared/src/androidMain/res/mipmap-anydpi-v26`, round and monochrome too), the TV banner, `desktop/mac/icon.icns`, `desktop/windows/icon.ico` and the window icon (`shared/src/desktopMain/resources/ks-beam-icon.png`). All are drawn from the launcher artwork.
- Sender keys (`BeamState.handleKey`): Space plays or pauses, left and right seek 10 s, Esc stops, matching the TV remote; macOS adds its menu bar (⌘O, ⌘.), Windows adds Ctrl+O.

## Commands
```bash
./gradlew :shared:desktopTest                 # casting core tests
./gradlew check                               # tests + language-file check
./gradlew :mobile:android:installDebug        # phone app (sender) on a phone or emulator
./gradlew :tv:google-tv:installDebug          # Google TV app (receiver) on a TV or TV emulator
./gradlew :desktop:mac:run                    # macOS (or :desktop:windows:run on Windows)
./gradlew :desktop:mac:packageReleaseDmg      # .dmg; needs a non-Homebrew JDK (e.g. Corretto) or CI
```
Emulators are behind their own NAT and can't be discovered. Test the receiver on the TV emulator by forwarding its port and casting from the Mac: `adb forward tcp:49152 tcp:<port from logcat "receiver ready at">`, then `KS_BEAM_RENDERER=http://127.0.0.1:49152/desc.xml KS_BEAM_MEDIA=<file.mp4> KS_BEAM_MEDIA_HOST=10.0.2.2 ./gradlew :shared:desktopTest --tests '*EmulatorCastTest*'`. Emulators: the workspace's `scripts/emulators.sh` and `specs/testing.md`.
Release builds are shrunk: R8 on Android (`android-rules.pro`), ProGuard on the desktops (`desktop-rules.pro`). `-Pks.beam.r8debug=true` shrinks the debug build too, to test the shrunk code with the debug-only hooks. `KS_BEAM_SMOKE_TEST=<file>` makes a packaged desktop build open, check its native calls, write "ok" and quit (CI runs it on Windows).
To cast from the phone emulator or the desktop app to a renderer SSDP can't reach, name its description: `adb shell am start -n com.ks.beam/.android.MainActivity --es ks.beam.renderer http://10.0.2.2:49152/desc.xml` (debug builds only), or `KS_BEAM_RENDERER=<url>` for the desktop app.

## Phone and Google TV apps
- Two Android apps since v1.1.0 (the suite rule "Phone and Google TV builds" in the workspace's `specs/platform-folders.md`): the phone app (`mobile/android`, `ks-beam-v<version>.apk`) is always the sender, and the Google TV app (`tv/google-tv`, `ks-beam-v<version>-google-tv.apk`) is always the receiver. The role comes from the app, never from the device.
- Same package name, version and keystore, set once in the root `build.gradle.kts`; the TV app's `versionCode` adds 1,000,000,000. The TV manifest requires leanback, does not require a touchscreen or Wi-Fi, has the banner and sets `com.ks.beam.FORM_FACTOR` = `tv`.
- Code both apps run goes in `shared/src/androidMain`, never copied between the two modules. The release workflow builds both (`tv-module`).
- ks-utility's updater on every platform (the owner's decision, 2026-11-27): `shared/src/jvmSharedMain/.../update/` (`GitHubUpdater`, `UpdateService`) and `ui/UpdateSection.kt` (the "App updates" button and dialog, and the notice). The phone app asks for `UpdatePlatform.ANDROID`, the TV app for `UpdatePlatform.GOOGLE_TV` (`androidUpdater(…, tv)`), the desktops for `UpdatePlatform.current()` with ks-utility's `DesktopInstaller`. The desktop version reaches the app as `-Dks.beam.version`.

## Releases
- The suite rule "Build, test, release, no waiting" (`specs/releases.md`): every feature or fix is built, tested on the emulators or in CI, pushed to `dev` and released with the next tag, without asking anyone before the tag. Never commit or release while a test or CI fails.
- A feature raises MINOR, a fix raises PATCH. Each platform has its own version: `android-vX.Y.Z` (phone app), `google-tv-vX.Y.Z` (TV app), `mac-vX.Y.Z`, `windows-vX.Y.Z` release only that build; a plain `vX.Y.Z` releases all four. A platform's next version is higher than both its own last tag and the last plain tag (KS-Suite `specs/versioning-signing.md`, "Platform tags"). KS Beam's updater (from v1.7.0) already reads platform tags, so no bridge release.
- The owner's hand checks on real devices (a real TV and phone, the Mac window, Windows) happen after the release and never block it; what they find goes into the next version.

## Platform folders
- Every KS-Suite app repo has the same top-level platform folders: `tv/google-tv/`, `desktop/mac/`, `desktop/windows/`, `mobile/android/` and `mobile/ios/`. All five exist in every app repo, whether or not the app supports that platform yet.
- This app targets `mobile/android`, `desktop/windows` and `desktop/mac` (senders) and `tv/google-tv` (the receiver). `mobile/ios` is not a target. Do not add code for a platform that is not a target without the owner asking.
- Three layers, narrowest first: a platform folder holds code for one platform of this app; `shared/` holds code used by two or more platforms, written once and never copied between platform folders; `ks-utility` holds generic code used by several apps.
- Never rename, merge or remove these folders, and never add another top-level platform folder; the layout is a suite-wide rule.

## Shared code (`ks-utility`)
- A git submodule at `ks-utility/`, pinned to a version tag (`v0.15.0`), included with `includeBuild("ks-utility/kotlin")`. After cloning, run `git submodule update --init`.
- The language files go through ks-utility's `lang.mjs` (Gradle tasks `languageResources` and `checkLanguageFiles`; needs Node 23.6+).
- Never edit files under `ks-utility/` from this repo. Change them in the `ks-utility` repository, then move the pin in its own commit.

## Working efficiently (keep token use low)
- Use the knowledge graph first: follow the rules in the `graphify` section at the end of this file. [graphify](https://github.com/Graphify-Labs/graphify) is a development tool only; it is never a dependency of the code and is never bundled or shipped.
- One-time setup on a new machine (owner): `uv tool install graphifyy` (the package name has two y's), then `/graphify .` in Claude Code. The setup in `.claude/` is committed; `graphify-out/` is generated and git-ignored.
- Read only the specs the task needs, and do not re-read a file that has not changed in this session.
- Never read generated files: `build/`, `.gradle/`, `graphify-out/graph.json`.
- Keep command output short (show only failures), and keep summaries brief.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. When the answer is under a Markdown heading, `graphify section "<heading>"` prints that section's text, so the .md need not be opened. These return a scoped subgraph, usually much smaller than GRAPH_REPORT
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- `graphify-out/` is generated and git-ignored: never commit it.

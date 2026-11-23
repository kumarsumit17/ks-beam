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
| `shared/src/androidMain` | Content-URI files, settings and language for Android |
| `shared/src/desktopTest` | Core tests against a fake renderer and a fake SSDP responder |
| `mobile/android/` | Android entry point (`com.ks.beam`) |
| `desktop/mac/`, `desktop/windows/` | Desktop entry points and native packaging (.dmg, .msi) |

## Constraints
- Local network only; the file server answers only the file being cast, at a random-token address (`specs/constraints.md`).
- The core stays dependency-free JVM code: `java.net`, `HttpURLConnection`, the platform XML parser (DOCTYPEs refused).
- Desktop keyboard: space play/pause, arrows seek (to add with milestone 2) must match TV remotes once the receiver exists.

## Commands
```bash
./gradlew :shared:desktopTest                 # casting core tests
./gradlew check                               # tests + language-file check
./gradlew :mobile:android:installDebug        # Android phone or emulator
./gradlew :desktop:mac:run                    # macOS (or :desktop:windows:run on Windows)
./gradlew :desktop:mac:packageReleaseDmg      # .dmg; needs a non-Homebrew JDK (e.g. Corretto) or CI
```
The phone emulator is behind its own NAT and never sees TVs on the Wi-Fi; test discovery and casting on a real phone or the desktop app. Emulators: the workspace's `scripts/emulators.sh` and `specs/testing.md`.

## Platform folders
- Every KS-Suite app repo has the same top-level platform folders: `tv/google-tv/`, `desktop/mac/`, `desktop/windows/`, `mobile/android/` and `mobile/ios/`. All five exist in every app repo, whether or not the app supports that platform yet.
- This app targets `mobile/android`, `desktop/windows` and `desktop/mac` (senders). `tv/google-tv` is planned (the receiver, milestone 3) and holds only a `.gitkeep` until then; `mobile/ios` is not a target. Do not add code for a platform that is not a target without the owner asking.
- Three layers, narrowest first: a platform folder holds code for one platform of this app; `shared/` holds code used by two or more platforms, written once and never copied between platform folders; `ks-utility` holds generic code used by several apps.
- Never rename, merge or remove these folders, and never add another top-level platform folder; the layout is a suite-wide rule.

## Shared code (`ks-utility`)
- A git submodule at `ks-utility/`, pinned to a version tag (`v0.4.2`), included with `includeBuild("ks-utility/kotlin")`. After cloning, run `git submodule update --init`.
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

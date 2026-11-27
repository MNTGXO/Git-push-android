<div align="center">

<img src="design/app_icon_source.png" width="120" alt="GitPulse icon" />

# GitPulse

**Push a zip. Mirror a folder. Ship to GitHub — from your pocket.**

A full-power, GitHub-first Android client with an opinionated *mirror sync* engine:
upload a `.zip` (or a whole folder), GitPulse extracts it safely, diffs it against the
branch using **git's own blob SHA-1**, uploads only real changes, and **deletes files from
GitHub that no longer exist in your upload** — all in one atomic commit.

[![Build APK](https://github.com/MNTGXO/Git-push-android/actions/workflows/ci.yml/badge.svg)](https://github.com/MNTGXO/Git-push-android/actions/workflows/ci.yml)

</div>

---

## Why it's different

| | Typical mobile git app | **GitPulse** |
|---|---|---|
| Upload a zip | ❌ | ✅ safe extraction, zip-slip protected, junk stripped |
| Mirror a folder | ❌ | ✅ recursive SAF copy, then exact diff |
| Delete remote files missing locally | ❌ | ✅ **automatic** (toggleable) |
| Compute changes without downloading | ❌ | ✅ local `blob <size>\0<content>` SHA-1 vs remote tree |
| N commits for N files | ❌ | ✅ **one atomic commit** via git-data API |
| Design | dated | ✅ custom design system, motion language, dark/AMOLED |

## Feature tour

### Sync (the headline)
- **Zip → repo** and **folder → repo** with a live plan: `+ added`, `~ modified`, `− deleted`
- **Mirror deletes** — anything in the branch but not in your zip is pruned
- **Local blob hashing** — only genuinely changed bytes are uploaded (parallel, 3-way)
- **Atomic commit** — a single `tree` + `commit` + `ref update`; no half-pushed states
- Streaming progress ring, per-file log, cancel, retry, dry-run
- Safety rails: path-traversal rejection, size limits, entry caps, `.DS_Store`/`node_modules`/build-folder filtering, unreadable-file tolerance

### Pull
- Whole-repo download over git blobs (parallel), conflict policies
  (**overwrite** / **skip existing** / **keep both**), then **export as `.zip`** and share.

### GitHub everywhere
- Repositories: browse, branch switch, language bar, stars/forks/issues metrics
- Files: folder navigation + **syntax-tinted viewer** with line numbers
- Commits: history + **diff detail with per-file patches**
- Issues, Pull requests, **Actions** (workflow runs, re-run)
- Search repos & developers, trending feed, notifications inbox, gists
- Create repositories (public/private, `.gitignore` templates, README init), star, delete

### Design & motion
- **GitPulse design language**: deep-space ink base, electric violet + mint accents
- Five accent presets, dark / AMOLED / light, dynamic colour support
- Custom **Space Grotesk** display, **Inter** body, **JetBrains Mono** code faces
- Procedurally drawn logo & progress rings (sharp at any density)
- Motion tokens (`Motion.enter/move/pop/springy`), staggered entrances, press-springs,
  shared-axis navigation transitions, shimmer skeletons, haptic vocabulary
- Edge-to-edge, glass surfaces with hairline gradient strokes, aurora backdrops

### Privacy
- Token encrypted with **AES-256-GCM + Android Keystore** (hardware-backed where available)
- Optional biometric gate, no analytics, no backend, nothing leaves the device except
  direct GitHub API calls

## Architecture

```
app/src/main/java/com/mntx/gitpulse/
├── data/            GitHubApi (coroutine OkHttp), models, SecureStore, Prefs
├── sync/            SyncEngine (push/pull), DiffEngine, GitHashing, ArchiveExtractor
├── ui/
│   ├── theme/       PulseColors, typography, Motion tokens
│   ├── components/  GlassCard, PulseButton, ProgressRing, PulseLogo, banners…
│   ├── home/ explore/ library/ activity/ sync/ repo/ code/ settings/ gists/ profile/
│   └── MainShell.kt navigation graph + floating dock
└── ServiceLocator.kt
```

**Zero annotation processors** — hand-rolled DI, `Async<T>` state container, and a thin
OkHttp client (no Retrofit/Moshi) keep the APK small and the cold start fast.

## Build

The APK is built by GitHub Actions on every push:

- **Debug APK** → Actions ▸ *Compile & APK* ▸ *Artifacts* ▸ `gitpulse-debug-apk`
- **Release APK + GitHub Release** → push a tag: `git tag v1.0.0 && git push origin v1.0.0`

Local build:

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew assembleRelease      # minified, resource-shrunk
```

Requires `minSdk 24`, `compileSdk 35`, JDK 17.

### Signing a release
Create `keystore.properties` in the repo root:

```properties
storeFile=/absolute/path/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without it, releases are signed with the debug key so CI artefacts stay installable.

## First run

1. Create a **fine-grained token** at <https://github.com/settings/tokens?type=beta>
   with *Contents: read & write*, *Metadata: read*, and *Actions: read & write* (for re-runs).
2. Paste it into GitPulse — it's validated immediately and encrypted on-device.
3. Tap the centre **sync** button, pick a `.zip` or folder, choose repo/branch, review the
   plan, push.

## Tests

```bash
./gradlew testDebugUnitTest
```

- `SyncEngineTest` pins git's blob SHA-1 (`hello\n` → `ce013625…`) and every mirror-semantics
  edge case: unchanged, modified, added, **deleted**, junk filtering, size caps, empty-folder
  nuke, unknown-hash fallback.
- `UiSnapshotTest` renders the key surfaces headlessly via Robolectric + Roborazzi.

## License

MIT — see [LICENSE](LICENSE).

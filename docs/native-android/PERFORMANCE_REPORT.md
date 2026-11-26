# Caelum Native Android Performance & Visual Parity Report

## Summary

The recorded universal release artifacts were 126.93 MiB for React Native and 72.64 MiB for native, a 42.77% reduction. The historical emulator run sampled first display and memory without verifying equivalent app state. A comparable loaded-feed performance result is pending.

## Methodology & Test Environment

- **Device**: Android Emulator `emulator-5554` (`webslicer_android35`), Android 15 (API 35), `x86_64` `userdebug` image (`1080x2400`).
- **Build Configurations**:
  - **React Native Baseline**: Production release APK (`/tmp/caelum-baseline/caelum-rn-release.apk`, `am.noz.caelum`, Hermes + React Native New Architecture).
  - **Native R8 Release**: R8-minified, resource-shrunk, Baseline Profile-compiled release APK (`/tmp/caelum-native-bench/caelum-native-sbs-release.apk`, `am.noz.caelum.native` via `-PsideBySide=true`).
- **Macrobenchmark Suite**: `StartupAndScrollBenchmark` (`:benchmark:connectedBenchmarkAndroidTest`) running under `CompilationMode.DEFAULT` (which leverages the bundled ART Baseline Profile in the native release APK).
- **Memory & Graphics Profiling**: Warm `adb -s emulator-5554 shell dumpsys meminfo` and `dumpsys gfxinfo` captured after interactive multi-screen navigation across Welcome, Sign-In Typeahead, Home/Discover feed, Search/Trending, Drawer, Settings, Appearance, Infrastructure, Customization, `@bsky.app` Profile, and `Open in…` sheet.

---

## 1. Startup Latency (`StartupAndScrollBenchmark`)

Historical first-display observations on the Android 15 emulator, across three configured iterations per launch mode under `CompilationMode.DEFAULT`:

| Benchmark | Min | Median | Max |
|---|---:|---:|---:|
| `coldStartupReactNative` | 700.6 ms | 748.8 ms | 1,343.4 ms |
| `coldStartupNative` | 231.9 ms | 245.5 ms | 264.5 ms |
| `warmStartupReactNative` | 8.0 ms | 12.1 ms | 15.9 ms |
| `warmStartupNative` | 38.5 ms | 43.0 ms | 61.5 ms |

TTID measures the first displayed frame. The historical run did not verify matching startup routes, account state, or usable content at that frame. A startup comparison needs matched launch surfaces and a separate content-ready measurement if reporting time to a usable feed.

## 2. Feed Scroll Frame Timings (`StartupAndScrollBenchmark`)

The old benchmark sent coordinate swipes after launch and did not validate feed readiness or displacement. The baseline feed screenshot shows a loading spinner. The old frame samples therefore lack evidence of comparable feed scrolling and are excluded from the comparison.

The revised benchmark opens the same custom-feed link in each app and checks that the gesture replaces the first visible fixture post with a later one after a fresh process launch for each iteration. See the [rerun setup](BUILD_AND_RUN.md#side-by-side-ab-macrobenchmark).

On November 18, 2026, the native release completed all five guarded scroll iterations on a fresh API 35 emulator using the public Bluesky Team feed. The React Native baseline opened its signed-out welcome screen and failed the initial post check on the same link. This run validates the native benchmark and the failure guard. A performance comparison still requires matching signed-in test sessions.


### Live Session `dumpsys gfxinfo` Snapshot (Native Interactive Session)

Captured in `/tmp/caelum-native-bench/native-gfxinfo.txt` across 245 frames rendered during full UI walkthrough:
- **Janky frames**: `13 / 245` (`5.31%`), **Number Missed Vsync**: `0`
- **Frame percentiles**: P50 `18 ms`, P90 `19 ms`, P95 `24 ms`, P99 `32 ms` *(emulator software-assisted GL compositing)*
- **Skia (OpenGL) GPU memory usage**: `7.12 MB` (`2.88 MB` purgeable), CPU glyph/resource caches: `533.83 KB`

---

## 3. Memory Footprint (`dumpsys meminfo`)

These are historical point-in-time observations after interactive walkthroughs. A matched memory comparison requires the same account state, navigation sequence, data, and sampling point. Raw captures (`/tmp/caelum-baseline/rn-meminfo.txt` vs `/tmp/caelum-native-bench/native-meminfo.txt`):

### App Summary PSS Breakdown (KB)

| Memory Category | React Native Baseline (KB) | Native Kotlin + Compose (KB) | Delta (KB) | % Change |
|---|---:|---:|---:|---|
| **Java Heap** | `24,088` | `14,164` | `-9,924` | **`-41.2%`** |
| **Native Heap** | `68,172` | `36,044` | `-32,128` | **`-47.1%`** |
| **Code** | `68,784` | `27,156` | `-41,628` | **`-60.5%`** |
| **Stack** | `2,116` | `1,612` | `-504` | **`-23.8%`** |
| **Graphics** *(App Summary)* | `0` | `0` | `0` | `—` |
| **Private Other** | `43,192` | `5,872` | `-37,320` | **`-86.4%`** |
| **System** | `19,119` | `6,447` | `-12,672` | **`-66.3%`** |
| **TOTAL PSS** | **`225,471`** | **`91,295`** | **`-134,176`** | **`-59.5%`** |
| **TOTAL RSS** | `348,596` | `198,684` | `-149,912` | **`-43.0%`** |
| **TOTAL SWAP PSS** | `511` | `349` | `-162` | `-31.7%` |

### Key Detailed `meminfo` Table Highlights (Pss Total / Heap)

- **Native Heap**: `68,214 KB` (RN) -> `36,074 KB` (Native).
- **Dalvik Heap**: `23,027 KB` (RN) -> `13,354 KB` (Native), with Dalvik Other falling from `6,839 KB` to `1,403 KB`.
- **`.apk mmap`**: `56,723 KB` (RN) -> `281 KB` (Native).
- **`Unknown` (anonymous mappings / JIT / JS VM arenas)**: `35,841 KB` (RN) -> `4,518 KB` (Native) (`-87.4%`).
- **View hierarchy**: `468` Android `View` instances (RN) -> `27` `View` instances (Native Compose).

---

## 4. Recorded Release APK Sizes & ART Baseline Profile

The sizes and hashes below identify the historical artifacts.

| Artifact | Size (Bytes) | Size (MiB) | SHA-256 | Notes |
|---|---:|---:|---|---|
| **React Native Baseline APK** (`am.noz.caelum`, `/tmp/caelum-baseline/caelum-rn-release.apk`) | `133,090,835` | `126.93 MiB` | `9da583374e208e27705eae37c5f6edbffbecceadb75d3f2f98cde109eabe81e3` | Hermes JS bundle + RN native `.so` libs + Expo modules |
| **Native Default Upgrade-Compatible Release APK** (`am.noz.caelum`, `app/build/outputs/apk/release/app-release.apk` / `/tmp/caelum-native-bench/caelum-native-default-release.apk`) | `76,172,538` | `72.64 MiB` | `2bff882533f1eda9c22c5cb14826fc1fc6b546abd64e41938f01c2cfa8d47077` | `./gradlew :app:assembleRelease` (`isMinifyEnabled = true`, `isShrinkResources = true`, universal ABI) |
| **Native Side-by-Side Release APK** (`am.noz.caelum.native`, `/tmp/caelum-native-bench/caelum-native-sbs-release.apk`) | `76,172,550` | `72.64 MiB` | `40df7d4d5e8b3aae9815ea108600add1c096bbac18cea0891993b2108cd321e3` | `./gradlew :app:assembleRelease -PsideBySide=true` (`isMinifyEnabled = true`, `isShrinkResources = true`, universal ABI) |
| **Size Reduction (Default / Side-by-Side)** | **`-56,918,297` B / `-56,918,285` B** | **`-54.28 MiB`** | — | **`-42.77%` (`-56.92 MB`)** |

- **Bundled ART Baseline Profile**: Verified present inside the native release APKs at `assets/dexopt/baseline.prof` (`19,968` bytes in `am.noz.caelum`; `19,969` bytes in `am.noz.caelum.native`) and `assets/dexopt/baseline.profm` (`2,480` bytes), generated via `:app:generateReleaseBaselineProfile`.

---

## 5. Side-by-Side Visual Comparison

All native screenshots were captured on `emulator-5554` using T3 Code's `agent-device` CLI against the R8-minified release build (`am.noz.caelum.native`).

| Screen | React Native Baseline | Native Kotlin + Compose | Visual & Functional Notes |
|---|---|---|---|
| **Welcome (`C1`)** | — | [00_welcome.png](screenshots/native/00_welcome.png) | Caelum logo mark, Create account, Sign in, Explore public feeds, App language selector |
| **Sign-In Typeahead (`C2`)** | — | [00b_signin_typeahead.png](screenshots/native/00b_signin_typeahead.png) | Live `typeahead.waow.tech` suggestions dropdown for `"bsky"` with avatars & handles |
| **Home / Discover Feed** | [baseline/01_home.png](screenshots/baseline/01_home.png) | [native/01_home.png](screenshots/native/01_home.png) | Native renders Discover & What's Hot tabs, 4-image grid, verified badge, action bar, and bottom nav cleanly; baseline capture showed guest loading spinner |
| **Search / Explore** | [baseline/02_search.png](screenshots/baseline/02_search.png) | [native/02_search.png](screenshots/native/02_search.png) | Native displays live Trending Topics (1–10 with category, post counts, and avatar clusters) + Trending Videos banner |
| **Navigation Drawer** | [baseline/03_drawer.png](screenshots/baseline/03_drawer.png) | [native/03_drawer.png](screenshots/native/03_drawer.png) | Edge-to-edge Material 3 `ModalNavigationDrawer` with Caelum branding, auth CTAs, and all 10 primary/secondary routes |
| **Settings Hub** | [baseline/04_settings.png](screenshots/baseline/04_settings.png) | [native/04_settings.png](screenshots/native/04_settings.png) | Guest/account header card, Invite a friend, Saved feeds, Account, Privacy, Moderation, Notifications, Content & media, Appearance & Caelum |
| **Appearance & Themes (`C10`)** | [baseline/05_appearance.png](screenshots/baseline/05_appearance.png) | [native/05_appearance.png](screenshots/native/05_appearance.png) | Color Mode, Dark Variant (Dim/Dark), subordinate Material You Dynamic Color toggle, `+ Add themes`, and Theme Library (`Bluesky Default`, `Caelum Light`, `Caelum Dim`) |
| **Infrastructure Routing (`C11`)** | [baseline/06_infrastructure.png](screenshots/baseline/06_infrastructure.png) | [native/06_infrastructure.png](screenshots/native/06_infrastructure.png) | `go.bsky.app` link redirect toggle, AppView Server presets (`api.bsky.app`, `bsky.network`, `zeppelin.social`, `Eurosky`, `Blacksky`), and custom hostname input |
| **Profile (`@bsky.app`)** | [baseline/07_profile.png](screenshots/baseline/07_profile.png) | [native/07_profile.png](screenshots/native/07_profile.png) | Banner, avatar, trusted verifier badge, follower/following/post counts, bio, tab strip, and reposted/quoted posts |
| **Customization (`C4`, `C8`, `C12`)** | — | [08_customization.png](screenshots/native/08_customization.png) | `Open Bluesky links in Caelum`, `Automatically like reposts`, and `Searchable saved posts` Room index controls |
| **`Open in…` Sheet (`C7`)** | — | [09_open_in_sheet.png](screenshots/native/09_open_in_sheet.png) | `OpenInBottomSheet` on `@bsky.app` listing `sifa.id`, `PDSls`, and `Bluesky Web` |

---

## 6. Transparent Hardware & Environment Caveats

1. **Hardware**
   - The historical runtime measurements were collected on an `x86_64` `userdebug` Android 15 emulator (`emulator-5554`). Collect release performance numbers on physical hardware.
   - Warm startup (`StartupMode.WARM`) measures activity recreation in an existing process.
   - Absolute APK sizes (`72.64 MiB` native universal APK) include all ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) and bundled ML Kit / Media3 / ZXing native dependencies; per-ABI Play App Bundle splits reduce download size further.
2. **On-Device ML Kit ALT-Text Tools (`C6`)**:
   - **Extract Text (OCR)** uses `com.google.mlkit:text-recognition:16.0.1` and runs across all supported Android devices (`minSdk = 26+`), including emulators.
   - **Suggest Description** uses `com.google.mlkit:genai-image-description:1.0.0-beta1`, which relies on **Google AICore / Gemini Nano** hardware support on compatible physical Android devices; on emulators and non-AICore devices, `OnDeviceAltTextEngine` gracefully checks `checkFeatureStatus()` and reports unavailability rather than crashing.

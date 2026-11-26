# Build & Run Guide — Caelum Native Android (`android-native/`)

The native Kotlin + Jetpack Compose Android implementation of Caelum lives under [`android-native/`](../../android-native) as a three-module Gradle project (`:app`, `:benchmark`, `:baselineprofile`).

---

## 1. Prerequisites

- **JDK**: **17** (`JavaVersion.VERSION_17` / `JvmTarget.JVM_17` configured across `:app`, `:benchmark`, and `:baselineprofile`).
- **Android SDK**:
  - `compileSdk = 37`
  - `targetSdk = 36`
  - `minSdk = 26` for `:app` (`minSdk = 28` for `:benchmark` and `:baselineprofile`)
- **Gradle Wrapper**: Use [`android-native/gradlew`](../../android-native/gradlew) (pinned to **Gradle 9.8.0** in [`gradle-wrapper.properties`](../../android-native/gradle/wrapper/gradle-wrapper.properties), AGP `9.4.1`, Kotlin `2.3.21`, KSP `2.3.14` in [`libs.versions.toml`](../../android-native/gradle/libs.versions.toml)).
- **Android Emulator / Device** (for UI tests, Baseline Profiles, and Macrobenchmarks): API 28+ (Android 15/16 recommended) with `adb` connected.

All commands below assume your working directory is `android-native/`:

```bash
cd android-native
```

---

## 2. Building & Installing APKs

### Default Upgrade-Compatible Build (`am.noz.caelum`)

By default, `:app` builds with the exact production package identity and SDK bounds of the React Native Caelum app so it installs directly as an in-place upgrade:

- `applicationId = "am.noz.caelum"`
- `app_name = "Caelum"`
- `minSdk = 26`, `targetSdk = 36`, `compileSdk = 37`
- `versionName = "1.138.0-native"`

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK (R8 minification + resource shrinking enabled)
./gradlew :app:assembleRelease
```

### Side-by-Side Dev / Comparison Build (`am.noz.caelum.native`)

Pass `-PsideBySide=true` to install the native build alongside the existing React Native `am.noz.caelum` app on the same device or emulator:

- `applicationId = "am.noz.caelum.native"` (`applicationIdSuffix = ".native"`)
- `app_name = "Caelum Native"`

```bash
# Side-by-side Debug APK
./gradlew :app:assembleDebug -PsideBySide=true

# Side-by-side Release APK
./gradlew :app:assembleRelease -PsideBySide=true
```

---

## 3. Optional Push Gateway & Firebase Configuration

[`app/build.gradle.kts`](../../android-native/app/build.gradle.kts) reads optional push gateway and Firebase settings from Gradle properties or environment variables:

| Setting | Gradle Property | Environment Variable | Effect |
|---|---|---|---|
| Push Gateway DID | `-PPUSH_GATEWAY_DID=...` | `EXPO_PUBLIC_PUSH_GATEWAY_DID` | Sets `BuildConfig.PUSH_GATEWAY_DID`. Runtime [`PushGatewayManager`](../../android-native/app/src/main/java/am/noz/caelum/core/network/PushGatewayManager.kt) defaults to `did:web:push.noz.am#bsky_notif` and registers `appId = "am.noz.caelum"`. |
| Firebase Config | `-PCAELUM_GOOGLE_SERVICES_FILE=/path/to/google-services.json` | `CAELUM_GOOGLE_SERVICES_FILE` | Copies the file to `android-native/app/google-services.json` and applies the `com.google.gms.google-services` plugin when present. |

Example build with Push Gateway and Firebase enabled:

```bash
./gradlew :app:assembleRelease \
  -PPUSH_GATEWAY_DID=did:web:push.noz.am#bsky_notif \
  -PCAELUM_GOOGLE_SERVICES_FILE=/abs/path/to/google-services.json
```

When `google-services.json` is not supplied, the build skips the `google-services` plugin cleanly and in-app notifications continue to function without FCM.

---

## 4. Testing, Baseline Profiles & A/B Macrobenchmarks

### Unit Tests (JVM + Robolectric + MockWebServer + Room)

Runs all unit tests under `android-native/app/src/test/java/am/noz/caelum/` against shared TypeScript parity fixtures (`app/src/test/resources/fixtures/`):

```bash
./gradlew :app:testDebugUnitTest
```

### Compose UI / Instrumentation Tests

Runs Android instrumentation tests against the side-by-side build on a connected device/emulator:

```bash
./gradlew :app:connectedDebugAndroidTest -PsideBySide=true
```

### Baseline Profile & Startup Profile Generation

Generates the ART Baseline Profile and Startup Profile using [`BaselineProfileGenerator`](../../android-native/baselineprofile/src/main/java/am/noz/caelum/baselineprofile/BaselineProfileGenerator.kt):

```bash
./gradlew :app:generateReleaseBaselineProfile -PsideBySide=true
```

To target a specific package ID explicitly, pass `-Pandroid.testInstrumentationRunnerArguments.targetAppId=am.noz.caelum` (defaults to `am.noz.caelum.native`).

### Side-by-Side A/B Macrobenchmark

[`StartupAndScrollBenchmark`](../../android-native/benchmark/src/main/java/am/noz/caelum/benchmark/StartupAndScrollBenchmark.kt) measures cold and warm first display and loaded-feed frame timing for React Native (`am.noz.caelum`) and native Compose (`am.noz.caelum.native`).

Use a dedicated benchmark device with both release builds, matching session state and display settings. For feed timing, choose a public custom feed that serves the same ordered posts, media, and pagination to both builds for the duration of the run. Use an existing stable feed or a fixed fixture. Confirm that the first unique plain-text post is visible on load and that a later unique plain-text post outside the starting viewport becomes visible after a center swipe up in both clients. Keep the same feed and marker texts across both cases. Live or personalized feeds qualify only when their content and ordering can be held constant.

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest -PsideBySide=true \
  "-Pandroid.testInstrumentationRunnerArguments.feedUrl=${BENCHMARK_FEED_URL}" \
  "-Pandroid.testInstrumentationRunnerArguments.feedFirstPost=${BENCHMARK_FIRST_POST}" \
  "-Pandroid.testInstrumentationRunnerArguments.feedLaterPost=${BENCHMARK_LATER_POST}"
```

Provide a canonical `https://bsky.app/profile/<did-or-handle>/feed/<rkey>` URL and a unique visible phrase from each of the two posts as the three variables. Setup waits for the first post to load outside frame collection. Each iteration starts a new process outside frame collection. The measured block validates the forward swipe, and a missing marker fails the run. Compare traces from successful runs on the same physical device with the same compilation mode. The benchmark suppresses AndroidX's emulator warning so the harness can also be exercised on an emulator.

---

## 5. Legacy React Native -> Native Upgrade Migration

When a user upgrades an existing React Native `am.noz.caelum` installation in-place to the native Android build, [`SessionManager.initialize()`](../../android-native/app/src/main/java/am/noz/caelum/core/session/SessionManager.kt) invokes [`LegacyRnMigrationManager.migrateIfNeeded()`](../../android-native/app/src/main/java/am/noz/caelum/core/storage/LegacyRnMigrationManager.kt) before reading sessions or rendering UI.

### One-Time Guard & Storage Targets

- **Migration guards**. `legacyRnMigrated` tracks the SQLite and SharedPreferences import. A separate `legacy_mmkv_migrated` key lets [`PreferencesRepository`](../../android-native/app/src/main/java/am/noz/caelum/core/storage/PreferencesRepository.kt) fill missing MMKV-backed settings on installs with the earlier marker. Recovery preserves existing native values and leaves theme selection alone because earlier native versions removed the key when users reset to the default theme.
- **1. AsyncStorage SQLite (`RKStorage` -> `catalystLocalStorage`, key `BSKY_STORAGE`)**:
  - `session.accounts` & `session.currentAccount.did` -> persisted to `PreferencesRepository`, while `accessJwt` and `refreshJwt` are encrypted per DID into [`AndroidKeystoreTokenVault`](../../android-native/app/src/main/java/am/noz/caelum/core/storage/KeystoreVault.kt) (`AES/GCM/NoPadding`, Keystore alias `caelum_session_aes_key_v1`, `caelum_token_vault` `SharedPreferences`).
  - `colorMode`, `darkTheme`, `languagePrefs` (`primaryLanguage`, `contentLanguages`, `postLanguage`, `postLanguageHistory`, `appLanguage`), accessibility/media flags (`requireAltTextEnabled`, `largeAltBadgeEnabled`, `disableAutoplay`, `disableHaptics`), and `mutedThreads` -> migrated into DataStore.
- **2. MMKV `bsky_device`**. The reader decodes binary records and their `{"data": ...}` JSON envelopes in place. It imports `searchableBookmarks`, `autoLikeReposts`, `openBskyLinksInCaelum`, `fontScale`, `fontFamily`, and `infrastructure`. The old device-level `witchskySelectedTheme` is a fallback for first-time imports.
- **3. MMKV `bsky_account`**. The first import uses `$did:witchskyTheme` for the active account. It also reads `$did:hiddenPosts` into per-account moderation settings where present.
- **4. Notification `SharedPreferences` (`"xyz.blueskyweb.app"`) & Fallback Compatibility**:
  - Migrates `playSoundChat` into DataStore while keeping `"xyz.blueskyweb.app"` `SharedPreferences` in sync (`playSoundChat` and `disabledPushReasons:$did`) so [`CaelumFirebaseMessagingService`](../../android-native/app/src/main/java/am/noz/caelum/push/CaelumFirebaseMessagingService.kt) and [`PreferencesRepository`](../../android-native/app/src/main/java/am/noz/caelum/core/storage/PreferencesRepository.kt) preserve identical client-side suppression behavior.
- **5. Searchable Saved Posts Compatibility**:
  - [`SearchableBookmarkEngine`](../../android-native/app/src/main/java/am/noz/caelum/core/storage/CaelumDatabase.kt) supports normalizing legacy RN scope keys (`normalizeRnScopeKey`) alongside Room's `bookmark_index` table and gzipped snapshots.

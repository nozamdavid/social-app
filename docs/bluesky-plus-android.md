# Caelum Android

Independent Android fork of the client in this repository. This is separate
from the Bsky+ browser extension.

Work map: [Android fork in Taproot (original project name)](http://192.168.0.199:8789/p/L2k7ZXwGIDDmGn).
Assessment: [Android fork dependencies](android-fork-assessment.md).

## Current release scope

Remote push notifications are out of scope for this release, as confirmed on
2026-11-09. Keep permission prompts, token acquisition and upstream push
registration disabled. No Firebase sender or independent push backend is
required for this release. In-app notifications remain available and still need
normal functional testing.

## Naming

Caelum is the Android client. References to the Bluesky network, accounts,
CDN/AppView, moderation services, official app, policy links and upstream
attribution retain their names. Local app labels, permissions, wordmark,
external-link prompts and the saved-media album use Caelum.

The existing package `am.noz.blueskyplus`, `blueskyplus://` links, Expo slug,
`IS_BLUESKY_PLUS` build switch and related environment keys remain compatible.
The new `caelum-apk` profile aliases `bluesky-plus-apk`. Renaming the launcher
label requires a native rebuild; installed app data remains under the same ID.
Dated emulator reports and screenshots below retain the name shown at capture.

## Current foundation

The opt-in `caelum-apk` EAS profile sets
`EXPO_PUBLIC_APP_VARIANT=bluesky-plus` and produces an internal APK. Always select
Android; this profile does not configure an independent iOS app.

- Display name: Caelum.
- Android package: `am.noz.blueskyplus`.
- URL scheme: `blueskyplus` (also used by the Android share-intent module).
- OTA updates disabled, including upstream certificate configuration.
- Product metrics disabled before event queuing or timer setup.
- GrowthBook uses an empty local configuration with no remote initialization or
  refresh. Feature consumers use their existing fallback values; feature parity
  has not been validated.
- Bitdrift build plugin and runtime key disabled. Android auto-linking also
  excludes its native SDK for the fork variant. The JavaScript dependency
  remains in the package manifest for upstream builds.
- Remote push permission prompts, token acquisition/registration listeners and
  unregister calls disabled. In-app notification queries remain available.
- Upstream Firebase client file omitted. HTTPS `bsky.app` links are declared
  in the Android intent filter so users can select this app as their handler.
  Automatic domain verification is not granted by this declaration.
- Existing Bluesky network services remain in use, including auxiliary config
  and regional services. This does not remove all requests to Bluesky.

The default build profiles retain upstream configuration. For local development,
set `EXPO_PUBLIC_APP_VARIANT=bluesky-plus` before generating and building native
projects as well as starting Metro. Changing the variant requires rebuilding
the native app; changing only the JavaScript environment is insufficient.

## Open Bluesky links in this app

In the Android fork, **Settings → Customization → Open Bluesky links in Caelum** opens
Android's **Open by default** screen for the installed application. Enable
**Open supported links**, then select or add `bsky.app`. If the official Bluesky
app already handles that domain, disable its supported links first. Menu labels
vary by Android version and manufacturer. If the direct link settings screen
is unavailable, the button falls back to this app's general settings page.

This is a user-selected domain association, documented by
[Android](https://developer.android.com/training/app-links/verify-applinks#user-associate-domain).
It does not need a change to Bluesky's website. Automatic verification would
still require the domain owner to publish our package and signing certificate.
The internal `blueskyplus://` scheme remains separate. A native rebuild is
required to install the updated intent filter.

## Witchsky themes

**Settings → Appearance → Themes** loads the signed-in account’s
`app.witchsky.theme.colors` records and variants from its PDS. Selection applies
across the app and is cached per account for offline startup. Default restores
the existing color-mode controls. See [the color mapping and feature notes](../src/features/witchskyThemes/README.md).

## Sentry

Create our Sentry project and set `EXPO_PUBLIC_BLUESKY_PLUS_SENTRY_DSN` to its DSN.
The fork never falls back to `EXPO_PUBLIC_SENTRY_DSN`. Reporting remains disabled
until our DSN is supplied. Source-map uploads additionally require our
`SENTRY_AUTH_TOKEN`, `SENTRY_ORG` and `SENTRY_PROJECT`. All three must be set
before the fork enables the Expo Sentry plugin. Keep the auth token in private
build secrets. Configure the upload release name as
`am.noz.blueskyplus@<package.json version>`.

Existing native reporting retains upstream sampling and PII settings, including
`sendDefaultPii: true`. Review those settings and disclosures before public use.
Do not use upstream build workflows unchanged: their environment writer, release
names and credential sources are specific to the official application.

## Build prerequisites

1. Provision our Expo project and set `BLUESKY_PLUS_EAS_OWNER` and
   `BLUESKY_PLUS_EAS_PROJECT_ID` in its build environment. Neither defaults to
   Bluesky's project in the fork variant. Own signing credentials and backups
   are also required.
2. Complete the asset and branding inventory in Taproot. The current profile
   still references upstream icons, splash images and other assets. See
   [ASSETS.md](../ASSETS.md). No public redistribution is ready yet.
3. Build with `pnpm build-android --profile caelum-apk`. The existing script
   uses EAS version management; it needs access to our configured project.
4. Verify the signed artifact on Android. Test installation alongside Bluesky,
   login, posting, chat, media and text/image/video sharing. Verify outbound
   requests and upgrades. Widgets still use canonical web URLs and need routing
   verification because the fork does not claim verified `bsky.app` links.

The former working name was Bluesky+. Review remaining assets under
upstream's forking/brand guidance before public release. Publisher support,
privacy/legal links, dynamic icons and account/signup flows also remain open.

A debug APK has been built, installed and launched in the Android 15 emulator;
see [the emulator check](bluesky-plus-emulator-test.md) for evidence and limits.
No publisher-signed release, Sentry account or Expo project has been provisioned.
Independent remote push is deferred in Taproot and is not a release blocker.

## Verification of the foundation

Source branch: `bluesky-plus-android`, based on the existing `dm-pronouns`
checkout, preserving its changes. Verified on 2026-11-09:

- `pnpm typecheck`: passed for iOS, Android and web.
- `pnpm lint`: passed.
- Focused `pnpm test --runInBand`: 4 suites, 23 tests passed, covering fork and
  upstream build configuration, analytics suppression, URL parsing and the
  existing feature client behavior.
- `pnpm prettier`: passed for the changed supported files.
- `git diff --check`: passed.

`pnpm lint-native` could not run because `swiftlint` is unavailable on this
machine; the script stops before its Kotlin check. The subsequent emulator
build compiled the Kotlin sharing change, but sharing has not been exercised.
The signed-in follow-up verified that the new row renders, its button opens
Android's Open by default screen for the app, and Add link lists `bsky.app`.
The controller then rejected Settings interactions as escaping the session app.
After the user completed domain selection manually, a normal HTTPS profile URL
was verified to open the correct native profile in Bluesky+. Post-link testing
initially triggered repeated host-emulator SIGSEGV crashes. Core inspection
implicated the legacy SwiftShader GLES renderer; switching the emulator to the
host GPU resolved the crash. Both previously failing posts now render, and a
repeat URL-only launch also passed with the same APK and app source.
Outbound traffic and coexistence with the official app also remain unverified.
See the emulator report for the complete results and tool diagnostics.

## Caelum rename verification

On 2026-11-10 the Android fork was renamed to Caelum. `pnpm prebuild --platform
android --no-install` with `EXPO_PUBLIC_APP_VARIANT=bluesky-plus` generated
`app_name=Caelum` and `rootProject.name=Caelum`, with application ID
`am.noz.blueskyplus`. An x86_64 debug APK built successfully and was installed
as an update in the running Android 15 emulator. APK metadata confirms the
Caelum label and existing application ID. The welcome wordmark and link-settings
copy use Caelum; the Infrastructure screen retains Bluesky service names.
The current icon/illustration assets were not redesigned by the naming change.

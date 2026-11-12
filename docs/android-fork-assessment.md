# Independent Android release assessment

Assessed 2026-11-09 against checkout `b1ab11aef` (app 1.138.0). Static source
inspection and official documentation research; no build, credential changes,
push registration, or runtime testing was performed.

## Conclusion and scope

Releasing an independently branded Android client for the existing Bluesky
network is feasible with moderate engineering effort. Most social functionality
already talks to network services; a fork does not inherently require hosting a
PDS, AppView, relay, chat server, or media infrastructure. This is a client fork
assessment, not a plan to operate an independent social network. Existing service
availability, access rules, and rate limits remain dependencies.

The main uncertainties are remote push delivery and the scope of asset
replacement. Monitoring is comparatively straightforward. Production update
ownership must also be fixed before distribution.

Planning estimate: allow roughly **2-4 engineer-weeks for a public MVP without
remote push or OTA updates**, assuming an experienced React Native/Expo engineer,
ready branding or a modest replacement asset set, access to build and publisher
accounts, and no major native build failures. A private signed APK proof of
concept could be a few days. These are judgment estimates, not measured work;
store verification/testing/review adds calendar time. Feature parity including
independent push requires a separate estimate after its backend feasibility is
proven. Replacing every restricted icon/illustration or building a push backend
could materially exceed the MVP allowance.

## Integration inventory

| Area                         | Evidence in this checkout                                                                                                                         | Release action                                                                                                                                           | Difficulty                        |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------- |
| Android identity             | `app.config.js` uses `xyz.blueskyweb.app`, name/slug/scheme `Bluesky`/`bluesky`, Bluesky EAS owner/project                                        | Own package ID, product name, assets, scheme, EAS project if used, signing credentials, version counter                                                  | Low-medium                        |
| Sentry                       | `src/logger/sentry/setup/index.ts` enables reporting only with a DSN outside development; config adds upload plugin only with `SENTRY_AUTH_TOKEN` | Omit both runtime DSN and upload token, or supply our own project and update hardcoded organization/project and release names                            | Low                               |
| Bitdrift                     | Dependency and native config plugin remain; `src/env/common.ts` has optional key; `docs/build.md` calls it optional                               | Leave key unset initially. Audit/remove native plugin and SDK if we want no instrumentation; no runtime initialization was found in the inspected source | Low                               |
| Product metrics              | `src/analytics/metrics/client.ts` posts event batches to `${METRICS_API_HOST}/t`; default is `events.bsky.app`                                    | Disable collection centrally or use our own receiver; simply leaving env blank does not disable it                                                       | Low-medium                        |
| Feature flags                | `src/analytics/features/index.ts` initializes GrowthBook; host/key default to Bluesky configuration                                               | Own configuration or explicit local defaults. Audit every gate needed by supported features; do not just make the host unreachable                       | Medium                            |
| Remote push                  | Native FCM token; hardcoded app ID in both registration and unregistration; notification-service proxy                                            | Own Firebase client configuration plus a sender authorized for its tokens and a supported event delivery route                                           | High / unresolved                 |
| OTA updates                  | `app.config.js` points to `updates.bsky.app/manifest` and trusts `code-signing/certificate.pem` in production                                     | Disable for MVP with `DISABLE_UPDATES=1`; later use our own endpoint/project and signing identity, adapting update hooks as needed                       | Low to disable; medium to replace |
| Links and Android sharing    | Verified `bsky.app` intent filter; `bluesky://` in JS and native Android intent module                                                            | Own scheme throughout native and JS producers/parsers; owned domain for verified links; test alongside official app                                      | Medium                            |
| Branding and licensed assets | `README.md`, `ASSETS.md`, `NOTICE.md` explicitly distinguish code from asset rights                                                               | Replace restricted assets or obtain rights where available; retain distributable asset notices                                                           | Potentially substantial           |
| CI and distribution          | Android workflow only runs for `bluesky-social/social-app`; upstream runner, secrets, EAS state and Slack hooks                                   | Fork-specific build workflow, our credentials/artifact distribution, optional Play submission                                                            | Medium                            |
| Auxiliary service features   | Geolocation, app-config and live-event workers in `src/env/common.ts`; contact discovery and age assurance APIs                                   | Decide which remain supported dependencies, which get local/owned configuration, and which features are omitted                                          | Medium; needs release testing     |

Sources: [app config](../app.config.js), [EAS profiles](../eas.json),
[environment defaults](../src/env/common.ts),
[Sentry setup](../src/logger/sentry/setup/index.ts),
[metrics client](../src/analytics/metrics/client.ts),
[feature flags](../src/analytics/features/index.ts),
[Android workflow](../.github/workflows/build-submit-android.yml),
[build environment writer](../.github/actions/write-env/action.yml),
[forking guidance](../README.md#forking-guidelines).

## Important findings

### Push is a backend dependency, not just a new Firebase file

The app uses `getDevicePushTokenAsync()`, not Expo push tokens. Its authenticated
registration carries `appId`, `platform`, `token`, and `serviceDid`; it routes via
`${BLUESKY_PROXY_DID}#bsky_notif`. The payload's `serviceDid` and proxy target are
distinct. Neither is an FCM credential or an automatic third-party integration
mechanism. Both registration and unregistration must use our identity.

We can create a Firebase project and obtain tokens, but the sender must be
authorized for that project. The client and public schema do not establish that
Bluesky will onboard a new app ID or Firebase sender. Uploading a service-account
key to EAS does not configure Bluesky's backend. Keeping Bluesky's app ID does
not solve sender authorization.

The available paths are a supported sender arrangement with Bluesky, an
independently operated event-to-push service, or an initial release without
remote push. An independent gateway also needs a usable authenticated event
source, including a separate assessment for chat, and privacy, retries and
account lifecycle handling. It cannot be assumed that changing a DID will make
Bluesky forward events. Foreground in-app notification queries are separate
from OS push and can still work without remote delivery.

See [external push and distribution research](android-fork-external-research.md)
for official sources and explicit unknowns. Registration success alone is not a
delivery test. Test social and chat delivery with the signed release on a real
device, including background/terminated app, token refresh, account switching,
logout and notification taps.

### Blank environment variables do not isolate the fork

Metrics and GrowthBook use `||` fallbacks to Bluesky defaults. Production
geolocation, live-event and app-config URLs are hardcoded; their developer URL
overrides apply only in development. Add explicit fork configuration or disable
unneeded features rather than relying on blank `.env` entries. Disabling
analytics does not require disabling local diagnostic logging.

### Production updates can replace fork code

The current production configuration trusts the checked-in upstream update
certificate and fetches upstream manifests. This leaves a possible route for
compatible upstream bundles to replace fork behavior. Disable OTA before the
first independent binary or configure our own server and certificate. The
certificate is public verification material; we do not acquire signing
authority by copying it. Android APK signing and JavaScript OTA signing are
separate systems. Expo documents verification and key ownership in its
[code-signing guide](https://docs.expo.dev/eas-update/code-signing/).

### Rebranding includes the icon system

The source is MIT licensed, but [ASSETS.md](../ASSETS.md) explicitly excludes
commissioned illustrations, the Central UI icon set, Bluesky marks/app icons,
community artwork and currently undocumented product imagery under
`assets/images/`. Licensing Central directly may be an option; the repository
does not grant its rights. For replacement icons, regenerate both the generated
components and bundled native glyph fonts, not just the SVG files; see
[icon generation](../scripts/icons/README.md). Inter and other listed permissive
assets can be retained with notices. Scope the inventory to what the Android
artifact actually ships, including hidden/optional screens and dynamic icons.

### Identity changes reach native code

Besides `app.config.js` and [JS link configuration](../src/lib/routes/linking.ts),
[`parseLinkingUrl`](../src/lib/parseLinkingUrl.ts) specifically normalizes
`bluesky://` URLs. The [Android sharing module](../modules/expo-receive-android-intents/android/src/main/java/xyz/blueskyweb/app/exporeceiveandroidintents/ExpoReceiveAndroidIntentsModule.kt)
constructs the same scheme for shared text/images/video. Changing only Expo's
scheme can therefore break sharing or launch the official app.

The hardcoded shared-preferences filename is app-private, so its old name alone
does not make the fork share data with the official app. Similarly, internal
library Java namespaces need not all be renamed to change the application ID.
Use deliberate identity changes, not a repository-wide text replacement.

Verified `bsky.app` links need the domain owner's association for our package
and certificate. We can still share canonical Bluesky content URLs, but cannot
promise those links automatically open our fork. Own-domain App Links are an
alternative. Independently signed apps also have separate app data: expect users
to log in again rather than inherit the official app's sessions.

### Signup and regional features need separate validation

[Signup](../src/screens/Signup/StepCaptcha/index.tsx) uses device attestation when
configured and has an unattested CAPTCHA path when the GCP project is zero.
The [native CAPTCHA WebView](../src/screens/Signup/StepCaptcha/CaptchaWebView.tsx)
has fixed host allowlists and callback expectations. A new GCP project does not
by itself make Bluesky accept the fork's Play Integrity results. Existing-account
login is a sensible first milestone; verify signup's fallback against production
before advertising it, or direct account creation to the existing web flow.

Contact discovery, age assurance and geolocation remain service dependencies;
they are not automatically independent just because we built the client. Verify
the intended behavior and account/privacy disclosures for the supported release.
This assessment does not claim every current first-party feature is available
to third-party publishers.

## Suggested release sequence

1. Prove an independently signed release APK installs alongside the official
   app and supports existing-account login, feeds, posting and chat. Disable
   OTA, remote push and upstream telemetry first; choose explicit feature defaults.
2. Inventory and replace/licence restricted shipped assets. Set our name,
   package, scheme, support links and publisher/privacy information. Audit
   dynamic icons, sharing, widget links, download/referral links and About UI.
3. Create a minimal repeatable Android release workflow using our signing and
   EAS configuration, or local Android builds. Existing CI requires rewriting
   upstream repository guards and credentials; the release-preparation flow is
   explicitly still a dry run in [its documentation](release-preparation/README.md).
4. Validate signed APK and Play AAB artifacts: cold start/offline launch,
   media upload/download, sharing, link routing, account switch/logout, upgrades
   preserving fork data, moderation/block/report/delete flows, supported OS
   versions and native packaging compatibility. Inspect outbound requests to
   verify no upstream telemetry or update downloads remain.
5. Prepare the Play listing, required declarations, privacy policy, test access
   and applicable publisher verification/testing. Use our production signing
   certificate for domain association. Expo distinguishes installable APKs from
   store AABs in its [production build guide](https://docs.expo.dev/deploy/build-project/).
6. Treat push as an independent feasibility milestone, and introduce our OTA
   service only after the initial release pipeline is reliable.

Ongoing work includes merging upstream changes, API/SDK compatibility, native
patches, asset license inventory, device regression testing, and signing-key
custody. Keeping fork-specific configuration and service adapters in a small
surface will reduce that cost. A Google-free/F-Droid build or a self-hosted
network is additional scope; neither follows from a normal Play/APK release.

## Limits and next decision

No native artifact was built or tested; no production API or sender support was
proven. Official store requirements and detailed external citations are in the
[companion research](android-fork-external-research.md). The next useful technical
step is a signed, isolated release-build spike. The product decision with the
largest impact is whether the first public version must include remote push.

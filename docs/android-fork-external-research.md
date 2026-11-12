# Independent Android fork: external release research

Assessed 2026-11-09 against checkout `b1ab11aef`. Research only; no builds,
registration requests, credential access, or delivery tests. Read root
`AGENTS.md` and `docs/build.md`. Other integrations and licensing/branding are
outside this assessment. Official documentation below was consulted on this date;
store requirements should be rechecked at submission.

## Assessment

An independently signed Android release needs its own package identity and
distribution setup. Push delivery is a separate unresolved dependency: obtaining
an FCM token or successfully registering it does not establish that Bluesky can
send to the fork's Firebase project. Do not promise push support until the sender
arrangement and end-to-end delivery are verified.

## What this checkout establishes

- [Push registration](../src/lib/notifications/notifications.ts) obtains
  `Notifications.getDevicePushTokenAsync()` after notification permission is
  granted, registers after login, and listens for token changes. The explicit
  get-and-register path skips `IS_DEV`, which is `__DEV__` in
  [environment configuration](../src/env/common.ts). A debug build is therefore
  insufficient to validate the normal release registration path.
- Registration sends `platform`, native `token.data`, hardcoded
  `appId: 'xyz.blueskyweb.app'`, `ageRestricted`, and `serviceDid` equal to
  `did:web:api.bsky.app` or `did:web:api.staging.bsky.dev` based on the account's
  service string. Unregistration repeats that identity. Errors are caught and
  logged; registration success is not a delivery receipt.
- The request uses the authenticated PDS client with
  `service: NOTIF_SERVICE`. [Constants](../src/lib/constants.ts) define this as
  `${BLUESKY_PROXY_DID}#bsky_notif`; its default DID is `did:web:api.bsky.app`.
  This proxy target and the payload's `serviceDid` are distinct fields. The
  [XRPC specification](https://atproto.com/specs/xrpc#service-proxying) describes
  proxy routing via `atproto-proxy` and DID service identifiers.
- The local [registerPush lexicon](../lexicons/app/bsky/notification/registerPush.json)
  requires authentication and `serviceDid`, `token`, `platform`, `appId`.
  `serviceDid` is only constrained to DID syntax; `appId` is a string. It does
  **not** document credential provisioning, third-party sender onboarding,
  accepted application IDs, or the backend's delivery/authorization rules.
- [App configuration](../app.config.js) still uses package `xyz.blueskyweb.app`,
  `./google-services.json`, scheme `bluesky`, and an auto-verified HTTPS intent
  filter for `bsky.app`. It targets API 36. [Build guidance](build.md) permits an
  example Firebase config for development, which is not evidence of working
  production push, and instructs independent EAS users to change owner/project ID.

## FCM and Expo: verified external constraints

1. **Native token, not Expo Push Service token.** Expo documents
   `getDevicePushTokenAsync()` as returning native FCM/APNs tokens, while
   `getExpoPushTokenAsync()` returns a token for Expo's push service. Token
   rotation requires updating the backend. The existing client follows the
   native-token model; uploading a sender key to EAS alone does not provision
   Bluesky's sender. See [Expo Notifications](https://docs.expo.dev/versions/latest/sdk/notifications/)
   and [direct FCM/APNs delivery](https://docs.expo.dev/push-notifications/sending-notifications-custom/).
2. **Client configuration is not sender authority.** Register the fork's exact
   Android package in Firebase and use its `google-services.json`. Firebase
   describes that file as containing non-secret identifiers, not server private
   credentials. See [Firebase Android setup](https://firebase.google.com/docs/android/setup).
   Expo separately documents a private service-account key for its hosted sender:
   [FCM V1 credentials](https://docs.expo.dev/push-notifications/fcm-credentials/).
   Keep any sender credentials on the sending service, outside the APK.
3. **The sender must be authorized for the token's project.** Firebase documents
   `SENDER_ID_MISMATCH` (403) when sender identity does not match a token's
   allowed senders. Changing the client project does not make existing backend
   credentials valid for its tokens. See [FCM errors](https://firebase.google.com/docs/cloud-messaging/error-codes).
   Credentials need not originate in the same project: Google explicitly allows
   cross-project service accounts with appropriate IAM authorization and sends
   to the target project's endpoint. See [FCM HTTP v1 authorization](https://firebase.google.com/docs/cloud-messaging/send/v1-api).

### Backend behavior still unverified

Neither this client nor the inspected API contract establishes whether Bluesky's
production notification service accepts independent `appId` values, selects
sender credentials by `appId`, supports external Firebase projects, or permits
third-party notification-service DIDs. The payload's `serviceDid` is not an FCM
project ID or credential upload mechanism. Replacing it with a fork DID cannot
be assumed to create a functioning gateway.

Practical options to assess are an explicit Bluesky-supported sender arrangement,
a separately operated event-to-push gateway with authorized FCM sending, or a
release without remote pushes. The gateway option requires an event source and
account authorization; the registerPush schema alone does not establish that
Bluesky will forward events to it. Confirm backend support, then test real social
and chat events in foreground/background/terminated states, token rotation,
logout/unregistration, and notification taps with the actual signed release.

## Android identity, signing, and links

- Use a new package name for an independent Play listing; package names are
  unique and permanent. Retain an update-compatible signing identity and
  increment `versionCode`. See [Play app setup](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en).
  Android requires signed APKs; bundles uploaded to Play use an upload key and
  Play App Signing. A separately signed fork cannot ordinarily update an
  installed official app; supported signing-key rotation is a separate managed
  process. See [Android signing](https://developer.android.com/studio/publish/app-signing).
- App Links require the domain owner to publish
  `https://<host>/.well-known/assetlinks.json` associating the fork's package and
  signing-certificate SHA-256 fingerprint. Use the **Play app-signing certificate**
  for Play installs, not merely the upload/debug certificate. Keeping
  `autoVerify: true` does not authorize a fork for `bsky.app`; that needs the
  domain owner's cooperation. An owned domain is an independent alternative.
  See [Android website associations](https://developer.android.com/training/app-links/configure-assetlinks).

## Play prerequisites to budget for

- Publisher account, distribution agreement, US$25 one-time registration fee,
  identity verification, and applicable personal-account device verification:
  [Play Console registration](https://support.google.com/googleplay/android-developer/answer/6112435?hl=en).
  Personal accounts created after 2023-11-13 require at least 12 continuously
  opted-in closed testers for 14 days before applying for production access;
  meeting the test count is not automatic approval:
  [testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).
- Signed Android App Bundle, listing/assets, content-rating and audience/app-access
  declarations, and a rising version code:
  [app setup](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en).
  Current phone/tablet submissions require target API 36 or higher from
  2026-08-31; the configured target meets that number, but the artifact is untested:
  [target API rules](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).
  Verify all native libraries and packaging for 16 KB pages; Android's current
  guide states API 35+ apps must support them and incompatible updates will be
  blocked starting 2027-02-01:
  [page-size requirements](https://developer.android.com/guide/practices/page-sizes).
- Accurate Data safety disclosures and privacy policy covering the fork and its
  SDKs: [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).
  Assess account deletion obligations if account creation remains, plus reporting,
  blocking and moderation for public UGC:
  [developer policies](https://support.google.com/googleplay/android-developer/answer/18265346?hl=en)
  and [UGC rules](https://support.google.com/googleplay/android-developer/answer/9876937/).
  Social-category apps must meet Child Safety Standards even when adults-only:
  [official policy guidance](https://support.google.com/googleplay/android-developer/answer/14747720?hl=en).
- Check Android developer verification/package registration for intended markets.
  The current rollout began 2026-09-30 for participating stores in Brazil,
  Indonesia, Singapore and Thailand, with broader expansion in 2027:
  [Android developer verification](https://developer.android.com/developer-verification).

This is a dependency assessment, not a completed store-policy audit or evidence
of backend permission. No production credentials, Play account eligibility,
domain association, native artifact compatibility, or push delivery were verified.

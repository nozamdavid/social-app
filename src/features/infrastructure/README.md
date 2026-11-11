# Infrastructure settings

Caelum exposes Settings → Infrastructure at `/settings/infrastructure`. The redirect toggle and preset choices save immediately and apply to every
account on the device. Custom hostnames are drafts until their own Apply button
is pressed; applying one field never commits the other field’s draft. An unsaved
CDN draft survives the AppView cache remount in transient memory, scoped to the
current account and settings route; reopening settings starts from saved values. The defaults
button also applies immediately. Defaults preserve
existing behavior: `go.bsky.app` post-link redirects, Bluesky AppView, and
`cdn.bsky.app`. The extension project at `/home/david/fce7tlckyr6nvxr757xqoqxm`
provided the reference hostname policy, AppView presets and image CDN presets.

## AppView

`withInfrastructureRouting` wraps clients built by `createLexClient`, reading
settings at dispatch time. Signed-in AppView requests keep their session/PDS
transport and replace the default `atproto-proxy` target with
`did:web:<hostname>#bsky_appview`. Session credentials stay with the PDS; it
performs [AT Protocol service proxying](https://atproto.com/specs/xrpc#service-proxying).

Repository calls, login/refresh, actor preferences, video, chat and moderation
services retain their own routes. Explicit non-default proxy targets are kept.
Unauthenticated GETs to the two standard Bluesky AppView origins are retargeted
to the custom host. Requests carrying Authorization or DPoP are never retargeted
to that origin. Existing public and language-sensitive custom-feed clients are
covered too. Custom AppView traffic omits the app's device/session analytics IDs.

Presets are Bluesky, Eurosky and Blacksky; a custom hostname is also supported.
Both alternative preset DID documents were checked for `#bsky_appview` services
on 2026-11-09. There is no automatic failover or claim that every provider supports
every Bluesky API. Choosing a different AppView replaces the account's query
client and changes its persisted-cache buster, retaining normal account cache
cleanup and the signed-in session. Switching is disabled during pending mutations. The current navigation stack is
restored through the query-cache remount, so switching keeps Infrastructure open.
The transient navigation snapshot is scoped to the current account and cleared
when navigation is ready.

## Images

`#/components/Image` is the shared Expo Image boundary. It rewrites `source`,
placeholders, image backgrounds, prefetch and decoded-image loads, forwarding refs
and other props to Expo. Image consumers import that boundary; type exports still
come from Expo. The plain React Native avatar path and media downloads also use
the same URL policy. No global fetch patch or API-response mutation is used.

The image CDN picker offers Bluesky (default), Luna (`luna.danielmorrisey.com`)
and Custom, matching the extension. Existing custom hostnames reopen under Custom.

Only HTTPS `cdn.bsky.app/img/<preset>/plain/…` image URLs change. Presets, CIDs,
formats and query strings are preserved. Local files, bundled assets, image refs,
video streams and unrelated image hosts remain as supplied. A custom CDN must
serve Bluesky-compatible image paths, including avatars/banners and download
presets. Changing the CDN updates rendered images without replacing query data.

## Links and validation

The redirect preference controls existing `createProxiedUrl` callers. Native link
opening reads it at click time; web links and iOS previews also subscribe to the
preference. Existing opt-in rules for which links get proxied are preserved.

Hostnames accept optional `https://` and a trailing slash. Paths, ports,
credentials, other protocols and malformed DNS labels are rejected before save.
The root provider and request dispatch share guarded raw-storage snapshots, so
malformed JSON also falls back without blocking startup. The Bluesky CDN entry restores the default, while Custom requires a hostname.
The default AppView entry restores the configured Bluesky service. Corrupt stored fields fall back independently to defaults.

## Validation

Feature tests cover hostname validation, cache busting, persistence, proxy scope,
credential retention on a real PasswordSession over a mock transport, public
routing, link preferences, source arrays, refs, placeholders and prefetch.
Run `pnpm test --runInBand src/features/infrastructure` and `pnpm typecheck`.

Android 15 emulator checks confirmed invalid AppView input is rejected, radio and
redirect controls update immediately, switching to Eurosky reloads a populated
feed while keeping the session signed in, and custom CDN input is normalized on
save. A restart preserved the AppView, redirect and CDN choices. A reserved `.invalid` hostname was used for the CDN preference test; the
image transport itself is covered by mock-render/prefetch tests rather than a
provisioned third-party CDN.
The settings entry was reached from the main Settings list, and the original
Bluesky defaults were restored after runtime checks. Captures in
`.impeccable/review/infrastructure-*-phone-android.png` document the Android
settings layout and validation state.

The CDN picker also matches the extension’s Bluesky/Luna/Custom choices. A live
Luna avatar request returned a JPEG response during the 2026-11-10 check.

Immediate-save checks on 2026-11-10 confirmed the redirect toggle, Eurosky and
Luna selections survive a full app restart without Save. Switching AppView
keeps Infrastructure open. Invalid custom AppView input is rejected; applying
the CDN still succeeds independently and normalizes its hostname. Applying
`https://api.bsky.app/` restores the default AppView without changing the saved
CDN. The defaults button applies immediately. Original settings (Bluesky
AppView/CDN, redirects off) were restored after these checks. Fresh captures:
`.impeccable/review/infrastructure-autosave-*-phone-android.png`.

The final Android remount check retained an unapplied custom CDN URL while
switching from Bluesky to Eurosky. The CDN Apply button remained enabled; the
saved CDN stayed at its default. Regression coverage includes a keyed cache
boundary remount, account isolation, and another settings route mounting first.

## PLC directory

The directory picker matches [Witchsky's Runes infrastructure settings](https://tangled.org/jollywhoppers.com/witchsky.app):
`plc.directory` (default), Eurosky (`plc.eurosky.network`), Wafflehouse.dev
(`plc.wafflehouse.dev`) and Custom. Presets save immediately; custom hostnames
require their own Apply button. Existing saved settings migrate to the default.
The defaults button resets this choice too. AppView changes retain both unapplied
CDN and PLC drafts in memory, scoped to the current account and settings route.

Reference source checked at `738ef56a8a86a5601927038704ea4439e3f62ce9`:
`src/screens/Settings/RunesSettings/InfrastructureSettings.tsx`,
`src/state/preferences/plc-directory.tsx`, `src/state/session/identity-resolver.ts`
and `src/state/queries/resolve-identity.ts`. Witchsky uses the preference for
client-side identity and PDS lookup. The current Caelum code has one direct PLC
lookup, in `pds-detection.ts`; that lookup now uses the selected directory.
PasswordSession gets its DID document from its session server and uses the
stored/current PDS; it does not perform a separate hardcoded PLC lookup.

`useHostingProvider` keys account discovery results by directory as well as
identifier, and submission captures the current directory again. Changing the
picker therefore bypasses results from the previous directory. Only public DID
documents are fetched from this directory; login credentials remain with the PDS.
`did:web` lookups still use the identity's domain. Existing signed-in sessions,
feed/profile data supplied by AppViews, and identity resolution performed inside
remote servers do not move to the selected directory. Transient directory errors
retain the existing fail-login behavior rather than silently trying another host.

Read-only probes on 2026-11-10 returned HTTP 200 and the expected DID document from
all three preset hosts, including encoded DID paths. Tests cover legacy settings,
custom validation, isolated Apply actions, cache separation, current settings at
request dispatch, `did:web`, and directory failures.

Android 15 checks confirmed that a PLC preset saves immediately and survives a
full restart. The custom field rejected HTTP input, then normalized and applied
`https://PLC.WAFFLEHOUSE.DEV/` as the Wafflehouse.dev preset. The original default
PLC directory was restored afterward, preserving the other infrastructure
preferences. Captures: `.impeccable/review/plc-presets-phone-android.png` and
`.impeccable/review/plc-custom-invalid-phone-android.png`.

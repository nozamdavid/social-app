# Caelum social push gateway

Pinned upstream: `100fd29f03348bf69491b32f461b2738bae3113d` from
https://github.com/DracoBlue/atproto-push-gateway.

The Docker build applies `caelum-expo.patch` and runs the full Go test suite.
For registrations with app ID `am.noz.caelum` (or legacy `am.noz.blueskyplus`),
the patch carries that ID
through event delivery and adapts data-only FCM to Expo's Android contract:
`title` and `message` for display, JSON `body` for `content.data`, and flat
`reason`/`channelId` for the native background handler. Other apps retain the
upstream payload. The patch includes contract tests for all six social reasons.

## Local development

From the repository root:

```sh
docker compose -p caelum-push-local -f tools/push-gateway/compose.local.yaml up -d --build
python3 tools/push-gateway/smoke-test.py
curl --fail http://127.0.0.1:18787/health
```

The local compose publishes only to host loopback. Its dev auth bypass is for
HTTP smoke tests; never put a public reverse proxy in front of this instance.
The container must bind on its own network interface to reach Docker's port
publication, hence `DEV_MODE_ALLOW_PUBLIC` inside this loopback-published container.
The smoke test registers a fabricated DID/token, checks idempotence, and removes
it. It does not send an FCM message. `/test/push` only looks up tokens.
Jetstream connects after the first registration.

To enable real FCM credentials on the loopback-only local gateway, add the
credentials overlay. The key must remain outside the checkout:

```sh
export FCM_SERVICE_ACCOUNT_PATH="$HOME/.config/caelum/firebase-sender.json"
docker compose -p caelum-push-local \
  -f tools/push-gateway/compose.local.yaml \
  -f tools/push-gateway/compose.local.credentials.yaml up -d
```

The uploaded Android client configuration is installed locally as the ignored
`google-services.json`; `.env.local` selects that file and `did:web:push.noz.am`.
The sender key is stored separately with owner-only permissions. Neither file
is committed, and the sender key is never included in the Android build.

Stop it with:

```sh
docker compose -p caelum-push-local -f tools/push-gateway/compose.local.yaml down
```

## Set up push.noz.am

1. Choose a public server with Docker Compose and a persistent disk. Create a DNS
   A record for `push.noz.am` pointing at that server's public IPv4 address. Add
   AAAA only if IPv6 reaches that same server. Allow inbound TCP 80 and 443.
2. In Firebase Console create/select your Caelum project. Add an Android app with
   package **am.noz.caelum**, download its **google-services.json**, and store
   it outside the repository. This is the Android client configuration.
3. Enable the Firebase Cloud Messaging API (HTTP v1) for that project. In project
   settings, Service accounts, generate a service-account private key authorized
   to send FCM messages in that project. Store that JSON on the gateway server,
   for example `/etc/caelum/firebase-sender.json`, readable only by its owner.
   This private key stays on the gateway; it is never the Android client file.
4. Copy `tools/push-gateway` to the server. Set the sender file path and start the
   production compose file (it does not enable dev mode):

   ```sh
   export FCM_SERVICE_ACCOUNT_PATH=/etc/caelum/firebase-sender.json
   docker compose -p caelum-push -f tools/push-gateway/compose.yaml up -d --build
   ```

   Paths above assume the same repository layout; when copying just the folder,
   run `docker compose -p caelum-push -f compose.yaml up -d --build` from it.
   Persist the environment variable in your server's deployment environment for
   later compose commands. Keep the named SQLite volume across restarts.
5. Install Caddy on the **host**, merge the supplied `Caddyfile` block into its
   existing configuration, and reload Caddy. It obtains the HTTPS certificate
   automatically when DNS and ports 80/443 work. If you already use another
   reverse proxy, route `push.noz.am` with a valid TLS certificate to
   `http://127.0.0.1:18787`, forwarding all paths and Authorization headers.
   A containerized proxy needs an explicit shared-network setup instead of this
   host-loopback example. Do not expose the gateway's container port directly.
6. Verify these from outside the server, without skipping TLS verification:

   ```sh
   curl --fail https://push.noz.am/health
   curl --fail https://push.noz.am/.well-known/did.json
   ```

   The DID document must identify `did:web:push.noz.am` and advertise
   `#bsky_notif` as `BskyNotificationService` at `https://push.noz.am`.
   Registration without service authentication must return HTTP 401. Do not put
   an interactive login page in front of DID discovery or the XRPC endpoints.

Public HTTPS was subsequently verified: health and DID discovery return HTTP
200, unauthenticated registration returns 401, and dev test endpoints return
404. The DID advertises `did:web:push.noz.am` and the expected notification
service endpoint.

## This machine's existing reverse proxy

The authenticated gateway is published on both loopback and the host LAN address.
The LAN mapping lets the user's existing reverse proxy target
`192.168.0.199:18787` without changing its configuration. Preserve the host overlay
when recreating the gateway:

```sh
export FCM_SERVICE_ACCOUNT_PATH="$HOME/.config/caelum/firebase-sender.json"
export PUSH_PROXY_NETWORK=npm_default
export PUSH_GATEWAY_LAN_IP=192.168.0.199
docker compose -p caelum-push \
  -f tools/push-gateway/compose.yaml \
  -f tools/push-gateway/compose.proxy.yaml \
  -f tools/push-gateway/compose.host.yaml up -d
```

The existing Docker proxy network attachment is preserved by `compose.proxy.yaml`.
Container port 8080 maps to host port 18787. Public `https://push.noz.am` now
passes TLS verification, health, DID discovery, and authentication checks. No
Nginx Proxy Manager proxy-host or certificate settings were changed by the agent.
The earlier local dev gateway remains stopped. Android registration and
synthetic FCM delivery were subsequently verified
as described below; natural Jetstream-triggered delivery remains to be tested.

## Build the Android app

Set these in the app's local `.env` (or equivalent EAS build environment):

```dotenv
EXPO_PUBLIC_APP_VARIANT=bluesky-plus
EXPO_PUBLIC_PUSH_GATEWAY_DID=did:web:push.noz.am
CAELUM_GOOGLE_SERVICES_FILE=/absolute/path/to/caelum-google-services.json
```

Rebuild the native app with `pnpm android` (Android SDK/JDK required). An existing
APK without the Firebase client configuration cannot acquire the correct FCM
token via a JS reload. `pnpm start --port 8083` starts Metro independently.
Allow Android notification permissions after signing in. Registration sends
both `serviceDid: did:web:push.noz.am` and the PDS proxy target
`did:web:push.noz.am#bsky_notif`; logout uses the same service and app ID.
Configured Caelum Android debug builds also register, to allow local testing.
With no gateway DID, Caelum push remains disabled. iOS Caelum remains disabled.

## Android verification performed

On the Android 15 emulator, the Firebase-enabled development APK was installed
as an update without clearing app data. The existing signed-in account obtained
a native FCM token and registered it through its PDS with the public HTTPS
gateway; gateway health reported one registered DID and one token.

Marked, synthetic data-only FCM messages were then sent directly to that
registered token, using the Expo payload contract tested in the gateway patch.
All six categories (like, repost, reply, mention, quote, follow) appeared in the
Android notification shade with the intended text while the app was backgrounded.
Tapping follow opened the intended profile; tapping like and reply opened the
intended post thread. No public post, like, repost, or follow was created.

One like test sent immediately after pressing Home did not display; repeating
it after confirming the launcher was foregrounded succeeded. This does not
verify foreground-to-background transition behavior. Foreground unread refresh,
cold-process startup, multi-account switching, logout on the real device, and
natural Jetstream-event-to-FCM delivery remain unverified. The synthetic FCM
sends bypassed the gateway event detector/sender, so they do not increment its
`pushesSent` counter. The Go suite separately tests gateway payload serialization.

## Delivery acceptance test (requires Firebase and public HTTPS)

Use a Google Play enabled emulator or a physical Android device. Sign in with a
recipient account and allow notifications. Confirm the gateway logs show the
recipient's authenticated registration; this tests hosted-PDS proxy forwarding,
which local dev auth tests do not exercise. From a second account, create a
like, repost, reply, mention, quote, and follow targeting the recipient.

For each reason, check foreground unread refresh, background notification text,
and tap routing. Likes/reposts open the subject post; replies/mentions/quotes
open the actor's post; follows open the actor's profile. Repeat taps after a
normal process exit (not Android force-stop), and while a different account is
selected, to check cold-start and account switching. Log out and verify token
removal. Never capture device tokens, account JWTs, or sender keys in artifacts.

This is a limited social-push integration: private chat and bell subscriptions
are unsupported. AppView private mutes and push category preferences are not
synchronized to this gateway. Native channel sound settings still apply. Keep
this opt-in until those semantics and real-device delivery are validated.

# atproto-push-gateway feasibility for Caelum Android

Source assessment, 2026-11-11. Reviewed the read-only checkout at
`/tmp/caelum-atproto-push-gateway-review`, commit
`100fd29f03348bf69491b32f461b2738bae3113d`, and the current Caelum working tree.
No registration, delivery, runtime tests, deployment, or credential access was
performed. The conclusions below describe code compatibility, not proven delivery.

## Conclusion

**A plausible backend for a limited public social-event push feature, but not a
drop-in replacement for Bluesky push.** It implements Jetstream event detection,
authenticated device registration, SQLite token storage and direct FCM delivery.
The main blockers are Caelum's disabled push setup, Firebase sender ownership,
registration routing, the Android payload contract, and incomplete notification
semantics. See the pinned [architecture and configuration](https://github.com/DracoBlue/atproto-push-gateway/blob/100fd29f03348bf69491b32f461b2738bae3113d/README.md)
and [delivery pipeline](https://github.com/DracoBlue/atproto-push-gateway/blob/100fd29f03348bf69491b32f461b2738bae3113d/internal/jetstream/consumer.go#L727-L782).

## Integration blockers

1. **Use our own Firebase configuration and authorized sender.** Caelum's
   [app.config.js](../app.config.js) currently sets `googleServicesFile` to
   `undefined` for `IS_BLUESKY_PLUS`; its Android package is `am.noz.blueskyplus`.
   It needs Firebase Android client configuration matching that package and a
   gateway service account authorized to send to that Firebase project's device
   tokens. The gateway reads `FCM_SERVICE_ACCOUNT_PATH` or
   `FCM_SERVICE_ACCOUNT_BASE64`; its sender extracts `project_id`, requests the
   Firebase Messaging OAuth scope, and calls FCM HTTP v1. The client
   `google-services.json` is not the server service-account credential. Caelum
   obtains native tokens with `getDevicePushTokenAsync()`, so direct FCM is the
   relevant route, not merely configuring the Expo fallback.
   Sources: [FCM sender](https://github.com/DracoBlue/atproto-push-gateway/blob/100fd29f03348bf69491b32f461b2738bae3113d/internal/push/fcm.go#L36-L76),
   [HTTP v1 request](https://github.com/DracoBlue/atproto-push-gateway/blob/100fd29f03348bf69491b32f461b2738bae3113d/internal/push/fcm.go#L151-L183),
   [local token lifecycle](../src/lib/notifications/notifications.ts).

2. **Change both DIDs, through the authenticated PDS proxy.** For a gateway
   configured as `PUSH_GATEWAY_DID=did:web:push.example.org`, use body
   `serviceDid: 'did:web:push.example.org'` AND proxy target
   `did:web:push.example.org#bsky_notif`. In this fork the latter is the lex
   client's per-call `service` option, which emits `atproto-proxy`.
   [NOTIF_SERVICE](../src/lib/constants.ts) currently uses `BLUESKY_PROXY_DID`;
   [registration and unregistration](../src/lib/notifications/notifications.ts)
   still use upstream AppView DIDs and `appId: 'xyz.blueskyweb.app'`, and have
   Caelum early returns across permission/token/registration/logout handling.
   All those paths need deliberate integration, including token refresh and
   logout cleanup. The gateway must expose its HTTPS DID document with
   `#bsky_notif` / `BskyNotificationService`; it verifies a service-auth JWT
   with issuer DID signature, matching gateway `aud`, exact method `lxm`, and
   expiry, then separately checks body `serviceDid`. Changing only the body
   does not route the request. This flow is supported by source, but actual
   hosted-PDS forwarding remains untested.
   Sources: [client example and DID setup](https://github.com/DracoBlue/atproto-push-gateway/blob/100fd29f03348bf69491b32f461b2738bae3113d/README.md#production-setup),
   [registration/auth implementation](https://github.com/DracoBlue/atproto-push-gateway/blob/100fd29f03348bf69491b32f461b2738bae3113d/internal/xrpc/handler.go).

3. **Adapt the payload before enabling push.** Gateway FCM sends flat structured
   `data.reason`, `uri`, `subject`, `recipientDid`, etc. By default it also sends
   an FCM `notification` block; `FCM_DATA_ONLY=true` instead adds English
   `data.title`, `data.body` and `channelId`. Installed Expo's
   [NotificationData.kt](../node_modules/expo-notifications/android/src/main/java/expo/modules/notifications/notifications/model/NotificationData.kt)
   expects display text in `data.message` and a JSON object encoded in
   `data.body`; [RemoteNotificationContent.kt](../node_modules/expo-notifications/android/src/main/java/expo/modules/notifications/notifications/model/RemoteNotificationContent.kt)
   uses that parsed body as content data. Caelum's
   [getNotificationPayload / notificationToURL](../src/lib/hooks/useNotificationHandler.ts)
   expects `request.content.data.reason`. Thus the flat gateway map does not
   satisfy the existing JS payload path, and data-only `body` is English text
   where Expo expects JSON. A suitable adapter would preserve flat fields for
   the native handler, add `message` for display text, and encode the structured
   payload as JSON in `body`, or intentionally change the client parser.
   Verify foreground/background/cold-start display and tap routing afterward.
   Sources: [gateway FCM formats](https://github.com/DracoBlue/atproto-push-gateway/blob/100fd29f03348bf69491b32f461b2738bae3113d/internal/push/fcm.go#L104-L149),
   [Caelum Expo patch notes](../patches/expo-notifications@58.1.8.patch.md),
   [native background handler](../modules/expo-background-notification-handler/android/src/main/java/expo/modules/backgroundnotificationhandler/BackgroundNotificationHandler.kt).

## Coverage and privacy limits

- **No chat push implementation found.** The consumer subscribes only to public
  like/repost/post/follow/block/verification collections; there is no chat event
  source or delivery branch. Caelum's chat UI, payload handling and separately
  authenticated chat settings do not supply the gateway with private message
  events. Chat requires a separate supported integration, not another Jetstream
  collection toggle. Sources: [subscription list](https://github.com/DracoBlue/atproto-push-gateway/blob/100fd29f03348bf69491b32f461b2738bae3113d/internal/jetstream/consumer.go#L238-L250),
  [local settings services](../src/state/queries/notifications/settings.ts).
- **Private mutes and notification preferences are not honored automatically.**
  Upstream explicitly documents the mute gap. Delivery gates check registration,
  blocks and tokens, not the user's AppView notification settings. Caelum writes
  those settings to AppView/chat, not this gateway. Therefore muted users or
  disabled push categories may still notify unless a new synchronization/filter
  path is implemented; the existing native handler only adjusts channels/sound.
  Default FCM notification messages can be displayed by Android before client
  filtering; data-only plus a real filter would need validation.
  Sources: [mute gap](https://github.com/DracoBlue/atproto-push-gateway/blob/100fd29f03348bf69491b32f461b2738bae3113d/docs/NOTIFICATIONS.md#mute-gap),
  [delivery gates](https://github.com/DracoBlue/atproto-push-gateway/blob/100fd29f03348bf69491b32f461b2738bae3113d/internal/jetstream/consumer.go#L727-L744),
  [local settings](../src/state/queries/notifications/settings.ts).
- Public block suppression is implemented, but it is not equivalent to private
  moderation/preferences parity. Bell subscriptions (`subscribed-post`),
  starter-pack joins and contact matches are also explicitly unimplemented.
  Sources: [coverage limitations](https://github.com/DracoBlue/atproto-push-gateway/blob/100fd29f03348bf69491b32f461b2738bae3113d/docs/NOTIFICATIONS.md#not-implemented),
  [block suppression](https://github.com/DracoBlue/atproto-push-gateway/blob/100fd29f03348bf69491b32f461b2738bae3113d/docs/NOTIFICATIONS.md#block-suppression).

Treat this as a candidate for a scoped social-push proof of concept, contingent
on authenticated PDS registration and end-to-end Android delivery validation.
Do not describe it as tested delivery or full Bluesky notification parity.

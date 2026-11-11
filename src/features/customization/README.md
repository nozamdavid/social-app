# Customization

Caelum adds Settings → Customization (`/settings/customization`) for fork-specific
behavior. It contains the Android “Open Bluesky links in Caelum” action and
“Like posts when reposting”. The latter is off by default, saves immediately and
applies to every account on this device. It does not sync to the account’s PDS.

The shared repost mutation queue likes only after a successful new repost. It
reads the setting and latest cached like state after the repost resolves, skips
existing/pending likes, and uses the ordinary like mutations, history and shadow
updates. Likes are serialized per QueryClient/post between all manual and automatic
controls, so an unlike during an automatic like deletes the returned record URI.
Undoing a repost does not remove the like, and quoting does not trigger auto-like.

A failed like keeps the successful repost and shows a warning inviting a manual
retry. The toggle queue settles repeated requests for the same desired state
without sending duplicate writes or leaving callers pending.

`pnpm test --runInBand src/features/customization src/state/queries/__tests__/post-mutations.test.tsx src/lib/hooks/useToggleMutationQueue.test.tsx`
covers preferences, ordering, failures, existing/pending likes, and interactions
with manual likes/unlikes. These tests use mocked PDS writes; emulator checks
exercise navigation, immediate saving, restart persistence, and Android link
settings without creating public likes or reposts.

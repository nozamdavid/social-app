# Inline emoji suggestions

Caelum enables native post/reply and shared message-composer suggestions after
`:` followed by a contiguous shortcode. Tap a result to replace the whole token,
including text after a mid-word cursor. Whitespace ends the query. URLs, times,
non-collapsed selections and colons inside words do not trigger suggestions.
Names, shortcodes and aliases are English, as supplied by the bundled dataset.
The native post editor shows four results; the shared composer shows eight.
The separate web post editor is unchanged.

## Library choice and ranking

Reuse the installed [Emoji Mart data](https://github.com/missive/emoji-mart) and
[Fuse.js](https://www.fusejs.io/fuzzy-search.html). Emoji Mart supplies names,
aliases, keywords and Unicode sequences. Its React picker targets the web; native
suggestions use existing ALF controls. No new dependency or remote service is
needed. The previous search expected a `search` property absent from the raw data.

Full upstream license notices are bundled in `notices.json` and displayed offline
under Settings → About → Emoji suggestion credits. `NOTICE.md` and the copies in
`licenses/` document the same attributions for source distributions. When upgrading
these dependencies, update the package versions and preserve their shipped license
texts and copyright headers; the bundled notices must stay in sync.

An index is built lazily once and shared across composers. Exact shortcodes and
aliases rank first, followed by prefixes, adjacent letter swaps, substring and
Fuse fuzzy matches. The last 160 characters before the shortcode provide up to
12 distinct words to break ties using emoji names and keywords. This is a local
keyword heuristic, not a language model. Draft text is not sent to a suggestion
service. No recent-use history is stored.

## Editor integration

`tokens.ts` uses UTF-16 offsets, matching React Native and Tapper selections.
The native editor predicts the cursor until `onSelectionChange` arrives and
revalidates the token before insertion. Suggestions only show for the active
post. Existing mention suggestions remain separate.

Tapper uses the complete-shortcode regex, retaining ordinary character deletion
for unfinished shortcodes. Before selecting a partial result, its selection moves
to the token end so Tapper replaces any suffix too. The shared composer's existing
closing-colon conversion only accepts exact shortcodes/aliases; fuzzy results are
never silently substituted. Emoji queries do not retain previous-query results.

## Checks

Run `pnpm test --runInBand src/features/emojiSuggestions` for bundled-data search,
context ranking, aliases, typo recovery, token boundaries, Unicode insertion,
mid-word replacements and real Tapper integration. The Jest transform allowlist
includes Tapper's published ESM for these integration tests.

Android 15 emulator checks confirmed contextual post suggestions, fuzzy results,
and tapping a result to insert its emoji. Device automation then failed its
allowed retry, preventing the remaining interaction checks. Final screenshots,
software keyboard retention, chat interaction, TalkBack and enlarged font checks
are still pending. Source review found no further material issues after adding
readable emoji names to the shared suggestion rows.

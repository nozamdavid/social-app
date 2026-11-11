# Witchsky themes

The Caelum variant adds **Settings → Appearance → Themes**. It reads the signed-in
account's `app.witchsky.theme.colors` collection directly from that account's PDS.
Each record appears once as a radio option with color swatches. Selecting a
record with multiple variants reveals a Variant segmented control using the
record's own variant names (such as Dim, Dark, or Light). Variant changes use the
same immediate feedback and progress flow as theme changes. Existing saved
variant IDs remain valid. Records are grouped by URI, so equal names do not
merge unrelated themes. Controls with more than three variants scroll
horizontally; single-variant themes do not show a variant selector. Refresh retries loading; Load more follows repository pagination.
Default restores the existing system/light/dark and dim/dark preferences. Font
preferences remain independent.

A tap updates the radio selection locally and opens an **Applying theme…** modal
before writing the app-wide preference. The native modal's `onShow` and two
animation frames give that feedback a chance to paint before synchronous storage
notifications update the whole app. After React commits the themed tree, another
paint boundary dismisses the modal. There is no fixed progress timeout. Duplicate
taps are blocked during application; pending work is canceled on unmount or
account change. A failed storage write closes progress, restores the applied
selection, and shows a retry message. Default follows the same sequence.

Selections are stored in the existing account-scoped local storage, including
validated colors so startup works offline. Switching accounts or signing out
cannot apply another account's selection. A selected theme absent from a fetch
remains available as a local cached entry; Default is always available. Selection
does not write or modify repository records.

Schema source: [app.witchsky.theme.colors](https://pds.ls/at://did:plc:nstiflhvn4dywu5xlwq5wp4v/com.atproto.lexicon.schema/app.witchsky.theme.colors#schema),
retrieved from the author's PDS on 2026-11-09. `special` generator metadata does
not run generators: stored base colors and variant overrides are used as-is.
Recommended opposite-mode pairs are not automatically fetched or applied.

| Witchsky value  | App mapping                                                                                              |
| --------------- | -------------------------------------------------------------------------------------------------------- |
| `canvas`        | ALF background/contrast 0, legacy background, Android window background                                  |
| `surface`       | ALF contrast 25, legacy secondary background                                                             |
| `surfaceRaised` | ALF contrast 50, native sheet/header/footer backgrounds                                                  |
| `text`          | Primary text, contrast 1000                                                                              |
| `textMuted`     | Secondary text, contrast 400/700                                                                         |
| `border`        | Dividers and low-contrast outlines, contrast 100                                                         |
| `accent`        | Links, primary actions and selected controls, primary 500/600                                            |
| `accentSoft`    | Subtle primary backgrounds, primary 25/50/100                                                            |
| `onAccent`      | Primary button text/icons, floating action icons, selected radio/checkbox content, settings count badges |
| `positive`      | Positive 500/600 ramp and legacy success color                                                           |
| `warning`       | Yellow/warning palette color                                                                             |
| `critical`      | Negative 500/600 ramp and legacy destructive/error color                                                 |
| `favorite`      | Pink and legacy like palette colors                                                                      |

Other ALF ramp shades are interpolated between these semantic anchors. Absolute
white/black remain unchanged for images, media overlays and unrelated content.
Theme colors are not contrast-corrected: a record can supply low-contrast pairs.
Record colors must be strings supported by React Native; they are normalized to
hex before interpolation. Invalid records are skipped without breaking the
picker, and invalid variants do not hide a valid base. Partial variants inherit
all unspecified colors from the base.

The canvas luminance selects effective light/dark appearance for ALF,
navigation and system-bar icons. This is necessary because a single record's
variants can invert the background without changing the parent `mode` field.
The declared mode is used as the compositing ground for translucent canvases.

## Validation

On 2026-11-09, tested in the signed-in Android 15/API 35 emulator using the existing
debug APK and Metro. Read the actual account's empty collection, then copied
these two public records into its collection with the user's explicit approval:

- [BlueAT](https://pds.ls/at://did:plc:l37td5yhxl2irrzrgvei4qay/app.witchsky.theme.colors/3mvq4tnapuk2g)
- [Coffee / Espresso](https://pds.ls/at://did:plc:l37td5yhxl2irrzrgvei4qay/app.witchsky.theme.colors/3mvpzxkrbms25)

The temporary import UI and earlier local response fixture were removed. The
picker subsequently read the real copied records from the signed-in PDS. Public
source records are retained in `requestedThemes.fixture.json` for regression
tests, not bundled as fallback themes. No source repository records were changed.

Automated coverage includes native color normalization, semantic mapping,
partial variants, malformed records, both exact public theme examples, account
isolation, persistence/remount, Default reset, PDS collection requests,
pagination/repeated cursors, signed-out behavior and server errors.

Device checks and remaining limitations are recorded in
[the Android emulator report](../../../docs/bluesky-plus-emulator-test.md).

# Caelum branding

This directory is the editable source of the app's identity:

- `brand.json`: display name, mark-to-wordmark spacing, purple accent, lavender,
  ink and canvas colors.
- `mark.svg`: monochrome Gust symbol, traced from the supplied logo reference.
- `wordmark.svg`: outlined Caelum lettering from the same reference. It uses no
  live text or system font, so its shape is consistent across devices.

To change the brand, edit these files and run:

```sh
pnpm branding:generate
pnpm branding:check
```

The generator produces everything in `generated/`: colored SVG marks, light and
dark lockups, native SVG path data, default theme palettes, launcher icons,
adaptive and monochrome foregrounds, notification glyph, splash images and
favicon. App code and `app.config.js` consume those outputs. Do not edit them by
hand. Commit the sources and generated files together.

Use path-based, monochrome SVG sources with a `viewBox`. Convert lettering and
other shapes to paths and flatten transforms in your vector editor before
exporting. The generator validates this contract. No tracing tool is required
to regenerate the app assets; the checked-in SVG files are the masters. A name
change also needs a matching replacement wordmark SVG.

The default launcher PNGs are 1024x1024. Larger exports are generated beside
these files as `icon-<variant>-2048.png` and `icon-<variant>-4096.png` for purple,
dark, light and black. They are rendered directly from SVG at the target size.

PNG launcher icons have opaque backgrounds and no baked-in corner mask. Android
applies its launcher mask; the separate adaptive foreground stays within its
safe area. Purple, dark, light monochrome and dark monochrome are selectable in
Appearance. Older launcher aliases remain registered but point to Caelum images.

UI-only artwork and palette changes need a new JavaScript bundle. Launcher,
notification and native splash changes also need Expo prebuild and a native
rebuild. For the Android fork, preserve the existing generated project when
refreshing resources:

```sh
EXPO_PUBLIC_ENV=production pnpm branding:prebuild:android
```

The explicit `--no-clean` matters: Expo 58 otherwise clears the native directory
before regeneration. Keep signing material backed up separately.

The Android install identity is `am.noz.caelum`; the URL scheme remains
`blueskyplus`. This is a separate install from the legacy `am.noz.blueskyplus`
package, so its app data does not migrate automatically. Keep package and URL
scheme changes separate from display-name or artwork changes. Bluesky service
names, network addresses and upstream notices are separate from this brand. Custom Witchsky theme colors still override the
default palette. The default upstream build retains its original branding.

Source artwork was provided by the project owner; the “02 / Gust” heading and
presentation background are not part of the logo.

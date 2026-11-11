import {createTheme, type Palette} from '@bsky.app/alf'

import {type Theme as LegacyTheme} from '#/lib/ThemeContext'
import {themes} from '#/alf/themes'
import {
  type SemanticColors,
  type ThemeOption,
} from '#/features/witchskyThemes/schema'

/** Interpolate RGBA colors for ALF's intermediate interaction shades. */
function mix(from: string, to: string, amount: number) {
  const channels = (color: string) => [
    ...[1, 3, 5].map(offset => parseInt(color.slice(offset, offset + 2), 16)),
    color.length === 9 ? parseInt(color.slice(7, 9), 16) : 255,
  ]
  const a = channels(from)
  const b = channels(to)
  const result = a.map((channel, i) =>
    Math.round(channel + (b[i] - channel) * amount),
  )
  return `#${result
    .slice(0, result[3] === 255 ? 3 : 4)
    .map(n => n.toString(16).padStart(2, '0'))
    .join('')}`
}

function ramp(
  prefix: 'primary' | 'positive' | 'negative',
  main: string,
  soft: string,
  text: string,
): Partial<Palette> {
  const stops = {
    25: soft,
    50: soft,
    100: soft,
    200: mix(soft, main, 0.3),
    300: mix(soft, main, 0.55),
    400: mix(soft, main, 0.8),
    500: main,
    600: main,
    700: mix(main, text, 0.15),
    800: mix(main, text, 0.3),
    900: mix(main, text, 0.45),
    950: mix(main, text, 0.6),
    975: mix(main, text, 0.7),
  }
  return Object.fromEntries(
    Object.entries(stops).map(([stop, value]) => [`${prefix}_${stop}`, value]),
  )
}

/** Semantic anchors stay exact; intermediate ALF shades are derived from them. */
export function createWitchskyTheme(option: ThemeOption) {
  const c = option.colors
  const name = option.mode === 'light' ? 'light' : 'dark'
  const palette: Palette = {
    ...themes[name].palette,
    ...ramp('primary', c.accent, c.accentSoft, c.text),
    ...ramp('positive', c.positive, mix(c.canvas, c.positive, 0.15), c.text),
    ...ramp('negative', c.critical, mix(c.canvas, c.critical, 0.15), c.text),
    contrast_0: c.canvas,
    contrast_25: c.surface,
    contrast_50: c.surfaceRaised,
    contrast_100: c.border,
    contrast_200: mix(c.border, c.textMuted, 0.25),
    contrast_300: mix(c.border, c.textMuted, 0.5),
    contrast_400: c.textMuted,
    contrast_500: mix(c.textMuted, c.text, 0.25),
    contrast_600: mix(c.textMuted, c.text, 0.45),
    contrast_700: c.textMuted,
    contrast_800: mix(c.textMuted, c.text, 0.65),
    contrast_900: mix(c.textMuted, c.text, 0.85),
    contrast_950: mix(c.textMuted, c.text, 0.93),
    contrast_975: mix(c.textMuted, c.text, 0.97),
    contrast_1000: c.text,
    pink: c.favorite,
    like: c.favorite,
    yellow: c.warning,
  }
  return {
    ...createTheme({name, scheme: option.mode, palette}),
    onAccent: c.onAccent,
    surfaceRaised: c.surfaceRaised,
  }
}

export function createWitchskyThemeOverrides(option: ThemeOption | null) {
  if (!option) return undefined
  const theme = createWitchskyTheme(option)
  return option.mode === 'light'
    ? {light: theme, lightPalette: theme.palette}
    : {
        dark: theme,
        dim: {...theme, name: 'dim' as const},
        darkPalette: theme.palette,
        dimPalette: theme.palette,
      }
}

/** Keep screens still using the legacy theme in sync with ALF. */
export function applyLegacyThemeColors(
  theme: LegacyTheme,
  c: SemanticColors,
): LegacyTheme {
  return {
    ...theme,
    palette: {
      ...theme.palette,
      default: {
        ...theme.palette.default,
        background: c.canvas,
        backgroundLight: c.surface,
        text: c.text,
        textLight: c.textMuted,
        textInverted: c.canvas,
        link: c.accent,
        border: c.border,
      },
      primary: {
        ...theme.palette.primary,
        background: c.accent,
        backgroundLight: c.accentSoft,
        text: c.onAccent,
        textLight: c.onAccent,
        textInverted: c.accent,
        link: c.onAccent,
        border: c.accent,
      },
      secondary: {
        ...theme.palette.secondary,
        background: c.positive,
        backgroundLight: c.surfaceRaised,
        text: c.text,
        textLight: c.textMuted,
        link: c.accent,
        border: c.border,
      },
      error: {
        ...theme.palette.error,
        background: c.critical,
        backgroundLight: c.surfaceRaised,
        text: c.text,
        textLight: c.textMuted,
        link: c.critical,
        border: c.critical,
      },
    },
  }
}

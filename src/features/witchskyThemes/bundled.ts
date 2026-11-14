import {
  createTheme,
  createThemes,
  DEFAULT_PALETTE,
  DEFAULT_SUBDUED_PALETTE,
} from '@bsky.app/alf'

import brand from '#/../assets/branding/caelum/brand.json'
import palette from '#/../assets/branding/caelum/generated/palette.json'
import {type ThemeOption} from '#/features/witchskyThemes/schema'

export const BUNDLED_THEME_URI = 'builtin://caelum'

const baseCaelum = createThemes({
  defaultPalette: {...DEFAULT_PALETTE, ...palette.defaultPalette},
  subduedPalette: {...DEFAULT_SUBDUED_PALETTE, ...palette.subduedPalette},
})

/** Deep violet surfaces distinguish Dark while preserving Caelum's accent ramp. */
const caelum = {
  ...baseCaelum,
  dark: createTheme({
    name: 'dark',
    scheme: 'dark',
    palette: {
      ...baseCaelum.dark.palette,
      contrast_0: '#09080f',
      contrast_25: '#100e19',
      contrast_50: '#171421',
      contrast_100: '#292437',
    },
  }),
}

/** Ship Caelum as an opt-in theme without changing Bluesky's base palettes. */
export const bundledThemes: ThemeOption[] = (
  ['light', 'dim', 'dark'] as const
).map(name => {
  const p = caelum[name].palette
  return {
    id: `${BUNDLED_THEME_URI}#${name}`,
    uri: BUNDLED_THEME_URI,
    name: 'Caelum',
    variantName: {light: 'Light', dim: 'Dim', dark: 'Dark'}[name],
    mode: name === 'light' ? 'light' : 'dark',
    colors: {
      canvas: p.contrast_0,
      surface: p.contrast_25,
      surfaceRaised: p.contrast_50,
      text: p.contrast_1000,
      textMuted: p.contrast_700,
      border: p.contrast_100,
      accent: p.primary_500,
      accentSoft: p.primary_50,
      onAccent: brand.colors.onPrimary,
      positive: p.positive_500,
      warning: p.yellow,
      critical: p.negative_500,
      favorite: p.like,
    },
  }
})

/** Resolve only known bundled identities to trusted, current app colors. */
export function getBundledTheme(id: string) {
  return bundledThemes.find(theme => theme.id === id)
}

export function getBundledAlfTheme(id: string) {
  const option = getBundledTheme(id)
  if (!option) return undefined
  const name = id.slice(id.indexOf('#') + 1) as keyof typeof caelum
  return {
    ...caelum[name],
    onAccent: option.colors.onAccent,
    surfaceRaised: option.colors.surfaceRaised,
  }
}

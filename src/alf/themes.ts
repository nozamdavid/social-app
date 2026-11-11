import {
  createThemes,
  DEFAULT_PALETTE,
  DEFAULT_SUBDUED_PALETTE,
} from '@bsky.app/alf'

import brand from '#/../assets/branding/caelum/brand.json'
import palette from '#/../assets/branding/caelum/generated/palette.json'
import {IS_BLUESKY_PLUS} from '#/env'

const baseThemes = createThemes({
  defaultPalette: IS_BLUESKY_PLUS
    ? {...DEFAULT_PALETTE, ...palette.defaultPalette}
    : DEFAULT_PALETTE,
  subduedPalette: IS_BLUESKY_PLUS
    ? {...DEFAULT_SUBDUED_PALETTE, ...palette.subduedPalette}
    : DEFAULT_SUBDUED_PALETTE,
})

const DEFAULT_THEMES = {
  light: {
    ...baseThemes.light,
    ...(IS_BLUESKY_PLUS && {onAccent: brand.colors.onPrimary}),
  },
  dark: {
    ...baseThemes.dark,
    ...(IS_BLUESKY_PLUS && {onAccent: brand.colors.onPrimary}),
  },
  dim: {
    ...baseThemes.dim,
    ...(IS_BLUESKY_PLUS && {onAccent: brand.colors.onPrimary}),
  },
}

export const themes = {
  lightPalette: DEFAULT_THEMES.light.palette,
  darkPalette: DEFAULT_THEMES.dark.palette,
  dimPalette: DEFAULT_THEMES.dim.palette,
  light: DEFAULT_THEMES.light,
  dark: DEFAULT_THEMES.dark,
  dim: DEFAULT_THEMES.dim,
}

/**
 * @deprecated use ALF and access palette from `useTheme()`
 */
export const lightPalette = DEFAULT_THEMES.light.palette
/**
 * @deprecated use ALF and access palette from `useTheme()`
 */
export const darkPalette = DEFAULT_THEMES.dark.palette
/**
 * @deprecated use ALF and access palette from `useTheme()`
 */
export const dimPalette = DEFAULT_THEMES.dim.palette
/**
 * @deprecated use ALF and access theme from `useTheme()`
 */
export const light = DEFAULT_THEMES.light
/**
 * @deprecated use ALF and access theme from `useTheme()`
 */
export const dark = DEFAULT_THEMES.dark
/**
 * @deprecated use ALF and access theme from `useTheme()`
 */
export const dim = DEFAULT_THEMES.dim

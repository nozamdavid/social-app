import {themes as blueskyThemes} from '@bsky.app/alf'

import {themes} from '#/alf/themes'
import {
  bundledThemes,
  getBundledAlfTheme,
} from '#/features/witchskyThemes/bundled'
import {readThemeSelection} from '#/features/witchskyThemes/schema'
import {createWitchskyThemeOverrides} from '#/features/witchskyThemes/theme'

jest.mock('#/env', () => ({IS_BLUESKY_PLUS: true}))

it.each(['light', 'dim', 'dark'] as const)(
  'keeps Bluesky %s as the default',
  name => {
    expect(themes[name]).toEqual(blueskyThemes[name])
  },
)

it.each(bundledThemes)(
  'restores bundled $variantName offline using trusted colors',
  option => {
    const raw = JSON.stringify({
      data: {...option, colors: {...option.colors, accent: '#ff0000'}},
    })
    expect(readThemeSelection(raw, 'did:plc:account')).toEqual(option)
    expect(createWitchskyThemeOverrides(option)?.[option.mode]).toEqual(
      getBundledAlfTheme(option.id),
    )
  },
)

it('rejects unknown bundled identities', () => {
  const raw = JSON.stringify({
    data: {...bundledThemes[0], id: 'builtin://unknown'},
  })
  expect(readThemeSelection(raw, 'did:plc:account')).toBeNull()
})

it('resetting to default removes bundled overrides', () => {
  expect(createWitchskyThemeOverrides(null)).toBeUndefined()
})

it('gives Caelum Dark distinctly deeper surfaces than Dim', () => {
  const dim = getBundledAlfTheme('builtin://caelum#dim')!
  const dark = getBundledAlfTheme('builtin://caelum#dark')!
  for (const stop of ['contrast_0', 'contrast_25', 'contrast_50'] as const) {
    const brightness = (hex: string) =>
      [1, 3, 5].reduce(
        (sum, offset) => sum + parseInt(hex.slice(offset, offset + 2), 16),
        0,
      )
    expect(brightness(dark.palette[stop])).toBeLessThan(
      brightness(dim.palette[stop]) * 0.75,
    )
  }
  expect(dark.atoms.bg.backgroundColor).toBe(dark.palette.contrast_0)
  expect(dark.palette.primary_500).toBe(dim.palette.primary_500)
})

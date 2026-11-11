jest.mock('#/alf', () => ({tokens: jest.requireActual('#/alf/tokens')}))

import {defaultTheme} from '#/lib/themes'
import {themes} from '#/alf/themes'
import {
  parseThemeRecord,
  readThemeSelection,
  THEME_COLLECTION,
} from '#/features/witchskyThemes/schema'
import {
  applyLegacyThemeColors,
  createWitchskyTheme,
  createWitchskyThemeOverrides,
} from '#/features/witchskyThemes/theme'
import requestedThemes from './requestedThemes.fixture.json'

const did = 'did:plc:nstiflhvn4dywu5xlwq5wp4v'
const uri = `at://${did}/${THEME_COLLECTION}/3muo2fd2s4d22`
/* Semantic colors from the lexicon author's public Base Color theme. */
const colors = {
  text: '#ffffff',
  accent: '#29998e',
  border: '#485b75',
  canvas: '#1c2b35',
  surface: '#22323d',
  warning: '#ffc404',
  critical: '#eb2452',
  favorite: '#ec4899',
  onAccent: '#000000',
  positive: '#0ac266',
  textMuted: '#abb8c9',
  accentSoft: '#163633',
  surfaceRaised: '#2c3d4c',
}
const record = {
  $type: THEME_COLLECTION,
  name: 'Base Color',
  mode: 'dark',
  base: {name: 'Dim', colors},
  createdAt: '2026-09-04T00:00:00.000Z',
  variants: [{name: 'Dark', colors: {canvas: '#111822'}}],
  special: {$type: 'app.witchsky.theme.defs#hue'},
}

it('loads the base and merges partial variants over its semantic colors', () => {
  const [base, variant] = parseThemeRecord(uri, record)
  expect(base).toMatchObject({
    uri,
    name: 'Base Color',
    variantName: 'Dim',
    mode: 'dark',
    colors,
  })
  expect(variant.id).not.toBe(base.id)
  expect(variant.colors).toEqual({...colors, canvas: '#111822'})
})

it('isolates malformed variants and records without losing the valid base', () => {
  expect(
    parseThemeRecord(uri, {
      ...record,
      variants: [
        {name: 'Broken', colors: {accent: 'url(https://example.com)'}},
      ],
    }),
  ).toHaveLength(1)
  expect(
    parseThemeRecord(uri, {
      ...record,
      base: {name: 'Broken', colors: {...colors, text: 'invalid'}},
    }),
  ).toEqual([])
  expect(parseThemeRecord(uri, {...record, mode: 'future-mode'})).toEqual([])
  expect(
    parseThemeRecord(uri, {...record, $type: 'app.bsky.feed.post'}),
  ).toEqual([])
})

it('normalizes native color formats before generating a palette', () => {
  const [theme] = parseThemeRecord(uri, {
    ...record,
    base: {
      name: 'Colors',
      colors: {
        ...colors,
        canvas: '#abc',
        text: 'rgb(255, 255, 255)',
        accent: 'rebeccapurple',
        onAccent: '#0008',
      },
    },
  })
  expect(theme.colors).toMatchObject({
    canvas: '#aabbcc',
    text: '#ffffff',
    accent: '#663399',
    onAccent: '#00000088',
  })
})

it('maps all semantic roles and leaves absolute white and black unchanged', () => {
  const [option] = parseThemeRecord(uri, record)
  const theme = createWitchskyTheme(option)
  expect(theme.scheme).toBe('dark')
  expect(theme.atoms.bg.backgroundColor).toBe(colors.canvas)
  expect(theme.atoms.bg_contrast_25.backgroundColor).toBe(colors.surface)
  expect(theme.atoms.bg_contrast_50.backgroundColor).toBe(colors.surfaceRaised)
  expect(theme.atoms.text.color).toBe(colors.text)
  expect(theme.atoms.text_contrast_medium.color).toBe(colors.textMuted)
  expect(theme.atoms.border_contrast_low.borderColor).toBe(colors.border)
  expect(theme.atoms.text_link.color).toBe(colors.accent)
  expect(theme.palette.primary_50).toBe(colors.accentSoft)
  expect(theme.onAccent).toBe(colors.onAccent)
  expect(theme.palette.positive_500).toBe(colors.positive)
  expect(theme.palette.negative_500).toBe(colors.critical)
  expect(theme.palette.yellow).toBe(colors.warning)
  expect(theme.palette.pink).toBe(colors.favorite)
  expect(theme.palette.like).toBe(colors.favorite)
  expect(theme.palette.white).toBe('#FFFFFF')
  expect(theme.palette.black).toBe('#000000')
  expect(themes.dark.palette.primary_500).not.toBe(colors.accent)
})

it('uses the declared mode and restores unmodified themes for Default', () => {
  const [dark] = parseThemeRecord(uri, record)
  const [light] = parseThemeRecord(uri, {
    ...record,
    mode: 'light',
    base: {name: 'Light', colors: {...colors, canvas: '#ffffff'}},
  })
  expect(createWitchskyThemeOverrides(dark)?.dark?.scheme).toBe('dark')
  expect(createWitchskyThemeOverrides(light)?.light?.scheme).toBe('light')
  expect(createWitchskyThemeOverrides(null)).toBeUndefined()
  const legacy = applyLegacyThemeColors(defaultTheme, dark.colors)
  expect(legacy.palette.default.background).toBe(colors.canvas)
  expect(legacy.palette.primary.text).toBe(colors.onAccent)
  expect(defaultTheme.palette.default.background).not.toBe(colors.canvas)
})

it('validates cached selections and prevents another account’s theme leaking', () => {
  const [theme] = parseThemeRecord(uri, record)
  const raw = JSON.stringify({data: theme})
  expect(readThemeSelection(raw, did)).toEqual(theme)
  expect(readThemeSelection(raw, 'did:plc:another')).toBeNull()
  expect(readThemeSelection('{broken', did)).toBeNull()
  expect(
    readThemeSelection(JSON.stringify({data: {...theme, colors: {}}}), did),
  ).toBeNull()
  expect(readThemeSelection(JSON.stringify({data: null}), did)).toBeNull()
})

it('loads both requested real-world records and resolves variant appearance from the canvas', () => {
  const [blue, coffee] = requestedThemes.map(record =>
    parseThemeRecord(record.uri, record.value),
  )
  expect(blue).toHaveLength(1)
  expect(blue[0].name).toBe('BlueAT')
  expect(blue[0].mode).toBe('dark')
  expect(coffee.map(theme => [theme.variantName, theme.mode])).toEqual([
    ['Default', 'dark'],
    ['Light', 'light'],
    ['Dark', 'dark'],
  ])
  expect(coffee[1].colors.positive).toBe(coffee[0].colors.positive)
  expect(createWitchskyTheme(coffee[2]).atoms.bg.backgroundColor).toBe(
    '#050505',
  )
})

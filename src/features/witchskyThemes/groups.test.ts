import {
  getThemeGroupOption,
  groupThemes,
} from '#/features/witchskyThemes/groups'
import {parseThemeRecord} from '#/features/witchskyThemes/schema'
import requested from './requestedThemes.fixture.json'

const [record] = requested
const options = parseThemeRecord(record.uri, {
  ...record.value,
  variants: [{name: 'Light', colors: {canvas: '#ffffff', text: '#000000'}}],
})

it('shows one theme for its base and variants, without merging same-name records', () => {
  const other = {
    ...options[0],
    id: 'at://other/theme/one',
    uri: 'at://other/theme/one',
  }
  const groups = groupThemes([...options, other], null)
  expect(groups).toHaveLength(2)
  expect(groups[0].variants.map(option => option.variantName)).toEqual([
    options[0].variantName,
    'Light',
  ])
  expect(groups[1].name).toBe(groups[0].name)
})

it('keeps the persisted variant selected and defaults new theme choices to the base', () => {
  const [group] = groupThemes(options, options[1])
  expect(getThemeGroupOption(group, options[1].id)).toBe(options[1])
  expect(getThemeGroupOption(group, 'default')).toBe(options[0])
})

it('deduplicates repeated pages and updates their variant values', () => {
  const updated = {
    ...options[1],
    colors: {...options[1].colors, accent: '#123456'},
  }
  const [group] = groupThemes([...options, updated], null)
  expect(group.variants).toHaveLength(2)
  expect(group.variants[1]).toBe(updated)
})

it('keeps a missing cached variant within its existing theme, with no duplicate row', () => {
  const [group] = groupThemes([options[0]], options[1])
  expect(group.variants).toHaveLength(2)
  expect(group.cachedVariantId).toBe(options[1].id)
  expect(getThemeGroupOption(group, options[1].id)).toBe(options[1])
})

it('keeps the selected cached theme available offline and removes it for Default', () => {
  const [group] = groupThemes([], options[1])
  expect(group.uri).toBe(options[1].uri)
  expect(group.cachedVariantId).toBe(options[1].id)
  expect(groupThemes([], null)).toEqual([])
})

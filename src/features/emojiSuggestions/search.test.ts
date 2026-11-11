import data, {type EmojiMartData} from '@emoji-mart/data'

import {createEmojiSearch, isExactEmojiShortcode} from './search'

const search = createEmojiSearch(data as EmojiMartData)

it('finds names, aliases, keywords and fuzzy misspellings in actual bundled data', () => {
  expect(search('coffee')[0].value).toBe('☕')
  expect(search('poop')[0].emoji.id).toBe('hankey')
  expect(search('+1')[0].value).toBe('👍')
  expect(search('-1')[0].value).toBe('👎')
  expect(search('celebrate').map(item => item.value)).toContain('🎉')
  expect(search('smiel')[0].emoji.id).toBe('smile')
  expect(search('smlie')[0].emoji.id).toBe('smile')
})

it('ranks exact shortcodes first and uses surrounding words to disambiguate prefixes', () => {
  expect(search('cat', 8, 'Happy birthday cake')[0].emoji.id).toBe('cat')
  expect(search('ca', 8, 'Happy birthday')[0].emoji.id).toBe('birthday')
})

it('recognizes exact aliases but does not treat fuzzy suggestions as complete shortcodes', () => {
  expect(isExactEmojiShortcode(search('poop')[0], 'poop')).toBe(true)
  expect(isExactEmojiShortcode(search('smiel')[0], 'smiel')).toBe(false)
})

it('bounds results and rejects empty, malformed and whitespace-spanning queries', () => {
  expect(search('s', 4)).toHaveLength(4)
  for (const q of ['', 'smile now', '://', 'x'.repeat(65)])
    expect(search(q)).toEqual([])
  expect(search('smile', 0)).toEqual([])
})

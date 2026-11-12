import {facets, Tapper} from '@bsky.app/tapper'

import {EMOJI_FACET_PATTERN, getEmojiToken} from './tokens'

function editor(text = '', cursor?: number) {
  const tapper = new Tapper({facets: {...facets, emoji: EMOJI_FACET_PATTERN}})
  tapper.replaceText(text, cursor)
  return tapper
}

it('keeps partial shortcodes editable after leaving them', () => {
  const tapper = editor()
  for (const text of [':', ':s', ':sm', ':smi', ':smi '])
    tapper.handleTextChange(text)
  tapper.handleTextChange(':smi')
  tapper.handleTextChange(':sm')
  expect(tapper.text).toBe(':sm')
})

it('replaces the complete partial token when choosing a result at a mid-word cursor', () => {
  const tapper = editor('hello :smile! later', 9)
  const token = getEmojiToken(tapper.text, tapper.selection.start)!
  expect(token.query).toBe('sm')
  expect(tapper.activeFacet?.range.end).toBe(9)
  tapper.handleSelectionChange({
    nativeEvent: {selection: {start: token.end, end: token.end}},
  })
  tapper.activeFacet!.replace('😄', {noTrailingSpace: true})
  expect(tapper.text).toBe('hello 😄! later')
  expect(tapper.selection).toEqual({start: 8, end: 8})
})

it('recognizes complete signed aliases and preserves mention detection', () => {
  const tapper = editor()
  for (const text of [':', ':+', ':+1', ':+1:']) tapper.handleTextChange(text)
  expect(tapper.activeFacet?.value).toBe('+1')
  const mention = editor('Hi ')
  for (const char of '@nozam') mention.handleTextChange(mention.text + char)
  expect(mention.activeFacet?.type).toBe('mention')
  expect(mention.activeFacet?.value).toBe('nozam')
})

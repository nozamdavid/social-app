import {getCursorAfterEdit, getEmojiToken, replaceEmojiToken} from './tokens'

it('opens after a colon and letters, including parentheses and multiline input', () => {
  expect(getEmojiToken(':s', 2)?.query).toBe('s')
  expect(getEmojiToken('hello\n:thumbs_up', 16)?.query).toBe('thumbs_up')
  expect(getEmojiToken('(:party', 7)?.query).toBe('party')
  expect(getEmojiToken(':+1', 3)?.query).toBe('+1')
})

it('does not trigger for a bare colon, whitespace, URLs, times, or text selections', () => {
  for (const text of [
    ':',
    ': smile',
    'https://example.com',
    '12:30',
    'word:smile',
    ':smile ',
  ]) {
    expect(getEmojiToken(text, text.length)).toBeNull()
  }
  expect(getEmojiToken(':smile!', 7)).toBeNull()
  expect(getEmojiToken(':smile', 1, 4)).toBeNull()
})

it('replaces the full shortcode at a mid-word cursor without eating punctuation or surrounding text', () => {
  const text = 'hi :smile:! bye'
  const token = getEmojiToken(text, 6)!
  expect(token.query).toBe('sm')
  expect(replaceEmojiToken(text, token, '😄')).toEqual({
    text: 'hi 😄! bye',
    selection: {start: 5, end: 5},
  })
})

it('keeps a correct UTF-16 cursor after multi-codepoint emoji and retains context', () => {
  const text = 'Happy birthday :cake'
  const token = getEmojiToken(text, text.length)!
  expect(token.context).toBe('Happy birthday ')
  const result = replaceEmojiToken(text, token, '👩‍💻')
  expect(result.text).toBe('Happy birthday 👩‍💻 ')
  expect(result.selection.start).toBe(result.text.length)
})

it('predicts end, middle, deletion and pasted edits before the native selection event', () => {
  expect(getCursorAfterEdit(':sm', ':smi')).toBe(4)
  expect(getCursorAfterEdit('hi :sm bye', 'hi :smi bye')).toBe(7)
  expect(getCursorAfterEdit('hi :smi', 'hi :sm')).toBe(6)
  expect(getCursorAfterEdit('hello', 'hello :heart')).toBe(12)
})

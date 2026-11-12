/** The boundary prefix is also understood by Tapper's facet parser. */
export const EMOJI_FACET_PATTERN = /(^|\s|\():([a-zA-Z0-9_+-]+):/g
const EMOJI_TOKEN_PATTERN = /(^|\s|\():([a-zA-Z0-9_+-]+):?/g

export type EmojiToken = {
  start: number
  end: number
  query: string
  context: string
}

/** Find only a collapsed cursor within a contiguous colon shortcode. */
export function getEmojiToken(
  text: string,
  start: number,
  end = start,
): EmojiToken | null {
  if (start !== end || start < 0 || start > text.length) return null
  const pattern = new RegExp(EMOJI_TOKEN_PATTERN.source, 'g')
  let match: RegExpExecArray | null
  while ((match = pattern.exec(text))) {
    const tokenStart = match.index + match[1].length
    const tokenEnd = match.index + match[0].length
    if (start <= tokenStart + 1 || start > tokenEnd) continue
    const queryEnd = Math.min(start, tokenStart + 1 + match[2].length)
    return {
      start: tokenStart,
      end: tokenEnd,
      query: text.slice(tokenStart + 1, queryEnd),
      context: text.slice(Math.max(0, tokenStart - 160), tokenStart),
    }
  }
  return null
}

/** Replace the whole token, including any suffix to the right of the cursor. */
export function replaceEmojiToken(
  text: string,
  token: EmojiToken,
  emoji: string,
) {
  const suffix = text.slice(token.end)
  const insertion = emoji + (suffix.length === 0 ? ' ' : '')
  return {
    text: text.slice(0, token.start) + insertion + suffix,
    selection: {
      start: token.start + insertion.length,
      end: token.start + insertion.length,
    },
  }
}

/** Predict an edit's cursor before native onSelectionChange catches up. */
export function getCursorAfterEdit(previous: string, next: string) {
  let prefix = 0
  while (
    prefix < previous.length &&
    prefix < next.length &&
    previous[prefix] === next[prefix]
  )
    prefix++
  let suffix = 0
  while (
    suffix < previous.length - prefix &&
    suffix < next.length - prefix &&
    previous[previous.length - 1 - suffix] === next[next.length - 1 - suffix]
  )
    suffix++
  return next.length - suffix
}

import {type Emoji, type EmojiMartData} from '@emoji-mart/data'
import Fuse from 'fuse.js'

import {type AutocompleteEmoji} from '#/components/Autocomplete/types'

type Entry = {
  emoji: Emoji
  shortcodes: string[]
  normalizedShortcodes: string[]
  name: string
  keywords: string[]
  words: string[]
}

function normalize(value: string) {
  if (/^[+-]\d+$/.test(value)) return value
  return value.toLowerCase().replace(/[_-]+/g, ' ').trim()
}

/** Fuse counts a swapped pair as two edits, so handle common typing swaps first. */
function isAdjacentSwap(query: string, code: string) {
  if (query.length !== code.length) return false
  for (let i = 0; i < query.length - 1; i++) {
    if (query[i] === code[i]) continue
    return (
      query[i] === code[i + 1] &&
      query[i + 1] === code[i] &&
      query.slice(i + 2) === code.slice(i + 2)
    )
  }
  return false
}

export function isExactEmojiShortcode(item: AutocompleteEmoji, query: string) {
  return (item.shortcodes ?? [item.emoji.id]).some(
    code => normalize(code) === normalize(query),
  )
}

/** Search bundled names, aliases and keywords, using nearby words as a tie-break. */
export function createEmojiSearch(data: EmojiMartData) {
  const aliases = new Map<string, string[]>()
  for (const [alias, id] of Object.entries(data.aliases)) {
    aliases.set(id, [...(aliases.get(id) ?? []), alias])
  }
  const entries: Entry[] = Object.values(data.emojis).map(emoji => {
    const shortcodes = [emoji.id, ...(aliases.get(emoji.id) ?? [])]
    const name = normalize(emoji.name)
    const keywords = emoji.keywords.map(normalize)
    return {
      emoji,
      shortcodes,
      normalizedShortcodes: shortcodes.map(normalize),
      name,
      keywords,
      words: [
        ...new Set(
          [...shortcodes.map(normalize), name, ...keywords].flatMap(value =>
            value.split(/\s+/),
          ),
        ),
      ],
    }
  })
  const fuse = new Fuse(entries, {
    keys: [
      {name: 'shortcodes', weight: 4},
      {name: 'name', weight: 2},
      'keywords',
    ],
    threshold: 0.35,
    ignoreLocation: true,
    includeScore: true,
  })
  const stopWords = new Set([
    'the',
    'and',
    'for',
    'you',
    'with',
    'this',
    'that',
    'have',
    'was',
    'are',
    'our',
    'your',
    'just',
    'from',
    'about',
  ])

  return (query: string, limit = 8, context = ''): AutocompleteEmoji[] => {
    if (!/^[a-zA-Z0-9_+-]+$/.test(query) || query.length > 64 || limit < 1)
      return []
    const q = normalize(query)
    if (!q) return []
    const contextWords = [
      ...new Set(
        normalize(context)
          .split(/[^\p{L}\p{N}]+/u)
          .filter(word => word.length > 2 && !stopWords.has(word))
          .slice(-12),
      ),
    ]
    const candidates = new Map<Entry, number>()
    for (const entry of entries) {
      const codes = entry.normalizedShortcodes
      let rank: number | undefined
      if (codes.includes(q)) rank = 0
      else if (codes.some(code => code.startsWith(q))) rank = 1
      else if (entry.words.some(word => word.startsWith(q))) rank = 1.1
      else if (q.length >= 3 && codes.some(code => isAdjacentSwap(q, code)))
        rank = 2
      else if (entry.name.includes(q)) rank = 2.1
      if (rank !== undefined) candidates.set(entry, rank)
    }
    if (q.length >= 3) {
      for (const result of fuse.search(q, {limit: 64})) {
        if (!candidates.has(result.item))
          candidates.set(result.item, 3 + (result.score ?? 1))
      }
    }
    return [...candidates]
      .map(([entry, rank]) => {
        const contextScore = contextWords.filter(word =>
          entry.words.includes(word),
        ).length
        return {entry, rank, contextScore}
      })
      .sort(
        (a, b) =>
          Math.floor(a.rank) - Math.floor(b.rank) ||
          b.contextScore - a.contextScore ||
          a.rank - b.rank ||
          a.entry.emoji.id.localeCompare(b.entry.emoji.id),
      )
      .slice(0, limit)
      .map(({entry}) => ({
        key: entry.emoji.id,
        type: 'emoji',
        value: entry.emoji.skins[0].native,
        emoji: entry.emoji,
        shortcodes: entry.shortcodes,
      }))
  }
}
